###############################################################################
# MODULE: SNS
# Creates: SNS topic for order notifications + email subscription
###############################################################################

resource "aws_sns_topic" "orders" {
  name         = "${var.project}-${var.environment}-notifications"
  display_name = "Food Delivery Order Notifications"

  tags = { Name = "${var.project}-${var.environment}-sns" }
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.orders.arn
  protocol  = "email"
  endpoint  = var.notification_email

  # Prevent Terraform from destroying/recreating this on every apply.
  # Recreating it deactivates the confirmed subscription and requires
  # the user to re-confirm every pipeline run.
  lifecycle {
    ignore_changes = [endpoint]
  }
}
