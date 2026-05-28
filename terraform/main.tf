###############################################################################
# ROOT MAIN — Food Delivery Infrastructure
###############################################################################

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "food-delivery-tfstate-753668405724-dev"
    key            = "food-delivery/terraform.tfstate"
    region         = "ap-south-1"
    encrypt        = true
    dynamodb_table = "food-delivery-tfstate-lock"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "food-delivery"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

module "vpc" {
  source      = "./modules/vpc"
  project     = var.project
  environment = var.environment
  vpc_cidr    = var.vpc_cidr
  azs         = var.availability_zones
}

module "security_groups" {
  source      = "./modules/security_groups"
  project     = var.project
  environment = var.environment
  vpc_id      = module.vpc.vpc_id
  app_port    = var.app_port
}

module "sns" {
  source      = "./modules/sns"
  project     = var.project
  environment = var.environment
}

module "iam" {
  source        = "./modules/iam"
  project       = var.project
  environment   = var.environment
  sns_topic_arn = module.sns.topic_arn
}

module "ecr" {
  source      = "./modules/ecr"
  project     = var.project
  environment = var.environment
}

module "rds" {
  source            = "./modules/rds"
  project           = var.project
  environment       = var.environment
  subnet_ids        = module.vpc.public_subnet_ids
  security_group_id = module.security_groups.rds_sg_id
  db_name           = var.db_name
  db_username       = var.db_username
  db_password       = var.db_password
  db_instance_class = var.db_instance_class
}

module "lambda" {
  source          = "./modules/lambda"
  project         = var.project
  environment     = var.environment
  aws_region      = var.aws_region
  sns_topic_arn   = module.sns.topic_arn
  sns_topic_name  = module.sns.topic_name
  db_host         = module.rds.db_endpoint
  db_name         = var.db_name
  db_username     = var.db_username
  db_password     = var.db_password
  subnet_ids      = module.vpc.public_subnet_ids
  lambda_sg_id    = module.security_groups.lambda_sg_id
  lambda_role_arn = module.iam.lambda_role_arn
  ses_from_email  = var.notification_email
}

module "ec2" {
  source               = "./modules/ec2"
  project              = var.project
  environment          = var.environment
  subnet_id            = module.vpc.public_subnet_ids[0]
  security_group_id    = module.security_groups.app_sg_id
  iam_instance_profile = module.iam.instance_profile_name
  ami_id               = var.ec2_ami_id
  instance_type        = var.ec2_instance_type
  key_name             = var.ec2_key_name
  app_port             = var.app_port
  ecr_repo_url         = module.ecr.repository_url
  aws_region           = var.aws_region
  db_endpoint          = module.rds.db_endpoint
  db_name              = var.db_name
  db_username          = var.db_username
  db_password          = var.db_password
  sns_topic_arn        = module.sns.topic_arn
  jwt_secret           = var.jwt_secret
  sqs_order_queue_url  = module.lambda.order_processing_queue_url
  ses_from_email       = var.notification_email
}
