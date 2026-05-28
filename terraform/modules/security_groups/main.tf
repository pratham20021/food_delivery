###############################################################################
# MODULE: SECURITY GROUPS
###############################################################################

# ── App Server SG ─────────────────────────────────────────────────────────────
resource "aws_security_group" "app" {
  name        = "${var.project}-${var.environment}-app-sg"
  description = "Allow HTTP app traffic and SSH"
  vpc_id      = var.vpc_id

  ingress {
    description = "App port"
    from_port   = var.app_port
    to_port     = var.app_port
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"] # Restrict to your IP in production
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project}-${var.environment}-app-sg" }
}

# ── RDS SG ────────────────────────────────────────────────────────────────────
resource "aws_security_group" "rds" {
  name        = "${var.project}-${var.environment}-rds-sg"
  description = "Allow MySQL only from app and Lambda security groups"
  vpc_id      = var.vpc_id

  ingress {
    description     = "MySQL from app"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  ingress {
    description     = "MySQL from Lambda"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project}-${var.environment}-rds-sg" }
}

# ── Lambda SG ─────────────────────────────────────────────────────────────────
resource "aws_security_group" "lambda" {
  name        = "${var.project}-${var.environment}-lambda-sg"
  description = "Lambda functions - outbound only (RDS, AWS APIs)"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project}-${var.environment}-lambda-sg" }
}
