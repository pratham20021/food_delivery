pipeline {

    agent any

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-21'
    }

    environment {
        AWS_REGION   = 'us-east-1'
        PROJECT      = 'food-delivery'
        ENVIRONMENT  = "${env.BRANCH_NAME == 'main' ? 'prod' : 'dev'}"
        IMAGE_TAG    = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
        TF_DIR       = 'terraform'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG        = env.GIT_COMMIT_SHORT
                }
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn clean package -DskipTests=false --batch-mode'
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'target/*.jar',
                                     fingerprint: true,
                                     allowEmptyArchive: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                withCredentials([[
                    $class:            'AmazonWebServicesCredentialsBinding',
                    credentialsId:     'aws-credentials',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    script {
                        env.AWS_ACCOUNT_ID = sh(
                            script: "aws sts get-caller-identity --query Account --output text --region ${AWS_REGION}",
                            returnStdout: true
                        ).trim()
                        env.ECR_REPO = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${PROJECT}-${ENVIRONMENT}"

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
                    """
                }
            }
            post {
                always {
                    sh "docker rmi ${env.ECR_REPO}:${IMAGE_TAG} ${env.ECR_REPO}:latest || true"
                }
            }
        }

        stage('Terraform Plan') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-credentials',
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
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
                              -out=tfplan -input=false
                        """
                    }
                }
            }
        }

        stage('Terraform Apply') {
            when { branch 'main' }
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-credentials',
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                    string(credentialsId: 'db-password',        variable: 'TF_VAR_db_password'),
                    string(credentialsId: 'jwt-secret',         variable: 'TF_VAR_jwt_secret'),
                    string(credentialsId: 'notification-email', variable: 'TF_VAR_notification_email')
                ]) {
                    dir("${TF_DIR}") {
                        sh 'terraform apply -input=false -auto-approve tfplan'
                        script {
                            env.EC2_PUBLIC_IP = sh(script: 'terraform output -raw app_public_ip', returnStdout: true).trim()
                            env.SNS_TOPIC_ARN = sh(script: 'terraform output -raw sns_topic_arn', returnStdout: true).trim()
                            env.RDS_ENDPOINT  = sh(script: 'terraform output -raw rds_endpoint',  returnStdout: true).trim()
                        }
                    }
                }
            }
        }

        stage('Deploy to EC2') {
            when { branch 'main' }
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-credentials',
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                    string(credentialsId: 'db-password',    variable: 'DB_PASSWORD'),
                    string(credentialsId: 'jwt-secret',     variable: 'JWT_SECRET'),
                    string(credentialsId: 'sns-topic-arn',  variable: 'SNS_TOPIC_ARN_SECRET')
                ]) {
                    script {
                        def instanceId = sh(
                            script: """
                                aws ec2 describe-instances \
                                  --region ${AWS_REGION} \
                                  --filters \
                                    "Name=tag:Name,Values=${PROJECT}-${ENVIRONMENT}-app-server" \
                                    "Name=instance-state-name,Values=running" \
                                  --query 'Reservations[0].Instances[0].InstanceId' \
                                  --output text
                            """,
                            returnStdout: true
                        ).trim()

                        // Write env file on EC2 then pull + restart container
                        def deployScript = """
                            cat > /etc/food-delivery.env << 'ENVEOF'
SPRING_PROFILES_ACTIVE=prod
DB_HOST=${env.RDS_ENDPOINT?.split(':')[0] ?: ''}
DB_NAME=food_delivery
DB_USERNAME=admin
DB_PASSWORD=${DB_PASSWORD}
AWS_REGION=${AWS_REGION}
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
SNS_TOPIC_ARN=${SNS_TOPIC_ARN_SECRET}
JWT_SECRET=${JWT_SECRET}
ENVEOF
                            chmod 600 /etc/food-delivery.env
                            aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${env.ECR_REPO}
                            docker pull ${env.ECR_REPO}:${IMAGE_TAG}
                            docker stop food-delivery 2>/dev/null || true
                            docker rm   food-delivery 2>/dev/null || true
                            docker run -d --name food-delivery --restart unless-stopped \
                              -p 8080:8080 \
                              --env-file /etc/food-delivery.env \
                              ${env.ECR_REPO}:${IMAGE_TAG}
                        """

                        def cmdId = sh(
                            script: """
                                aws ssm send-command \
                                  --region ${AWS_REGION} \
                                  --instance-ids ${instanceId} \
                                  --document-name "AWS-RunShellScript" \
                                  --parameters 'commands=["${deployScript.replaceAll('"', '\\"')}"]' \
                                  --query 'Command.CommandId' --output text
                            """,
                            returnStdout: true
                        ).trim()

                        sh """
                            aws ssm wait command-executed \
                              --command-id ${cmdId} \
                              --instance-id ${instanceId} \
                              --region ${AWS_REGION}
                        """
                    }
                }
            }
        }

        stage('SNS Setup') {
            when { branch 'main' }
            steps {
                withCredentials([string(credentialsId: 'notification-email', variable: 'NOTIFY_EMAIL')]) {
                    script {
                        // Trigger SNS topic creation + email subscription via the app's own API
                        retry(5) {
                            sleep(time: 15, unit: 'SECONDS')
                            sh """
                                curl -sf -X POST \
                                  "http://${env.EC2_PUBLIC_IP}:8080/api/sns/setup?email=${NOTIFY_EMAIL}" \
                                  -o /dev/null
                            """
                        }
                        echo "SNS setup triggered — check ${NOTIFY_EMAIL} to confirm subscription"
                    }
                }
            }
        }

        stage('Health Check') {
            when { branch 'main' }
            steps {
                retry(12) {
                    sleep(time: 15, unit: 'SECONDS')
                    sh "curl -sf http://${env.EC2_PUBLIC_IP}:8080/actuator/health | grep -q UP"
                }
                echo "App live at http://${env.EC2_PUBLIC_IP}:8080"
            }
        }
    }

    post {
        success {
            echo "Pipeline SUCCESS — http://${env.EC2_PUBLIC_IP ?: 'localhost'}:8080"
        }
        failure {
            echo "Pipeline FAILED on branch ${env.BRANCH_NAME} commit ${env.GIT_COMMIT_SHORT}"
        }
        always {
            cleanWs()
        }
    }
}
