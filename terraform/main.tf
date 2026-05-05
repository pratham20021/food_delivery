###############################################################################
# ROOT MAIN — Food Delivery Infrastructure
# Orchestrates: VPC → Security Groups → IAM → ECR → SNS → RDS → EC2
###############################################################################

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state — swap bucket/key to match your account
  backend "s3" {
    bucket         = "food-delivery-tfstate"
    key            = "food-delivery/terraform.tfstate"
    region         = "us-east-1"
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

# ── VPC ───────────────────────────────────────────────────────────────────────
module "vpc" {
  source      = "./modules/vpc"
  project     = var.project
  environment = var.environment
  vpc_cidr    = var.vpc_cidr
  azs         = var.availability_zones
}

# ── Security Groups ───────────────────────────────────────────────────────────
module "security_groups" {
  source      = "./modules/security_groups"
  project     = var.project
  environment = var.environment
  vpc_id      = module.vpc.vpc_id
  app_port    = var.app_port
}

# ── IAM ───────────────────────────────────────────────────────────────────────
module "iam" {
  source      = "./modules/iam"
  project     = var.project
  environment = var.environment
  sns_topic_arn = module.sns.topic_arn
}

# ── ECR ───────────────────────────────────────────────────────────────────────
module "ecr" {
  source      = "./modules/ecr"
  project     = var.project
  environment = var.environment
}

# ── SNS ───────────────────────────────────────────────────────────────────────
module "sns" {
  source             = "./modules/sns"
  project            = var.project
  environment        = var.environment
  notification_email = var.notification_email
}

# ── RDS ───────────────────────────────────────────────────────────────────────
module "rds" {
  source              = "./modules/rds"
  project             = var.project
  environment         = var.environment
  subnet_ids          = module.vpc.private_subnet_ids
  security_group_id   = module.security_groups.rds_sg_id
  db_name             = var.db_name
  db_username         = var.db_username
  db_password         = var.db_password
  db_instance_class   = var.db_instance_class
}

# ── EC2 ───────────────────────────────────────────────────────────────────────
module "ec2" {
  source              = "./modules/ec2"
  project             = var.project
  environment         = var.environment
  subnet_id           = module.vpc.public_subnet_ids[0]
  security_group_id   = module.security_groups.app_sg_id
  iam_instance_profile = module.iam.instance_profile_name
  ami_id              = var.ec2_ami_id
  instance_type       = var.ec2_instance_type
  key_name            = var.ec2_key_name
  app_port            = var.app_port

  # Passed into user-data for bootstrap
  ecr_repo_url        = module.ecr.repository_url
  aws_region          = var.aws_region
  db_endpoint         = module.rds.db_endpoint
  db_name             = var.db_name
  db_username         = var.db_username
  db_password         = var.db_password
  sns_topic_arn       = module.sns.topic_arn
  jwt_secret          = var.jwt_secret
}
