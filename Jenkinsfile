///////////////////////////////////////////////////////////////////////////////
// Food Delivery — Jenkins Declarative Pipeline
//
// Stages:
//   1. Checkout          — clone source
//   2. Build & Test      — Maven compile + unit tests
//   3. Code Quality      — (optional) SonarQube scan placeholder
//   4. Docker Build      — build image, tag with commit SHA + latest
//   5. Push to ECR       — authenticate and push both tags
//   6. Terraform Plan    — show infra diff (always runs)
//   7. Terraform Apply   — apply only on main/master branch
//   8. Deploy            — pull new image on EC2 via SSM
//   9. Health Check      — verify app is responding
//
// Required Jenkins credentials:
//   aws-credentials      → AWS Access Key ID + Secret (type: AWS Credentials)
//   db-password          → RDS master password       (type: Secret text)
//   jwt-secret           → JWT signing secret        (type: Secret text)
//   notification-email   → SNS subscription email    (type: Secret text)
//   ec2-key-pair         → SSH private key           (type: SSH Username with private key)
///////////////////////////////////////////////////////////////////////////////

pipeline {

    agent any

    // ── Tool versions (configure these in Jenkins → Global Tool Config) ────────
    tools {
        maven 'Maven-3.9'
        jdk   'JDK-17'
    }

    // ── Pipeline-wide environment ─────────────────────────────────────────────
    environment {
        AWS_REGION       = 'us-east-1'
        PROJECT          = 'food-delivery'
        ENVIRONMENT      = "${env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master' ? 'prod' : 'dev'}"
        IMAGE_TAG        = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
        TF_DIR           = 'terraform'
        TF_VAR_FILE      = 'terraform/terraform.tfvars'
        DOCKER_BUILDKIT  = '1'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        // ── STAGE 1: Checkout ─────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG        = env.GIT_COMMIT_SHORT
                    echo "Building commit: ${env.GIT_COMMIT_SHORT} on branch: ${env.BRANCH_NAME}"
                }
            }
        }

        // ── STAGE 2: Build & Test ─────────────────────────────────────────────
        stage('Build & Test') {
            steps {
                dir('food-delivery') {
                    sh 'mvn clean package -DskipTests=false --batch-mode'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'food-delivery/target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'food-delivery/target/*.jar',
                                     fingerprint: true,
                                     allowEmptyArchive: true
                }
            }
        }

        // ── STAGE 3: Docker Build ─────────────────────────────────────────────
        stage('Docker Build') {
            steps {
                withCredentials([[
                    $class:            'AmazonWebServicesCredentialsBinding',
                    credentialsId:     'aws-credentials',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    script {
                        // Resolve ECR repo URL from AWS
                        env.AWS_ACCOUNT_ID = sh(
                            script: "aws sts get-caller-identity --query Account --output text --region ${AWS_REGION}",
                            returnStdout: true
                        ).trim()
                        env.ECR_REPO = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${PROJECT}-${ENVIRONMENT}"

                        dir('food-delivery') {
                            sh """
                                docker build \
                                  --build-arg BUILDKIT_INLINE_CACHE=1 \
                                  --cache-from ${env.ECR_REPO}:latest \
                                  -t ${env.ECR_REPO}:${IMAGE_TAG} \
                                  -t ${env.ECR_REPO}:latest \
                                  .
                            """
                        }
                    }
                }
            }
        }

        // ── STAGE 4: Push to ECR ──────────────────────────────────────────────
        stage('Push to ECR') {
            steps {
                withCredentials([[
                    $class:            'AmazonWebServicesCredentialsBinding',
                    credentialsId:     'aws-credentials',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} \
                          | docker login --username AWS --password-stdin ${env.ECR_REPO}

                        docker push ${env.ECR_REPO}:${IMAGE_TAG}
                        docker push ${env.ECR_REPO}:latest

                        echo "Pushed: ${env.ECR_REPO}:${IMAGE_TAG}"
                    """
                }
            }
            post {
                always {
                    // Clean up local images to save disk space
                    sh "docker rmi ${env.ECR_REPO}:${IMAGE_TAG} ${env.ECR_REPO}:latest || true"
                }
            }
        }

        // ── STAGE 5: Terraform Plan ───────────────────────────────────────────
        stage('Terraform Plan') {
            steps {
                withCredentials([
                    [
                        $class:            'AmazonWebServicesCredentialsBinding',
                        credentialsId:     'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ],
                    string(credentialsId: 'db-password',        variable: 'TF_VAR_db_password'),
                    string(credentialsId: 'jwt-secret',         variable: 'TF_VAR_jwt_secret'),
                    string(credentialsId: 'notification-email', variable: 'TF_VAR_notification_email')
                ]) {
                    dir("${TF_DIR}") {
                        sh """
                            terraform init -input=false

                            terraform plan \
                              -var="environment=${ENVIRONMENT}" \
                              -var="aws_region=${AWS_REGION}" \
                              -out=tfplan \
                              -input=false

                            terraform show -no-color tfplan > tfplan.txt
                        """
                        archiveArtifacts artifacts: 'tfplan.txt', fingerprint: true
                    }
                }
            }
        }

        // ── STAGE 6: Terraform Apply ──────────────────────────────────────────
        stage('Terraform Apply') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                }
            }
            steps {
                withCredentials([
                    [
                        $class:            'AmazonWebServicesCredentialsBinding',
                        credentialsId:     'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ],
                    string(credentialsId: 'db-password',        variable: 'TF_VAR_db_password'),
                    string(credentialsId: 'jwt-secret',         variable: 'TF_VAR_jwt_secret'),
                    string(credentialsId: 'notification-email', variable: 'TF_VAR_notification_email')
                ]) {
                    dir("${TF_DIR}") {
                        sh 'terraform apply -input=false -auto-approve tfplan'

                        // Capture outputs for downstream stages
                        script {
                            env.EC2_PUBLIC_IP = sh(
                                script: "terraform output -raw app_public_ip",
                                returnStdout: true
                            ).trim()
                            env.SNS_TOPIC_ARN = sh(
                                script: "terraform output -raw sns_topic_arn",
                                returnStdout: true
                            ).trim()
                            echo "EC2 IP: ${env.EC2_PUBLIC_IP}"
                            echo "SNS ARN: ${env.SNS_TOPIC_ARN}"
                        }
                    }
                }
            }
        }

        // ── STAGE 7: Deploy ───────────────────────────────────────────────────
        stage('Deploy') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                }
            }
            steps {
                withCredentials([[
                    $class:            'AmazonWebServicesCredentialsBinding',
                    credentialsId:     'aws-credentials',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    script {
                        // Get instance ID from Terraform output
                        dir("${TF_DIR}") {
                            env.EC2_INSTANCE_ID = sh(
                                script: "terraform output -raw app_public_ip 2>/dev/null || echo ''",
                                returnStdout: true
                            ).trim()
                        }

                        // Use SSM Run Command to redeploy on EC2 (no SSH needed)
                        sh """
                            INSTANCE_ID=\$(aws ec2 describe-instances \
                              --region ${AWS_REGION} \
                              --filters \
                                "Name=tag:Name,Values=${PROJECT}-${ENVIRONMENT}-app-server" \
                                "Name=instance-state-name,Values=running" \
                              --query 'Reservations[0].Instances[0].InstanceId' \
                              --output text)

                            echo "Deploying to instance: \$INSTANCE_ID"

                            COMMAND_ID=\$(aws ssm send-command \
                              --region ${AWS_REGION} \
                              --instance-ids "\$INSTANCE_ID" \
                              --document-name "AWS-RunShellScript" \
                              --parameters commands=["
                                aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${env.ECR_REPO} &&
                                docker pull ${env.ECR_REPO}:${IMAGE_TAG} &&
                                docker stop food-delivery || true &&
                                docker rm food-delivery || true &&
                                docker run -d --name food-delivery --restart unless-stopped -p 8080:8080 \
                                  -e DB_USERNAME=\\\$DB_USERNAME \
                                  -e DB_PASSWORD=\\\$DB_PASSWORD \
                                  -e AWS_REGION=${AWS_REGION} \
                                  -e SNS_TOPIC_ARN=\\\$SNS_TOPIC_ARN \
                                  -e JWT_SECRET=\\\$JWT_SECRET \
                                  -e SPRING_DATASOURCE_URL=\\\$SPRING_DATASOURCE_URL \
                                  ${env.ECR_REPO}:${IMAGE_TAG}
                              "] \
                              --query 'Command.CommandId' \
                              --output text)

                            echo "SSM Command ID: \$COMMAND_ID"

                            # Wait for command to complete
                            aws ssm wait command-executed \
                              --command-id "\$COMMAND_ID" \
                              --instance-id "\$INSTANCE_ID" \
                              --region ${AWS_REGION}

                            STATUS=\$(aws ssm get-command-invocation \
                              --command-id "\$COMMAND_ID" \
                              --instance-id "\$INSTANCE_ID" \
                              --region ${AWS_REGION} \
                              --query 'Status' --output text)

                            echo "Deploy status: \$STATUS"
                            [ "\$STATUS" = "Success" ] || exit 1
                        """
                    }
                }
            }
        }

        // ── STAGE 8: Health Check ─────────────────────────────────────────────
        stage('Health Check') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                }
            }
            steps {
                script {
                    if (!env.EC2_PUBLIC_IP) {
                        dir("${TF_DIR}") {
                            env.EC2_PUBLIC_IP = sh(
                                script: "terraform output -raw app_public_ip",
                                returnStdout: true
                            ).trim()
                        }
                    }
                    // Retry health check for up to 3 minutes
                    retry(12) {
                        sleep(time: 15, unit: 'SECONDS')
                        sh "curl -sf http://${env.EC2_PUBLIC_IP}:8080/api/restaurants > /dev/null"
                    }
                    echo "✅ Application is healthy at http://${env.EC2_PUBLIC_IP}:8080"
                }
            }
        }
    }

    // ── Post Actions ──────────────────────────────────────────────────────────
    post {
        success {
            echo """
            ╔══════════════════════════════════════════╗
            ║  ✅ Pipeline SUCCESS                     ║
            ║  Branch  : ${env.BRANCH_NAME}
            ║  Commit  : ${env.GIT_COMMIT_SHORT}
            ║  App URL : http://${env.EC2_PUBLIC_IP ?: 'N/A'}:8080
            ╚══════════════════════════════════════════╝
            """
        }
        failure {
            echo "❌ Pipeline FAILED on branch ${env.BRANCH_NAME} — commit ${env.GIT_COMMIT_SHORT}"
        }
        always {
            cleanWs()
        }
    }
}
