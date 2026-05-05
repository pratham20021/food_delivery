#!/bin/bash
# destroy.sh — Tear down all Terraform-managed infrastructure
# Usage: ./scripts/destroy.sh

set -euo pipefail

echo "⚠️  WARNING: This will DESTROY all infrastructure for food-delivery."
read -rp "Type 'yes' to confirm: " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Aborted."
    exit 0
fi

cd terraform

terraform destroy \
  -var="db_password=${TF_VAR_db_password}" \
  -var="notification_email=${TF_VAR_notification_email}" \
  -auto-approve

echo "✅ Infrastructure destroyed."
