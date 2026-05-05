###############################################################################
# ROOT VARIABLES
###############################################################################

variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Project name used as a prefix for all resources"
  type        = string
  default     = "food-delivery"
}

variable "environment" {
  description = "Deployment environment (dev | staging | prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging, or prod."
  }
}

# ── Networking ────────────────────────────────────────────────────────────────
variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of AZs to spread subnets across"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

# ── Application ───────────────────────────────────────────────────────────────
variable "app_port" {
  description = "Port the Spring Boot app listens on"
  type        = number
  default     = 8080
}

variable "jwt_secret" {
  description = "Base64-encoded JWT signing secret"
  type        = string
  sensitive   = true
  default     = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
}

# ── EC2 ───────────────────────────────────────────────────────────────────────
variable "ec2_ami_id" {
  description = "Amazon Linux 2023 AMI ID (region-specific)"
  type        = string
  default     = "ami-0c02fb55956c7d316" # Amazon Linux 2023 us-east-1
}

variable "ec2_instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.small"
}

variable "ec2_key_name" {
  description = "Name of the EC2 key pair for SSH access"
  type        = string
  default     = "food-delivery-key"
}

# ── RDS ───────────────────────────────────────────────────────────────────────
variable "db_name" {
  description = "MySQL database name"
  type        = string
  default     = "food_delivery"
}

variable "db_username" {
  description = "MySQL master username"
  type        = string
  default     = "admin"
}

variable "db_password" {
  description = "MySQL master password"
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

# ── SNS ───────────────────────────────────────────────────────────────────────
variable "notification_email" {
  description = "Email address to subscribe to SNS order notifications"
  type        = string
}
