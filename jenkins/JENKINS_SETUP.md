# Jenkins CI/CD Setup Guide

## Architecture
```
Your Machine (Docker)
    └── Jenkins Container
            ├── Builds JAR (Maven)
            ├── Builds Docker image
            ├── Pushes to AWS ECR
            └── Runs Terraform → provisions all AWS infra
                        └── EC2 pulls image from ECR on boot
```

---

## Step 1 — Start Jenkins

```bash
cd jenkins
docker compose up -d --build

# Wait ~60 seconds for Jenkins to start
docker logs -f jenkins
# Wait until you see: "Jenkins is fully up and running"
```

Open browser: http://localhost:8090

---

## Step 2 — Unlock Jenkins

```bash
# Get the initial admin password
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

- Paste it into the browser
- Click **Install suggested plugins**
- Create your admin user

---

## Step 3 — Install Required Plugins

Go to: **Manage Jenkins → Plugins → Available plugins**

Search and install:
- Pipeline
- Git
- Credentials Binding
- SSH Agent
- Workspace Cleanup

Click **Install** → restart Jenkins when prompted.

---

## Step 4 — Add Credentials

Go to: **Manage Jenkins → Credentials → System → Global credentials → Add Credential**

Add each one below exactly as shown:

| Kind | ID | Value |
|---|---|---|
| Secret text | `aws-access-key-id` | Your AWS Access Key ID |
| Secret text | `aws-secret-access-key` | Your AWS Secret Access Key |
| Secret text | `tf-db-password` | Your RDS password (e.g. MyPass123!) |
| Secret text | `tf-notification-email` | Your email for SNS notifications |
| Secret text | `tf-jwt-secret` | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` |
| Username/Password | `github-credentials` | GitHub username + Personal Access Token |

---

## Step 5 — Create the Pipeline Job

1. Click **New Item**
2. Name it: `food-delivery-pipeline`
3. Select **Pipeline** → click OK
4. Scroll to **Pipeline** section
5. Set **Definition** to: `Pipeline script from SCM`
6. Set **SCM** to: `Git`
7. Set **Repository URL**: `https://github.com/pratham20021/food_delivery.git`
8. Set **Credentials**: select `github-credentials`
9. Set **Branch**: `*/main`
10. Set **Script Path**: `Jenkinsfile`
11. Click **Save**

---

## Step 6 — Pre-flight: Create Terraform State Backend

Run these once before the first pipeline run:

```bash
aws configure   # if not already done

# State bucket
aws s3 mb s3://food-delivery-tfstate-ap-south-1 --region ap-south-1
aws s3api put-bucket-versioning \
    --bucket food-delivery-tfstate-ap-south-1 \
    --versioning-configuration Status=Enabled

# Lock table
aws dynamodb create-table \
    --table-name food-delivery-tfstate-lock \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region ap-south-1

# EC2 key pair
aws ec2 create-key-pair \
    --key-name food-delivery-key \
    --region ap-south-1 \
    --query 'KeyMaterial' \
    --output text > food-delivery-key.pem
chmod 400 food-delivery-key.pem
```

---

## Step 7 — Run the Pipeline

1. Open `food-delivery-pipeline` in Jenkins
2. Click **Build Now**
3. Click the build number → **Console Output** to watch live

### Pipeline Stages:
```
Checkout          → pulls code from GitHub
Build             → mvn clean package (builds JAR)
Lambda Layers     → pip install pymysql + boto3
Terraform ECR     → terraform apply -target=module.ecr
Docker Build+Push → builds image, pushes to ECR
Terraform Apply   → provisions all AWS infra (~10 min)
Capture Outputs   → reads EC2 public IP from terraform output
Health Check      → polls /actuator/health until 200 OK
Smoke Test        → registers user, logs in, hits /api/restaurants
```

---

## Step 8 — After Pipeline Succeeds

1. **Confirm SNS email** — check your inbox for AWS subscription confirmation email, click the link
2. **Access the app** — URL is printed in the "Capture Outputs" stage console output
3. **Check Lambda logs** in AWS Console → CloudWatch → Log groups → `/aws/lambda/food-delivery-dev-*`

---

## Teardown

To destroy all AWS resources:
```bash
cd terraform
terraform destroy -auto-approve
```

To stop Jenkins:
```bash
cd jenkins
docker compose down
```
