variable "project"              { type = string }
variable "environment"          { type = string }
variable "ami_id"               { type = string }
variable "instance_type"        { type = string }
variable "subnet_id"            { type = string }
variable "security_group_id"    { type = string }
variable "iam_instance_profile" { type = string }
variable "key_name"             { type = string }
variable "app_port"             { type = number }
variable "aws_region"           { type = string }
variable "ecr_repo_url"         { type = string }
variable "db_endpoint"          { type = string }
variable "db_name"              { type = string }
variable "db_username"          { type = string }
variable "db_password"          { type = string; sensitive = true }
variable "sns_topic_arn"        { type = string }
variable "jwt_secret"           { type = string; sensitive = true }
variable "sqs_order_queue_url"  { type = string }
