#!/bin/bash
# deploy.sh
# Manual redeploy: build → push ECR → pull on EC2 via SSM
# Usage: ./scripts/deploy.sh [image-tag]

set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ENV="${ENVIRONMENT:-dev}"
PROJECT="food-delivery"
TAG="${1:-latest}"

echo "=== Manual Deploy: $PROJECT-$ENV:$TAG ==="

# Resolve account + ECR URL
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_URL="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}-${ENV}"

# ── Build & Push ──────────────────────────────────────────────────────────────
echo "Building Docker image..."
cd food-delivery
docker build -t "${ECR_URL}:${TAG}" -t "${ECR_URL}:latest" .

echo "Pushing to ECR..."
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ECR_URL"

docker push "${ECR_URL}:${TAG}"
docker push "${ECR_URL}:latest"
cd ..

# ── Deploy via SSM ────────────────────────────────────────────────────────────
INSTANCE_ID=$(aws ec2 describe-instances \
  --region "$REGION" \
  --filters \
    "Name=tag:Name,Values=${PROJECT}-${ENV}-app-server" \
    "Name=instance-state-name,Values=running" \
  --query 'Reservations[0].Instances[0].InstanceId' \
  --output text)

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "None" ]; then
    echo "ERROR: No running EC2 instance found for ${PROJECT}-${ENV}-app-server"
    exit 1
fi

echo "Deploying to instance: $INSTANCE_ID"

COMMAND_ID=$(aws ssm send-command \
  --region "$REGION" \
  --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --parameters "commands=[
    'aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ECR_URL',
    'docker pull ${ECR_URL}:${TAG}',
    'docker stop food-delivery || true',
    'docker rm food-delivery || true',
    'docker run -d --name food-delivery --restart unless-stopped -p 8080:8080 --env-file /etc/food-delivery.env ${ECR_URL}:${TAG}'
  ]" \
  --query 'Command.CommandId' \
  --output text)

echo "Waiting for SSM command $COMMAND_ID..."
aws ssm wait command-executed \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE_ID" \
  --region "$REGION"

STATUS=$(aws ssm get-command-invocation \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE_ID" \
  --region "$REGION" \
  --query 'Status' --output text)

echo "Deploy status: $STATUS"

# ── Health Check ──────────────────────────────────────────────────────────────
PUBLIC_IP=$(aws ec2 describe-instances \
  --instance-ids "$INSTANCE_ID" \
  --region "$REGION" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

echo "Checking health at http://${PUBLIC_IP}:8080..."
for i in $(seq 1 12); do
    if curl -sf "http://${PUBLIC_IP}:8080/api/restaurants" > /dev/null 2>&1; then
        echo "✅ App healthy at http://${PUBLIC_IP}:8080"
        exit 0
    fi
    echo "  Attempt $i/12 — waiting 10s..."
    sleep 10
done

echo "❌ Health check failed after 120s"
exit 1
