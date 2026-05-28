"""
Lambda: notification_enricher
Trigger : SNS (order-notifications topic)
Layers  : aws_clients

Flow:
  SNS (ORDER_EVENT:<STATUS>) -> This Lambda
    -> SES email to customer for every status change
    -> S3 log (notifications/<orderId>/<STATUS>.json)
    -> On DELIVERED: upload invoices/pending/<orderId>.json
       -> S3 event triggers invoice_generator
"""
import json
import logging
import os

import aws_clients

log = logging.getLogger()
log.setLevel(logging.INFO)

NOTIFICATION_BUCKET = os.environ["NOTIFICATION_BUCKET"]
SES_FROM_EMAIL      = os.environ["SES_FROM_EMAIL"]

STATUS_SUBJECT = {
    "ORDER_RECEIVED":   "Order Received - Food Delivery",
    "PREPARING":        "Your Order is Being Prepared - Food Delivery",
    "OUT_FOR_DELIVERY": "Your Order is Out for Delivery - Food Delivery",
    "DELIVERED":        "Order Delivered - Enjoy your meal!",
}

STATUS_MSG = {
    "ORDER_RECEIVED":   "We have received your order and will start preparing it shortly.",
    "PREPARING":        "Our chefs are preparing your delicious meal!",
    "OUT_FOR_DELIVERY": "Your order is on its way. Estimated delivery: 30 minutes.",
    "DELIVERED":        "Your order has been delivered. Enjoy your meal!",
}


def handler(event, context):
    for record in event["Records"]:
        sns_msg = record["Sns"]
        subject = sns_msg.get("Subject", "")

        if not subject.startswith("ORDER_EVENT:"):
            log.info("Skipping non-order SNS message: %s", subject)
            continue

        order  = json.loads(sns_msg["Message"])
        status = order["status"]
        log.info("Enriching notification for orderId=%s status=%s", order["orderId"], status)

        # Send SES email to customer
        _send_status_email(order, status)

        # Log to S3
        aws_clients.s3.put_object(
            Bucket=NOTIFICATION_BUCKET,
            Key=f"notifications/{order['orderId']}/{status}.json",
            Body=json.dumps(order),
            ContentType="application/json",
        )
        log.info("Notification log saved for orderId=%s status=%s", order["orderId"], status)

        # On DELIVERED: upload invoice data to S3 -> triggers invoice_generator via S3 event
        if status == "DELIVERED":
            aws_clients.s3.put_object(
                Bucket=NOTIFICATION_BUCKET,
                Key=f"invoices/pending/{order['orderId']}.json",
                Body=json.dumps(order),
                ContentType="application/json",
            )
            log.info("Invoice pending file uploaded for orderId=%s", order["orderId"])

    return {"statusCode": 200}


def _send_status_email(order: dict, status: str):
    email_subject = STATUS_SUBJECT.get(status, "Food Delivery - Order Update")
    msg_body      = STATUS_MSG.get(status, "Your order status has been updated.")

    body_text = (
        f"Food Delivery - Order Status Update\n"
        f"=====================================\n"
        f"Order ID     : #{order['orderId']}\n"
        f"Status       : {status}\n"
        f"Customer     : {order['customerName']}\n"
        f"Restaurant   : {order['restaurantName']}\n"
        f"Total Amount : ${order['totalAmount']:.2f}\n"
        f"Delivery Addr: {order['deliveryAddress']}\n"
        f"Updated At   : {order['updatedAt']}\n"
        f"=====================================\n"
        f"{msg_body}"
    )

    try:
        aws_clients.ses.send_email(
            Source=SES_FROM_EMAIL,
            Destination={"ToAddresses": [order["customerEmail"]]},
            Message={
                "Subject": {"Data": email_subject},
                "Body":    {"Text": {"Data": body_text}},
            },
        )
        log.info("SES email sent to %s for orderId=%s status=%s",
                 order["customerEmail"], order["orderId"], status)
    except Exception as e:
        log.error("SES send failed for orderId=%s: %s", order["orderId"], e)
