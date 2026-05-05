# Jenkins Setup Guide — Food Delivery CI/CD

## 1. Jenkins Prerequisites

Install these plugins in Jenkins (Manage Jenkins → Plugins):
- **Pipeline** (usually pre-installed)
- **Git**
- **Amazon ECR** (`amazon-ecr`)
- **AWS Credentials** (`aws-credentials`)
- **SSH Agent** (`ssh-agent`)
- **Credentials Binding** (`credentials-binding`)
- **Timestamper**
- **Workspace Cleanup**

---

## 2. Global Tool Configuration

Go to **Manage Jenkins → Tools**:

### JDK
- Name: `JDK-17`
- Install automatically → AdoptOpenJDK 17

### Maven
- Name: `Maven-3.9`
- Install automatically → 3.9.x

### Terraform (install on Jenkins agent)
```bash
# On Jenkins agent / EC2
wget https://releases.hashicorp.com/terraform/1.7.0/terraform_1.7.0_linux_amd64.zip
unzip terraform_1.7.0_linux_amd64.zip
sudo mv terraform /usr/local/bin/
terraform --version
```

---

## 3. Credentials Setup

Go to **Manage Jenkins → Credentials → System → Global credentials → Add Credential**:

| ID                   | Type                              | Value                                      |
|----------------------|-----------------------------------|--------------------------------------------|
| `aws-credentials`    | AWS Credentials                   | Your IAM Access Key ID + Secret Access Key |
| `db-password`        | Secret text                       | RDS MySQL master password                  |
| `jwt-secret`         | Secret text                       | Base64 JWT secret                          |
| `notification-email` | Secret text                       | Email for SNS subscription                 |
| `ec2-key-pair`       | SSH Username with private key     | ec2-user + your .pem private key content   |

### Required IAM Permissions for `aws-credentials`:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:*", "rds:*", "sns:*", "ecr:*",
        "iam:*", "s3:*", "dynamodb:*",
        "ssm:SendCommand", "ssm:GetCommandInvocation",
        "ssm:DescribeInstanceInformation",
        "sts:GetCallerIdentity"
      ],
      "Resource": "*"
    }
  ]
}
```

---

## 4. Create the Pipeline Job

1. **New Item** → name: `food-delivery-pipeline` → type: **Pipeline**
2. Under **Pipeline**:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: `https://github.com/your-org/food-delivery.git`
   - Credentials: your GitHub credentials
   - Branch: `*/main`
   - Script Path: `Jenkinsfile`
3. Under **Build Triggers**:
   - ✅ GitHub hook trigger for GITScm polling (or Poll SCM: `H/5 * * * *`)
4. Save

---

## 5. First Run — Bootstrap Backend

Before the first pipeline run, create the Terraform S3 backend:

```bash
# On your local machine or Jenkins agent
export AWS_ACCESS_KEY_ID=<your-key>
export AWS_SECRET_ACCESS_KEY=<your-secret>

chmod +x scripts/bootstrap-backend.sh
./scripts/bootstrap-backend.sh us-east-1 dev
```

---

## 6. Pipeline Flow

```
Push to GitHub
      │
      ▼
┌─────────────┐
│  Checkout   │  Clone repo, extract commit SHA
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│  Build & Test   │  mvn clean package, JUnit reports
└──────┬──────────┘
       │
       ▼
┌──────────────────┐
│  Docker Build    │  Build image tagged with commit SHA
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  Push to ECR     │  Push :SHA and :latest tags
└──────┬───────────┘
       │
       ▼
┌──────────────────────┐
│  Terraform Plan      │  Always runs — shows infra diff
└──────┬───────────────┘
       │
       ▼ (main/master branch only)
┌──────────────────────┐
│  Terraform Apply     │  Provision/update AWS infrastructure
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│  Deploy via SSM      │  Pull new image on EC2, restart container
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│  Health Check        │  Retry curl until app responds
└──────────────────────┘
```

---

## 7. Branch Strategy

| Branch         | Terraform Apply | Deploy | Environment |
|----------------|-----------------|--------|-------------|
| `main`/`master`| ✅ Yes          | ✅ Yes | prod        |
| `feature/*`    | ❌ Plan only    | ❌ No  | dev         |
| `develop`      | ❌ Plan only    | ❌ No  | dev         |

---

## 8. Environment Variables on EC2

The EC2 instance reads app config from `/etc/food-delivery.env`.
Create this file on first deploy or via SSM:

```bash
aws ssm send-command \
  --instance-ids i-xxxxxxxxxxxxxxxxx \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=[
    "cat > /etc/food-delivery.env << EOF\nDB_USERNAME=admin\nDB_PASSWORD=yourpassword\nSPRING_DATASOURCE_URL=jdbc:mysql://your-rds-endpoint:3306/food_delivery\nSNS_TOPIC_ARN=arn:aws:sns:us-east-1:123456789012:food-delivery-dev-notifications\nJWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970\nEOF"
  ]'
```
