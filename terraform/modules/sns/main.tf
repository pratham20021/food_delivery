###############################################################################
# MODULE: SNS
# Creates: SNS topic only.
# NOTE: Email subscription is managed manually (not by Terraform) to prevent
#       it from being recreated on every apply which deactivates confirmation.
#       Run once manually:
#       aws sns subscribe --topic-arn <arn> --protocol email --notification-endpoint <email>
###############################################################################

resource "aws_sns_topic" "orders" {
  name         = "${var.project}-${var.environment}-notifications"
  display_name = "Food Delivery Order Notifications"

  tags = { Name = "${var.project}-${var.environment}-sns" }
}
