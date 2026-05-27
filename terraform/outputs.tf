###############################################################################
# ROOT OUTPUTS
###############################################################################

output "app_public_ip" {
  description = "Public IP of the EC2 application server"
  value       = module.ec2.public_ip
}

output "app_url" {
  description = "Application URL"
  value       = "http://${module.ec2.public_ip}:${var.app_port}"
}

output "ecr_repository_url" {
  description = "ECR repository URL for Docker image pushes"
  value       = module.ecr.repository_url
}

output "rds_endpoint" {
  description = "RDS MySQL endpoint"
  value       = module.rds.db_endpoint
  sensitive   = true
}

output "sns_topic_arn" {
  description = "SNS topic ARN for order notifications"
  value       = module.sns.topic_arn
}

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "order_processing_queue_url" {
  description = "SQS URL for order processing (EC2 publishes here)"
  value       = module.lambda.order_processing_queue_url
}

output "invoice_bucket_name" {
  description = "S3 bucket for generated invoices"
  value       = module.lambda.invoice_bucket_name
}

output "order_processor_lambda_arn" {
  description = "ARN of the order_processor Lambda"
  value       = module.lambda.order_processor_arn
}

output "notification_enricher_lambda_arn" {
  description = "ARN of the notification_enricher Lambda"
  value       = module.lambda.notification_enricher_arn
}

output "invoice_generator_lambda_arn" {
  description = "ARN of the invoice_generator Lambda"
  value       = module.lambda.invoice_generator_arn
}
