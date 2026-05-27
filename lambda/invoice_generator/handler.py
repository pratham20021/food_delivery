"""
Lambda: invoice_generator
Trigger : S3 (invoice-bucket — prefix: invoices/pending/)
Layers  : aws_clients

Flow:
  notification_enricher uploads  invoices/pending/<orderId>.json  to S3
  →  S3 triggers this Lambda
  →  Lambda generates a plain-text invoice
  →  Uploads final invoice to  invoices/completed/<orderId>.txt
  →  Publishes SNS notification with invoice summary

Note: For a real PDF, swap the text generation with reportlab (add as a layer).
"""
import json
import logging
import os
from datetime import datetime

import aws_clients

log = logging.getLogger()
log.setLevel(logging.INFO)

INVOICE_BUCKET  = os.environ["INVOICE_BUCKET"]
SNS_TOPIC_ARN   = os.environ["SNS_TOPIC_ARN"]


def handler(event, context):
    for s3_record in event["Records"]:
        bucket = s3_record["s3"]["bucket"]["name"]
        key    = s3_record["s3"]["object"]["key"]

        # Only process files under invoices/pending/
        if not key.startswith("invoices/pending/"):
            continue

        log.info("Generating invoice for s3://%s/%s", bucket, key)

        # ── Read order data ───────────────────────────────────────────────────
        obj   = aws_clients.s3.get_object(Bucket=bucket, Key=key)
        order = json.loads(obj["Body"].read())

        # ── Generate invoice text ─────────────────────────────────────────────
        invoice_text = _build_invoice(order)
        order_id     = order["orderId"]

        # ── Upload completed invoice ──────────────────────────────────────────
        completed_key = f"invoices/completed/{order_id}.txt"
        aws_clients.s3.put_object(
            Bucket=INVOICE_BUCKET,
            Key=completed_key,
            Body=invoice_text.encode("utf-8"),
            ContentType="text/plain",
        )
        log.info("Invoice saved to s3://%s/%s", INVOICE_BUCKET, completed_key)

        # ── Notify via SNS ────────────────────────────────────────────────────
        aws_clients.sns.publish(
            TopicArn=SNS_TOPIC_ARN,
            Subject=f"🧾 Invoice Ready — Order #{order_id}",
            Message=(
                f"Your invoice for Order #{order_id} is ready.\n\n"
                f"{invoice_text}\n\n"
                f"Invoice stored at: s3://{INVOICE_BUCKET}/{completed_key}"
            ),
        )
        log.info("Invoice notification sent for orderId=%s", order_id)

    return {"statusCode": 200}


def _build_invoice(order: dict) -> str:
    now = datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
    return f"""
========================================
       FOOD DELIVERY — INVOICE
========================================
Invoice Date  : {now}
Order ID      : #{order['orderId']}
----------------------------------------
Customer      : {order['customerName']}
Email         : {order['customerEmail']}
Restaurant    : {order['restaurantName']}
Delivery Addr : {order['deliveryAddress']}
----------------------------------------
Total Amount  : ${order['totalAmount']:.2f}
Status        : DELIVERED
========================================
Thank you for ordering with Food Delivery!
""".strip()
