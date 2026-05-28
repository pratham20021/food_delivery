#!/bin/bash
# deploy.sh — run on EC2 via SSM, reads all config from SSM Parameter Store
set -euo pipefail
exec > >(tee /var/log/deploy.log) 2>&1

REGION=$(curl -s http://169.254.169.254/latest/meta-data/placement/region)
APP_PORT=8080

echo "=== Reading config from SSM ==="
ECR_URL=$(aws ssm get-parameter --region "$REGION" --name /food-delivery/dev/ecr-url          --query Parameter.Value --output text)
DB_URL=$(aws ssm get-parameter  --region "$REGION" --name /food-delivery/dev/db-url           --query Parameter.Value --output text)
DB_PASS=$(aws ssm get-parameter --region "$REGION" --name /food-delivery/dev/db-password      --with-decryption --query Parameter.Value --output text)
SNS_ARN=$(aws ssm get-parameter --region "$REGION" --name /food-delivery/dev/sns-topic-arn    --query Parameter.Value --output text)
SQS_URL=$(aws ssm get-parameter --region "$REGION" --name /food-delivery/dev/sqs-queue-url    --query Parameter.Value --output text)
SES_EMAIL=$(aws ssm get-parameter --region "$REGION" --name /food-delivery/dev/ses-from-email --query Parameter.Value --output text)
JWT=$(aws ssm get-parameter     --region "$REGION" --name /food-delivery/dev/jwt-secret       --with-decryption --query Parameter.Value --output text)

echo "=== ECR Login ==="
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR_URL"

echo "=== Pull latest image ==="
docker pull "$ECR_URL:latest"

echo "=== Stop old container ==="
docker stop food-delivery 2>/dev/null || true
docker rm   food-delivery 2>/dev/null || true

echo "=== Start new container ==="
docker run -d \
  --name food-delivery \
  --restart unless-stopped \
  -p "$APP_PORT:$APP_PORT" \
  -e AWS_REGION="$REGION" \
  -e SNS_TOPIC_ARN="$SNS_ARN" \
  -e SQS_ORDER_QUEUE_URL="$SQS_URL" \
  -e SES_FROM_EMAIL="$SES_EMAIL" \
  -e JWT_SECRET="$JWT" \
  -e DB_USERNAME=admin \
  -e DB_PASSWORD="$DB_PASS" \
  -e SPRING_DATASOURCE_URL="$DB_URL" \
  "$ECR_URL:latest"

echo "=== Waiting 15s for startup ==="
sleep 15
docker ps | grep food-delivery
docker logs food-delivery --tail 30
echo "=== Deploy complete ==="
