#!/bin/bash
# bootstrap-backend.sh
# Run ONCE before the first `terraform init` to create the S3 + DynamoDB backend.
# Usage: ./scripts/bootstrap-backend.sh [region] [environment]

set -euo pipefail

REGION="${1:-us-east-1}"
ENV="${2:-dev}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET="food-delivery-tfstate-${ACCOUNT_ID}-${ENV}"
TABLE="food-delivery-tfstate-lock"

echo "=== Creating Terraform backend resources ==="
echo "Region  : $REGION"
echo "Bucket  : $BUCKET"
echo "Table   : $TABLE"

# ── S3 Bucket ─────────────────────────────────────────────────────────────────
if aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
    echo "✓ S3 bucket already exists: $BUCKET"
else
    aws s3api create-bucket \
        --bucket "$BUCKET" \
        --region "$REGION" \
        $([ "$REGION" != "us-east-1" ] && echo "--create-bucket-configuration LocationConstraint=$REGION")

    aws s3api put-bucket-versioning \
        --bucket "$BUCKET" \
        --versioning-configuration Status=Enabled

    aws s3api put-bucket-encryption \
        --bucket "$BUCKET" \
        --server-side-encryption-configuration '{
            "Rules": [{
                "ApplyServerSideEncryptionByDefault": {
                    "SSEAlgorithm": "AES256"
                }
            }]
        }'

    aws s3api put-public-access-block \
        --bucket "$BUCKET" \
        --public-access-block-configuration \
            "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

    echo "✓ S3 bucket created: $BUCKET"
fi

# ── DynamoDB Lock Table ───────────────────────────────────────────────────────
if aws dynamodb describe-table --table-name "$TABLE" --region "$REGION" 2>/dev/null; then
    echo "✓ DynamoDB table already exists: $TABLE"
else
    aws dynamodb create-table \
        --table-name "$TABLE" \
        --attribute-definitions AttributeName=LockID,AttributeType=S \
        --key-schema AttributeName=LockID,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST \
        --region "$REGION"

    echo "✓ DynamoDB table created: $TABLE"
fi

# ── Update backend bucket name in main.tf ─────────────────────────────────────
sed -i "s/bucket.*=.*\"food-delivery-tfstate\"/bucket = \"$BUCKET\"/" terraform/main.tf
echo "✓ Updated backend bucket in terraform/main.tf"

echo ""
echo "=== Backend ready. Now run: ==="
echo "  cd terraform"
echo "  terraform init"
echo "  terraform plan -var='db_password=SECRET' -var='notification_email=you@example.com'"
