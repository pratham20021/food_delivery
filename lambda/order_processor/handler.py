"""
Lambda: order_processor
Trigger : SQS (order-processing-queue)
Layers  : db_utils, aws_clients

Flow:
  EC2 Spring Boot  →  SQS  →  This Lambda  →  RDS (update status)
                                            →  SNS (publish status event)

Each SQS message body is JSON:
{
  "orderId": 42,
  "action": "PROCESS_ORDER" | "STATUS_UPDATE",
  "newStatus": "PREPARING"          # only for STATUS_UPDATE
}
"""
import json
import logging
import os

import db_utils
import aws_clients

log = logging.getLogger()
log.setLevel(logging.INFO)

SNS_TOPIC_ARN = os.environ["SNS_TOPIC_ARN"]


def handler(event, context):
    for record in event["Records"]:
        body = json.loads(record["body"])
        order_id  = body["orderId"]
        action    = body.get("action", "PROCESS_ORDER")
        new_status = body.get("newStatus")

        log.info("Processing orderId=%s action=%s", order_id, action)

        conn = db_utils.get_connection()
        try:
            order = db_utils.fetch_order(conn, order_id)
            if not order:
                log.error("Order %s not found — skipping", order_id)
                continue

            if action == "STATUS_UPDATE" and new_status:
                db_utils.update_order_status(conn, order_id, new_status)
                order["status"] = new_status

            # Publish enriched event to SNS so notification_enricher picks it up
            aws_clients.sns.publish(
                TopicArn=SNS_TOPIC_ARN,
                Subject=f"ORDER_EVENT:{order['status']}",
                Message=json.dumps({
                    "orderId":        order["id"],
                    "status":         order["status"],
                    "customerName":   order["customer_name"],
                    "customerEmail":  order["customer_email"],
                    "restaurantName": order["restaurant_name"],
                    "totalAmount":    float(order["total_amount"]),
                    "deliveryAddress": order["delivery_address"],
                    "updatedAt":      str(order["updated_at"]),
                }),
                MessageAttributes={
                    "eventType": {
                        "DataType": "String",
                        "StringValue": "ORDER_STATUS_CHANGE",
                    }
                },
            )
            log.info("SNS event published for orderId=%s status=%s", order_id, order["status"])
        finally:
            conn.close()

    return {"statusCode": 200}
