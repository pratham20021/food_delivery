"""
Lambda: notification_enricher
Trigger : SNS (order-notifications topic)
Layers  : aws_clients

Flow:
  SNS  →  This Lambda  →  SES rich HTML email to customer
                       →  S3 (store notification log)
                       →  SQS (trigger invoice generation for DELIVERED orders)

Receives the JSON message published by order_processor.
"""
import json
import logging
import os

import aws_clients

log = logging.getLogger()
log.setLevel(logging.INFO)

INVOICE_QUEUE_URL = os.environ["INVOICE_QUEUE_URL"]
NOTIFICATION_BUCKET = os.environ["NOTIFICATION_BUCKET"]

STATUS_EMOJI = {
    "ORDER_RECEIVED":   "🍽️",
    "PREPARING":        "👨‍🍳",
    "OUT_FOR_DELIVERY": "🚴",
    "DELIVERED":        "✅",
}

STATUS_MSG = {
    "ORDER_RECEIVED":   "We have received your order and will start preparing it shortly.",
    "PREPARING":        "Our chefs are preparing your delicious meal!",
    "OUT_FOR_DELIVERY": "Your order is on its way. Estimated delivery: 30 minutes.",
    "DELIVERED":        "Your order has been delivered. Enjoy your meal! 🎉",
}


def handler(event, context):
    for record in event["Records"]:
        sns_msg = record["Sns"]
        subject = sns_msg.get("Subject", "")

        # Only handle ORDER_EVENT messages published by order_processor
        if not subject.startswith("ORDER_EVENT:"):
            log.info("Skipping non-order SNS message: %s", subject)
            continue

        order = json.loads(sns_msg["Message"])
        status = order["status"]
        log.info("Enriching notification for orderId=%s status=%s", order["orderId"], status)

        emoji = STATUS_EMOJI.get(status, "📦")
        msg   = STATUS_MSG.get(status, "Your order status has been updated.")

        # ── Log notification to S3 ────────────────────────────────────────────
        log_key = f"notifications/{order['orderId']}/{status}.json"
        aws_clients.s3.put_object(
            Bucket=NOTIFICATION_BUCKET,
            Key=log_key,
            Body=json.dumps(order),
            ContentType="application/json",
        )
        log.info("Notification log saved to s3://%s/%s", NOTIFICATION_BUCKET, log_key)

        # ── For DELIVERED orders — push to invoice queue ──────────────────────
        if status == "DELIVERED":
            aws_clients.sqs.send_message(
                QueueUrl=INVOICE_QUEUE_URL,
                MessageBody=json.dumps({
                    "orderId":       order["orderId"],
                    "customerName":  order["customerName"],
                    "customerEmail": order["customerEmail"],
                    "restaurantName": order["restaurantName"],
                    "totalAmount":   order["totalAmount"],
                    "deliveryAddress": order["deliveryAddress"],
                    "updatedAt":     order["updatedAt"],
                }),
            )
            log.info("Invoice job queued for orderId=%s", order["orderId"])

    return {"statusCode": 200}
