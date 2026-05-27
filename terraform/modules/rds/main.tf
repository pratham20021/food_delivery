###############################################################################
# MODULE: RDS
# Creates: MySQL 8.0 RDS instance in private subnets
###############################################################################

resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-${var.environment}-db-subnet-group"
  subnet_ids = var.subnet_ids

  tags = { Name = "${var.project}-${var.environment}-db-subnet-group" }
}

resource "aws_db_instance" "mysql" {
  identifier        = "${var.project}-${var.environment}-mysql"
  engine            = "mysql"
  engine_version    = "8.0"
  instance_class    = var.db_instance_class
  allocated_storage = 20
  storage_type      = "gp2"   # gp2 is free tier eligible (gp3 is not)
  storage_encrypted = false   # encryption not available on free tier db.t2.micro

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.security_group_id]

  multi_az               = false  # Set true for prod
  publicly_accessible    = false
  skip_final_snapshot    = true   # Set false for prod
  deletion_protection    = false  # Set true for prod

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  parameter_group_name = aws_db_parameter_group.mysql8.name

  tags = { Name = "${var.project}-${var.environment}-mysql" }
}

resource "aws_db_parameter_group" "mysql8" {
  name   = "${var.project}-${var.environment}-mysql8-params"
  family = "mysql8.0"

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }
}
