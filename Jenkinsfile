pipeline {

    agent any

    environment {
        AWS_REGION       = "ap-south-1"
        PROJECT          = "food-delivery"
        ENVIRONMENT      = "dev"
        APP_PORT         = "8080"
        TF_DIR           = "terraform"
        TF_IN_AUTOMATION = "true"
        PATH             = "C:\\Python312;C:\\Python312\\Scripts;${env.PATH}"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                git credentialsId: 'github-credentials',
                    url: 'https://github.com/pratham20021/food_delivery.git',
                    branch: 'main'
            }
        }

        stage('Build') {
            steps {
                bat 'mvn clean package -DskipTests --batch-mode -q'
            }
            post {
                success { archiveArtifacts artifacts: 'target/*.jar', fingerprint: true }
            }
        }

        stage('Lambda Layers') {
            steps {
                bat """
                    "C:/Python312/Scripts/pip.exe" install pymysql -t lambda/layers/db_utils/python/ --quiet
                    "C:/Python312/Scripts/pip.exe" install boto3   -t lambda/layers/aws_clients/python/ --quiet
                    echo Lambda layers ready
                """
            }
        }

        stage('Terraform ECR') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY'),
                    string(credentialsId: 'tf-db-password',        variable: 'TF_VAR_db_password'),
                    string(credentialsId: 'tf-notification-email', variable: 'TF_VAR_notification_email'),
                    string(credentialsId: 'tf-jwt-secret',         variable: 'TF_VAR_jwt_secret')
                ]) {
                    bat '''
                        cd %TF_DIR%
                        terraform init -input=false
                        terraform apply -target=module.ecr ^
                            -auto-approve -input=false ^
                            -var="aws_region=%AWS_REGION%" ^
                            -var="environment=%ENVIRONMENT%"
                    '''
                }
            }
        }

        stage('Docker Build & Push to ECR') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    script {
                        def accountId = bat(
                            script: 'aws sts get-caller-identity --query Account --output text',
                            returnStdout: true
                        ).trim().readLines().last()

                        env.ECR_URL   = "${accountId}.dkr.ecr.${AWS_REGION}.amazonaws.com/${PROJECT}-${ENVIRONMENT}"
                        env.IMAGE_TAG = env.BUILD_NUMBER

                        bat """
                            aws ecr get-login-password --region %AWS_REGION% | docker login --username AWS --password-stdin ${env.ECR_URL}
                            docker build -t ${env.ECR_URL}:${env.IMAGE_TAG} -t ${env.ECR_URL}:latest .
                            docker push ${env.ECR_URL}:${env.IMAGE_TAG}
                            docker push ${env.ECR_URL}:latest
                            docker rmi ${env.ECR_URL}:${env.IMAGE_TAG} ${env.ECR_URL}:latest || exit 0
                        """
                    }
                }
            }
        }

        stage('Terraform Apply') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY'),
                    string(credentialsId: 'tf-db-password',        variable: 'TF_VAR_db_password'),
                    string(credentialsId: 'tf-notification-email', variable: 'TF_VAR_notification_email'),
                    string(credentialsId: 'tf-jwt-secret',         variable: 'TF_VAR_jwt_secret')
                ]) {
                    bat '''
                        cd %TF_DIR%
                        terraform apply ^
                            -auto-approve -input=false ^
                            -var="aws_region=%AWS_REGION%" ^
                            -var="environment=%ENVIRONMENT%" ^
                            -var="db_instance_class=db.t3.micro"
                    '''
                }
            }
        }

        stage('Capture Outputs') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY'),
                    string(credentialsId: 'tf-db-password',        variable: 'DB_PASSWORD'),
                    string(credentialsId: 'tf-notification-email', variable: 'SES_FROM_EMAIL'),
                    string(credentialsId: 'tf-jwt-secret',         variable: 'JWT_SECRET')
                ]) {
                    script {
                        env.APP_IP = bat(
                            script: "cd %TF_DIR% && terraform output -raw app_public_ip",
                            returnStdout: true
                        ).trim().readLines().last()

                        env.SNS_TOPIC_ARN = bat(
                            script: "cd %TF_DIR% && terraform output -raw sns_topic_arn",
                            returnStdout: true
                        ).trim().readLines().last()

                        env.SQS_QUEUE_URL = bat(
                            script: "cd %TF_DIR% && terraform output -raw order_processing_queue_url",
                            returnStdout: true
                        ).trim().readLines().last()

                        env.RDS_ENDPOINT = bat(
                            script: "cd %TF_DIR% && terraform output -raw rds_endpoint",
                            returnStdout: true
                        ).trim().readLines().last()

                        env.APP_URL = "http://${env.APP_IP}:${APP_PORT}"
                        echo "App URL: ${env.APP_URL}"

                        def dbUrl = "jdbc:mysql://${env.RDS_ENDPOINT}/food_delivery?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"

                        bat """
                            aws ssm put-parameter --region %AWS_REGION% --name /food-delivery/dev/ecr-url        --value "${env.ECR_URL}"       --type String       --overwrite
                            aws ssm put-parameter --region %AWS_REGION% --name /food-delivery/dev/sns-topic-arn  --value "${env.SNS_TOPIC_ARN}" --type String       --overwrite
                            aws ssm put-parameter --region %AWS_REGION% --name /food-delivery/dev/sqs-queue-url  --value "${env.SQS_QUEUE_URL}" --type String       --overwrite
                            aws ssm put-parameter --region %AWS_REGION% --name /food-delivery/dev/ses-from-email --value "%SES_FROM_EMAIL%"      --type String       --overwrite
                            aws ssm put-parameter --region %AWS_REGION% --name /food-delivery/dev/db-url         --value "${dbUrl}"             --type String       --overwrite
                            aws ssm put-parameter --region %AWS_REGION% --name /food-delivery/dev/db-password    --value "%DB_PASSWORD%"        --type SecureString --overwrite
                            aws ssm put-parameter --region %AWS_REGION% --name /food-delivery/dev/jwt-secret     --value "%JWT_SECRET%"         --type SecureString --overwrite
                        """
                        echo "SSM parameters stored"

                        // Also subscribe email to SNS if not already confirmed
                        bat """
                            aws sns subscribe --region %AWS_REGION% ^
                                --topic-arn "${env.SNS_TOPIC_ARN}" ^
                                --protocol email ^
                                --notification-endpoint "%SES_FROM_EMAIL%" 2>nul || echo SNS subscription already exists
                        """
                    }
                }
            }
        }

        // Deploy to EC2 via SSM — writes a shell script to S3 then runs it on EC2
        // This avoids ALL quoting issues: the script file has no escaping problems
        stage('Deploy to EC2') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    script {
                        def instanceId = bat(
                            script: """aws ec2 describe-instances --region %AWS_REGION% ^
                                --filters "Name=ip-address,Values=${env.APP_IP}" "Name=instance-state-name,Values=running" ^
                                --query "Reservations[0].Instances[0].InstanceId" --output text""",
                            returnStdout: true
                        ).trim().readLines().last()

                        echo "Deploying to EC2: ${instanceId}"

                        // Write deploy script — use single quotes so Groovy does NOT interpolate $
                        // All $ variables are bash variables evaluated on EC2
                        def region = 'ap-south-1'
                        def deployScript = '''\
#!/bin/bash
set -euo pipefail
exec > /tmp/deploy.log 2>&1

REGION=ap-south-1
echo "=== Reading config from SSM ==="
ECR=$(aws ssm get-parameter --region $REGION --name /food-delivery/dev/ecr-url --query Parameter.Value --output text)
DB_URL=$(aws ssm get-parameter --region $REGION --name /food-delivery/dev/db-url --query Parameter.Value --output text)
DB_PASS=$(aws ssm get-parameter --region $REGION --name /food-delivery/dev/db-password --with-decryption --query Parameter.Value --output text)
SNS=$(aws ssm get-parameter --region $REGION --name /food-delivery/dev/sns-topic-arn --query Parameter.Value --output text)
SQS=$(aws ssm get-parameter --region $REGION --name /food-delivery/dev/sqs-queue-url --query Parameter.Value --output text)
SES=$(aws ssm get-parameter --region $REGION --name /food-delivery/dev/ses-from-email --query Parameter.Value --output text)
JWT=$(aws ssm get-parameter --region $REGION --name /food-delivery/dev/jwt-secret --with-decryption --query Parameter.Value --output text)

echo "ECR=$ECR"
echo "SNS=$SNS"
echo "SES=$SES"

echo "=== ECR Login ==="
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ECR

echo "=== Pull latest image ==="
docker pull $ECR:latest

echo "=== Stop old container ==="
docker stop food-delivery 2>/dev/null || true
docker rm   food-delivery 2>/dev/null || true

echo "=== Start new container ==="
docker run -d \
  --name food-delivery \
  --restart unless-stopped \
  -p 8080:8080 \
  -e AWS_REGION=$REGION \
  -e SNS_TOPIC_ARN=$SNS \
  -e SQS_ORDER_QUEUE_URL=$SQS \
  -e SES_FROM_EMAIL=$SES \
  -e JWT_SECRET=$JWT \
  -e DB_USERNAME=admin \
  -e DB_PASSWORD=$DB_PASS \
  -e SPRING_DATASOURCE_URL="$DB_URL" \
  $ECR:latest

echo "=== Waiting 15s ==="
sleep 15
docker ps | grep food-delivery
docker logs food-delivery --tail 30
echo "=== Deploy complete ==="
'''
                        // Write script to file, upload to S3, run on EC2 via SSM
                        writeFile file: 'deploy.sh', text: deployScript

                        def invoiceBucket = bat(
                            script: "cd %TF_DIR% && terraform output -raw invoice_bucket_name",
                            returnStdout: true
                        ).trim().readLines().last()

                        // Upload script to S3
                        bat "aws s3 cp deploy.sh s3://${invoiceBucket}/scripts/deploy.sh --region %AWS_REGION%"

                        // Build SSM JSON — single command: download from S3 and run
                        def ssmCmd = "aws s3 cp s3://${invoiceBucket}/scripts/deploy.sh /tmp/deploy.sh --region ${region} && chmod +x /tmp/deploy.sh && /tmp/deploy.sh && cat /tmp/deploy.log"
                        def ssmJson = """{"InstanceIds":["${instanceId}"],"DocumentName":"AWS-RunShellScript","Parameters":{"commands":["${ssmCmd}"]},"TimeoutSeconds":180}"""
                        writeFile file: 'ssm-deploy.json', text: ssmJson

                        def cmdId = bat(
                            script: 'aws ssm send-command --region %AWS_REGION% --cli-input-json file://ssm-deploy.json --query Command.CommandId --output text',
                            returnStdout: true
                        ).trim().readLines().last()

                        echo "SSM Command ID: ${cmdId} — waiting 90s..."
                        sleep(90)

                        bat """aws ssm get-command-invocation ^
                            --region %AWS_REGION% ^
                            --command-id ${cmdId} ^
                            --instance-id ${instanceId} ^
                            --query "{Status:Status,Output:StandardOutputContent,Error:StandardErrorContent}"
                        """
                    }
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def healthy = false
                    for (int i = 1; i <= 15; i++) {
                        def status = bat(
                            script: "curl -s -o NUL -w \"%%{http_code}\" http://${env.APP_IP}:${APP_PORT}/actuator/health || echo 000",
                            returnStdout: true
                        ).trim().readLines().last()

                        if (status == '200') {
                            echo "App is UP at ${env.APP_URL}"
                            healthy = true
                            break
                        }
                        echo "Attempt ${i}/15 - HTTP ${status} - retrying in 20s..."
                        sleep(20)
                    }
                    if (!healthy) echo "WARNING: check ${env.APP_URL}/actuator/health"
                }
            }
        }

        stage('Smoke Test') {
            steps {
                script {
                    bat """
                        echo --- Register user ---
                        curl -s -X POST http://${env.APP_IP}:${APP_PORT}/api/auth/register ^
                            -H "Content-Type: application/json" ^
                            -d "{\\"name\\":\\"CI Test\\",\\"email\\":\\"ci@test.com\\",\\"password\\":\\"password123\\"}"
                    """

                    def tokenResponse = bat(
                        script: """curl -s -X POST http://${env.APP_IP}:${APP_PORT}/api/auth/login ^
                            -H "Content-Type: application/json" ^
                            -d "{\\"email\\":\\"ci@test.com\\",\\"password\\":\\"password123\\"}" """,
                        returnStdout: true
                    ).trim().readLines().last()

                    def token = new groovy.json.JsonSlurper().parseText(tokenResponse)?.token ?: ''
                    echo "Token received: ${token ? 'YES' : 'NO'}"

                    bat """
                        echo --- Get restaurants ---
                        curl -s -H "Authorization: Bearer ${token}" http://${env.APP_IP}:${APP_PORT}/api/restaurants
                    """
                }
            }
        }
    }

    post {
        success {
            echo """
            ===========================================
               DEPLOYMENT SUCCESSFUL
               Build  : #${env.BUILD_NUMBER}
               App URL: ${env.APP_URL ?: 'see Capture Outputs stage'}
            ===========================================
            """
        }
        failure { echo "FAILED - Build #${env.BUILD_NUMBER} - check logs above" }
        always {
            bat 'docker logout || exit 0'
            cleanWs()
        }
    }
}
