###############################################################################
# MODULE: LAMBDA
# Creates:
#   Layers  : db_utils, aws_clients
#   SQS     : order-processing-queue, invoice-queue (+ DLQs)
#   S3      : invoice + notification bucket
#   Lambdas : order_processor (SQS trigger)
#             notification_enricher (SNS trigger)
#             invoice_generator (S3 trigger)
###############################################################################

locals {
  prefix = "${var.project}-${var.environment}"
}

# ── Package Lambda source zips ────────────────────────────────────────────────
data "archive_file" "db_utils_layer" {
  type        = "zip"
  source_dir  = "${path.root}/../lambda/layers/db_utils"
  output_path = "${path.module}/zips/db_utils_layer.zip"
}

data "archive_file" "aws_clients_layer" {
  type        = "zip"
  source_dir  = "${path.root}/../lambda/layers/aws_clients"
  output_path = "${path.module}/zips/aws_clients_layer.zip"
}

data "archive_file" "order_processor" {
  type        = "zip"
  source_dir  = "${path.root}/../lambda/order_processor"
  output_path = "${path.module}/zips/order_processor.zip"
}

data "archive_file" "notification_enricher" {
  type        = "zip"
  source_dir  = "${path.root}/../lambda/notification_enricher"
  output_path = "${path.module}/zips/notification_enricher.zip"
}

data "archive_file" "invoice_generator" {
  type        = "zip"
  source_dir  = "${path.root}/../lambda/invoice_generator"
  output_path = "${path.module}/zips/invoice_generator.zip"
}

# ── Lambda Layers ─────────────────────────────────────────────────────────────
resource "aws_lambda_layer_version" "db_utils" {
  layer_name          = "${local.prefix}-db-utils"
  filename            = data.archive_file.db_utils_layer.output_path
  source_code_hash    = data.archive_file.db_utils_layer.output_base64sha256
  compatible_runtimes = ["python3.12"]
  description         = "MySQL connection helpers (pymysql)"
}

resource "aws_lambda_layer_version" "aws_clients" {
  layer_name          = "${local.prefix}-aws-clients"
  filename            = data.archive_file.aws_clients_layer.output_path
  source_code_hash    = data.archive_file.aws_clients_layer.output_base64sha256
  compatible_runtimes = ["python3.12"]
  description         = "Pre-configured boto3 SNS, S3, SQS clients"
}

# ── SQS Queues ────────────────────────────────────────────────────────────────
resource "aws_sqs_queue" "order_dlq" {
  name                      = "${local.prefix}-order-processing-dlq"
  message_retention_seconds = 1209600 # 14 days
  tags                      = { Name = "${local.prefix}-order-processing-dlq" }
}

resource "aws_sqs_queue" "order_processing" {
  name                       = "${local.prefix}-order-processing-queue"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 86400
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.order_dlq.arn
    maxReceiveCount     = 3
  })
  tags = { Name = "${local.prefix}-order-processing-queue" }
}

resource "aws_sqs_queue" "invoice_dlq" {
  name                      = "${local.prefix}-invoice-dlq"
  message_retention_seconds = 1209600
  tags                      = { Name = "${local.prefix}-invoice-dlq" }
}

resource "aws_sqs_queue" "invoice" {
  name                       = "${local.prefix}-invoice-queue"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 86400
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.invoice_dlq.arn
    maxReceiveCount     = 3
  })
  tags = { Name = "${local.prefix}-invoice-queue" }
}

# ── S3 Bucket (invoices + notification logs) ──────────────────────────────────
resource "aws_s3_bucket" "invoices" {
  bucket        = "${local.prefix}-invoices-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
  tags          = { Name = "${local.prefix}-invoices" }
}

resource "aws_s3_bucket_versioning" "invoices" {
  bucket = aws_s3_bucket.invoices.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "invoices" {
  bucket = aws_s3_bucket.invoices.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "invoices" {
  bucket                  = aws_s3_bucket.invoices.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

data "aws_caller_identity" "current" {}

# ── Lambda: order_processor (SQS Trigger) — in VPC to reach RDS ─────────────
resource "aws_lambda_function" "order_processor" {
  function_name    = "${local.prefix}-order-processor"
  filename         = data.archive_file.order_processor.output_path
  source_code_hash = data.archive_file.order_processor.output_base64sha256
  handler          = "handler.handler"
  runtime          = "python3.12"
  role             = var.lambda_role_arn
  timeout          = 60
  memory_size      = 128  # free tier: 400,000 GB-seconds/month

  layers = [
    aws_lambda_layer_version.db_utils.arn,
    aws_lambda_layer_version.aws_clients.arn,
  ]

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = [var.lambda_sg_id]
  }

  environment {
    variables = {
      DB_HOST         = var.db_host
      DB_NAME         = var.db_name
      DB_USERNAME     = var.db_username
      DB_PASSWORD     = var.db_password
      SNS_TOPIC_ARN   = var.sns_topic_arn
      AWS_REGION_NAME = var.aws_region
    }
  }

  tags = { Name = "${local.prefix}-order-processor" }
}

resource "aws_lambda_event_source_mapping" "sqs_to_order_processor" {
  event_source_arn                   = aws_sqs_queue.order_processing.arn
  function_name                      = aws_lambda_function.order_processor.arn
  batch_size                         = 5
  maximum_batching_window_in_seconds = 10
  function_response_types            = ["ReportBatchItemFailures"]
}

# ── Lambda: notification_enricher (SNS Trigger) — no VPC needed ──────────────
resource "aws_lambda_function" "notification_enricher" {
  function_name    = "${local.prefix}-notification-enricher"
  filename         = data.archive_file.notification_enricher.output_path
  source_code_hash = data.archive_file.notification_enricher.output_base64sha256
  handler          = "handler.handler"
  runtime          = "python3.12"
  role             = var.lambda_role_arn
  timeout          = 60
  memory_size      = 128

  # No vpc_config — reaches S3/SNS/SES via public AWS endpoints (no NAT needed)
  layers = [aws_lambda_layer_version.aws_clients.arn]

  environment {
    variables = {
      NOTIFICATION_BUCKET = aws_s3_bucket.invoices.bucket
      SES_FROM_EMAIL      = var.ses_from_email
      AWS_REGION_NAME     = var.aws_region
    }
  }

  tags = { Name = "${local.prefix}-notification-enricher" }
}

resource "aws_lambda_permission" "sns_invoke_enricher" {
  statement_id  = "AllowSNSInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.notification_enricher.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = var.sns_topic_arn
}

resource "aws_sns_topic_subscription" "sns_to_enricher" {
  topic_arn = var.sns_topic_arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.notification_enricher.arn
}

# ── Lambda: invoice_generator (S3 + SQS Trigger) — no VPC needed ─────────────
resource "aws_lambda_function" "invoice_generator" {
  function_name    = "${local.prefix}-invoice-generator"
  filename         = data.archive_file.invoice_generator.output_path
  source_code_hash = data.archive_file.invoice_generator.output_base64sha256
  handler          = "handler.handler"
  runtime          = "python3.12"
  role             = var.lambda_role_arn
  timeout          = 60
  memory_size      = 128

  # No vpc_config — only needs S3 and SES, both reachable via public endpoints
  layers = [aws_lambda_layer_version.aws_clients.arn]

  environment {
    variables = {
      INVOICE_BUCKET  = aws_s3_bucket.invoices.bucket
      SES_FROM_EMAIL  = var.ses_from_email
      AWS_REGION_NAME = var.aws_region
    }
  }

  tags = { Name = "${local.prefix}-invoice-generator" }
}

resource "aws_lambda_permission" "s3_invoke_invoice" {
  statement_id  = "AllowS3Invoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.invoice_generator.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.invoices.arn
}

resource "aws_s3_bucket_notification" "invoice_trigger" {
  bucket = aws_s3_bucket.invoices.id

  lambda_function {
    lambda_function_arn = aws_lambda_function.invoice_generator.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "invoices/pending/"
    filter_suffix       = ".json"
  }

  depends_on = [aws_lambda_permission.s3_invoke_invoice]
}

# invoice_generator is triggered only by S3 (invoices/pending/) — no SQS mapping needed
