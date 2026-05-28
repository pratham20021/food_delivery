###############################################################################
# MODULE: EC2
# Creates: App server with Elastic IP, bootstrapped via user-data
###############################################################################

data "aws_caller_identity" "current" {}

resource "aws_instance" "app" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = var.iam_instance_profile
  key_name               = var.key_name

  root_block_device {
    volume_size           = 20
    volume_type           = "gp3"
    delete_on_termination = true
    encrypted             = true
  }

  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    aws_region          = var.aws_region
    ecr_repo_url        = var.ecr_repo_url
    db_endpoint         = var.db_endpoint
    db_name             = var.db_name
    db_username         = var.db_username
    db_password         = var.db_password
    sns_topic_arn       = var.sns_topic_arn
    jwt_secret          = var.jwt_secret
    app_port            = var.app_port
    account_id          = data.aws_caller_identity.current.account_id
    sqs_order_queue_url = var.sqs_order_queue_url
    ses_from_email      = var.ses_from_email
  })

  tags = { Name = "${var.project}-${var.environment}-app-server" }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"
  tags     = { Name = "${var.project}-${var.environment}-app-eip" }
}
