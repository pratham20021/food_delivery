#!/bin/bash
set -euo pipefail
exec > >(tee /var/log/user-data.log | logger -t user-data) 2>&1

echo "=== Food Delivery Bootstrap START ==="

# ── System update & Docker install ───────────────────────────────────────────
apt-get update -y
apt-get install -y ca-certificates curl gnupg unzip

# Install Docker
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io

systemctl enable docker
systemctl start docker

# Install AWS CLI v2
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install
rm -rf /tmp/aws /tmp/awscliv2.zip

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
  -e JWT_SECRET="${jwt_secret}" \
  -e "SPRING_DATASOURCE_URL=jdbc:mysql://${db_endpoint}/${db_name}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true" \
  ${ecr_repo_url}:latest

# ── Health check ──────────────────────────────────────────────────────────────
echo "Waiting for app to start..."
for i in $(seq 1 24); do
  if curl -sf http://localhost:${app_port}/api/restaurants > /dev/null 2>&1; then
    echo "=== App is healthy after $((i * 5))s ==="
    break
  fi
  sleep 5
done

echo "=== Bootstrap COMPLETE ==="
