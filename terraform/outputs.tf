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
