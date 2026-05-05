# Food Delivery App — Deployment & Setup Guide

## Prerequisites
- Java 17, Maven 3.8+, MySQL 8.0+
- AWS Account with SNS access
- AWS CLI configured

---

## 1. AWS SNS Setup

### Create SNS Topic
```bash
aws sns create-topic --name food-delivery-notifications --region us-east-1
# Save the TopicArn from output: arn:aws:sns:us-east-1:<account-id>:food-delivery-notifications
```

### Subscribe Email
```bash
aws sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:<account-id>:food-delivery-notifications \
  --protocol email \
  --notification-endpoint your-email@example.com \
  --region us-east-1
# Check your email and click "Confirm subscription"
```

### Verify Subscription
```bash
aws sns list-subscriptions-by-topic \
  --topic-arn arn:aws:sns:us-east-1:<account-id>:food-delivery-notifications
```

---

## 2. IAM Policy for SNS

Attach this policy to your EC2 IAM role or IAM user:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["sns:Publish", "sns:ListTopics"],
    "Resource": "arn:aws:sns:us-east-1:<account-id>:food-delivery-notifications"
  }]
}
```

---

## 3. Local Development

### Start MySQL + App with Docker Compose
```bash
export SNS_TOPIC_ARN=arn:aws:sns:us-east-1:<account-id>:food-delivery-notifications
export AWS_ACCESS_KEY_ID=<your-key>
export AWS_SECRET_ACCESS_KEY=<your-secret>

docker-compose up --build
```

### Or run locally with Maven
```bash
# Start MySQL first, then:
export DB_USERNAME=root
export DB_PASSWORD=root
export SNS_TOPIC_ARN=arn:aws:sns:us-east-1:<account-id>:food-delivery-notifications

mvn spring-boot:run
```

App runs at: http://localhost:8080

---

## 4. EC2 Deployment (Amazon Linux 2023)

### Launch EC2
- AMI: Amazon Linux 2023
- Instance type: t3.small (minimum)
- Security Group: Allow inbound TCP 8080, 22
- IAM Role: Attach role with SNS publish permission

### Install Dependencies
```bash
# Connect to EC2
ssh -i your-key.pem ec2-user@<ec2-public-ip>

# Install Java 17
sudo dnf install -y java-17-amazon-corretto-devel

# Install Maven
sudo dnf install -y maven

# Install Docker
sudo dnf install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user
newgrp docker

# Install MySQL client (optional)
sudo dnf install -y mysql
```

### Deploy Application
```bash
# Clone or upload your project
git clone <your-repo-url> food-delivery
cd food-delivery

# Set environment variables
export DB_USERNAME=root
export DB_PASSWORD=yourpassword
export SNS_TOPIC_ARN=arn:aws:sns:us-east-1:<account-id>:food-delivery-notifications
export AWS_REGION=us-east-1
# Note: On EC2 with IAM role, no need for AWS_ACCESS_KEY_ID/SECRET

# Option A: Run with Docker Compose
docker-compose up -d

# Option B: Build JAR and run directly
mvn clean package -DskipTests
java -jar target/food-delivery-1.0.0.jar
```

### Run as systemd service
```bash
sudo tee /etc/systemd/system/food-delivery.service > /dev/null <<EOF
[Unit]
Description=Food Delivery App
After=network.target

[Service]
User=ec2-user
WorkingDirectory=/home/ec2-user/food-delivery
ExecStart=/usr/bin/java -jar target/food-delivery-1.0.0.jar
Environment="DB_USERNAME=root"
Environment="DB_PASSWORD=yourpassword"
Environment="SNS_TOPIC_ARN=arn:aws:sns:us-east-1:<account-id>:food-delivery-notifications"
Restart=always

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable food-delivery
sudo systemctl start food-delivery
sudo systemctl status food-delivery
```

---

## 5. API Documentation

### Base URL
```
http://localhost:8080/api
```

### Authentication

#### Register
```http
POST /api/auth/register
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "password123",
  "phone": "+1234567890"
}
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "password123"
}
```
Response includes `token` — use as `Authorization: Bearer <token>` for all subsequent requests.

---

### Restaurants

#### List All Restaurants
```http
GET /api/restaurants
Authorization: Bearer <token>
```

#### Get Restaurant by ID
```http
GET /api/restaurants/1
Authorization: Bearer <token>
```

#### Get Menu for Restaurant
```http
GET /api/restaurants/1/menu
Authorization: Bearer <token>
```

---

### Orders

#### Place Order
```http
POST /api/orders
Authorization: Bearer <token>
Content-Type: application/json

{
  "restaurantId": 1,
  "deliveryAddress": "123 Main St, City",
  "items": [
    { "menuItemId": 1, "quantity": 2 },
    { "menuItemId": 3, "quantity": 1 }
  ]
}
```

#### Get My Orders
```http
GET /api/orders
Authorization: Bearer <token>
```

#### Get Order by ID
```http
GET /api/orders/1
Authorization: Bearer <token>
```

#### Update Order Status (Admin/System)
```http
PUT /api/orders/1/status?status=PREPARING
Authorization: Bearer <token>
```
Valid status transitions:
- ORDER_RECEIVED → PREPARING
- PREPARING → OUT_FOR_DELIVERY
- OUT_FOR_DELIVERY → DELIVERED

---

## 6. Order Status Flow & SNS Notifications

Each status change triggers an SNS email notification:

| Status            | Email Subject                        |
|-------------------|--------------------------------------|
| ORDER_RECEIVED    | 🍽️ Order Received!                  |
| PREPARING         | 👨‍🍳 Your Order is Being Prepared   |
| OUT_FOR_DELIVERY  | 🚴 Your Order is Out for Delivery    |
| DELIVERED         | ✅ Order Delivered!                  |

### Sample Email Body
```
Food Delivery - Order Status Update
=====================================
Order ID    : #42
Status      : PREPARING
Customer    : John Doe (john@example.com)
Restaurant  : Pizza Palace
Total Amount: $27.97
Updated At  : 2024-01-15 14:30:00
=====================================
Our chefs are preparing your delicious meal!
```

---

## 7. Environment Variables Reference

| Variable              | Description                    | Default         |
|-----------------------|--------------------------------|-----------------|
| DB_USERNAME           | MySQL username                 | root            |
| DB_PASSWORD           | MySQL password                 | root            |
| AWS_REGION            | AWS region                     | us-east-1       |
| SNS_TOPIC_ARN         | Full SNS topic ARN             | (required)      |
| JWT_SECRET            | JWT signing secret (base64)    | (default set)   |
| AWS_ACCESS_KEY_ID     | AWS access key (local only)    | (IAM role on EC2)|
| AWS_SECRET_ACCESS_KEY | AWS secret key (local only)    | (IAM role on EC2)|
