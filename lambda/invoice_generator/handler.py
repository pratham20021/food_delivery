"""
Lambda: invoice_generator
Trigger : S3 (invoices/pending/<orderId>.json)
Layers  : aws_clients

Flow:
  notification_enricher uploads invoices/pending/<orderId>.json
  -> S3 triggers this Lambda
  -> Generates text invoice
  -> Saves to invoices/completed/<orderId>.txt
  -> Sends invoice email directly to customer via SES
"""
import json
import logging
import os
from datetime import datetime

import aws_clients

log = logging.getLogger()
log.setLevel(logging.INFO)

INVOICE_BUCKET = os.environ["INVOICE_BUCKET"]
SES_FROM_EMAIL = os.environ["SES_FROM_EMAIL"]


def handler(event, context):
    for s3_record in event["Records"]:
        key = s3_record["s3"]["object"]["key"]

        if not key.startswith("invoices/pending/"):
            continue

        bucket = s3_record["s3"]["bucket"]["name"]
        log.info("Generating invoice for s3://%s/%s", bucket, key)

        obj   = aws_clients.s3.get_object(Bucket=bucket, Key=key)
        order = json.loads(obj["Body"].read())

        invoice_text  = _build_invoice(order)
        order_id      = order["orderId"]
        completed_key = f"invoices/completed/{order_id}.txt"

        # Save completed invoice to S3
        aws_clients.s3.put_object(
            Bucket=INVOICE_BUCKET,
            Key=completed_key,
            Body=invoice_text.encode("utf-8"),
            ContentType="text/plain",
        )
        log.info("Invoice saved to s3://%s/%s", INVOICE_BUCKET, completed_key)

        # Email invoice directly to customer
        _send_invoice_email(order, invoice_text, order_id)

    return {"statusCode": 200}


def _send_invoice_email(order: dict, invoice_text: str, order_id):
    try:
        aws_clients.ses.send_email(
            Source=SES_FROM_EMAIL,
            Destination={"ToAddresses": [order["customerEmail"]]},
            Message={
                "Subject": {"Data": f"Your Invoice - Food Delivery Order #{order_id}"},
                "Body":    {"Text": {"Data": invoice_text}},
            },
        )
        log.info("Invoice email sent to %s for orderId=%s", order["customerEmail"], order_id)
    except Exception as e:
        log.error("SES invoice send failed for orderId=%s: %s", order_id, e)


def _build_invoice(order: dict) -> str:
    now = datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
    return (
        f"========================================\n"
        f"       FOOD DELIVERY - INVOICE\n"
        f"========================================\n"
        f"Invoice Date  : {now}\n"
        f"Order ID      : #{order['orderId']}\n"
        f"----------------------------------------\n"
        f"Customer      : {order['customerName']}\n"
        f"Email         : {order['customerEmail']}\n"
        f"Restaurant    : {order['restaurantName']}\n"
        f"Delivery Addr : {order['deliveryAddress']}\n"
        f"----------------------------------------\n"
        f"Total Amount  : ${order['totalAmount']:.2f}\n"
        f"Status        : DELIVERED\n"
        f"========================================\n"
        f"Thank you for ordering with Food Delivery!"
    )
