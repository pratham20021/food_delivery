#!/bin/bash
set -euo pipefail
exec > >(tee /var/log/user-data.log | logger -t user-data) 2>&1

echo "=== Food Delivery Bootstrap START ==="

# ── System update & Docker install ───────────────────────────────────────────
dnf update -y
dnf install -y docker aws-cli

systemctl enable docker
systemctl start docker

# ── ECR Login ─────────────────────────────────────────────────────────────────
aws ecr get-login-password --region ${aws_region} \
  | docker login --username AWS --password-stdin ${ecr_repo_url}

# ── Pull latest image ─────────────────────────────────────────────────────────
docker pull ${ecr_repo_url}:latest

# ── Stop any existing container ───────────────────────────────────────────────
docker stop food-delivery 2>/dev/null || true
docker rm   food-delivery 2>/dev/null || true

# ── Run application container ─────────────────────────────────────────────────
docker run -d \
  --name food-delivery \
  --restart unless-stopped \
  -p ${app_port}:${app_port} \
  -e DB_USERNAME="${db_username}" \
  -e DB_PASSWORD="${db_password}" \
  -e AWS_REGION="${aws_region}" \
  -e SNS_TOPIC_ARN="${sns_topic_arn}" \
  -e SQS_ORDER_QUEUE_URL="${sqs_order_queue_url}" \
  -e JWT_SECRET="${jwt_secret}" \
  -e "SPRING_DATASOURCE_URL=jdbc:mysql://${db_endpoint}/${db_name}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true" \
  ${ecr_repo_url}:latest

# ── Health check ──────────────────────────────────────────────────────────────
echo "Waiting for app to start..."
for i in $(seq 1 12); do
  if curl -sf http://localhost:${app_port}/api/restaurants > /dev/null 2>&1; then
    echo "=== App is healthy after $((i * 5))s ==="
    break
  fi
  sleep 5
done

echo "=== Bootstrap COMPLETE ==="
