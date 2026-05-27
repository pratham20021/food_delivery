output "order_processing_queue_url" { value = aws_sqs_queue.order_processing.url }
output "order_processing_queue_arn" { value = aws_sqs_queue.order_processing.arn }
output "invoice_queue_arn"          { value = aws_sqs_queue.invoice.arn }
output "invoice_bucket_name"        { value = aws_s3_bucket.invoices.bucket }
output "order_processor_arn"        { value = aws_lambda_function.order_processor.arn }
output "notification_enricher_arn"  { value = aws_lambda_function.notification_enricher.arn }
output "invoice_generator_arn"      { value = aws_lambda_function.invoice_generator.arn }
