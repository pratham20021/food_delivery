pipeline {

    agent any

    environment {
        AWS_REGION      = "us-east-1"
        PROJECT         = "food-delivery"
        ENVIRONMENT     = "dev"
        APP_PORT        = "8080"
        TF_DIR          = "terraform"
        TF_IN_AUTOMATION = "true"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        // ── 1. Checkout ───────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                git credentialsId: 'github-credentials',
                    url: 'https://github.com/pratham20021/food_delivery.git',
                    branch: 'main'
            }
        }

        // ── 2. Build JAR ──────────────────────────────────────────────────────
        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests --batch-mode -q'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        // ── 3. Install Lambda Layer Dependencies ──────────────────────────────
        stage('Lambda Layers') {
            steps {
                sh '''
                    pip3 install pymysql -t lambda/layers/db_utils/python/ --quiet
                    pip3 install boto3   -t lambda/layers/aws_clients/python/ --quiet
                    echo "Lambda layers ready"
                '''
            }
        }

        // ── 4. Terraform Init + ECR ───────────────────────────────────────────
        stage('Terraform ECR') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY'),
                    string(credentialsId: 'tf-db-password',        variable: 'TF_VAR_db_password'),
                    string(credentialsId: 'tf-notification-email', variable: 'TF_VAR_notification_email'),
                    string(credentialsId: 'tf-jwt-secret',         variable: 'TF_VAR_jwt_secret')
                ]) {
                    sh '''
                        cd ${TF_DIR}
                        terraform init -input=false
                        terraform apply -target=module.ecr \
                            -auto-approve \
                            -input=false \
                            -var="aws_region=${AWS_REGION}" \
                            -var="environment=${ENVIRONMENT}"
                    '''
                }
            }
        }

        // ── 5. Docker Build & Push to ECR ─────────────────────────────────────
        stage('Docker Build & Push to ECR') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    script {
                        def accountId = sh(
                            script: 'aws sts get-caller-identity --query Account --output text',
                            returnStdout: true
                        ).trim()

                        env.ECR_URL = "${accountId}.dkr.ecr.${AWS_REGION}.amazonaws.com/${PROJECT}-${ENVIRONMENT}"
                        env.IMAGE_TAG = env.BUILD_NUMBER

                        sh """
                            aws ecr get-login-password --region ${AWS_REGION} | \
                                docker login --username AWS --password-stdin ${env.ECR_URL}

                            docker build -t ${env.ECR_URL}:${env.IMAGE_TAG} \
                                         -t ${env.ECR_URL}:latest .

                            docker push ${env.ECR_URL}:${env.IMAGE_TAG}
                            docker push ${env.ECR_URL}:latest

                            docker rmi ${env.ECR_URL}:${env.IMAGE_TAG} \
                                       ${env.ECR_URL}:latest || true
                        """
                    }
                }
            }
        }

        // ── 6. Terraform Full Apply ───────────────────────────────────────────
        stage('Terraform Apply') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY'),
                    string(credentialsId: 'tf-db-password',        variable: 'TF_VAR_db_password'),
                    string(credentialsId: 'tf-notification-email', variable: 'TF_VAR_notification_email'),
                    string(credentialsId: 'tf-jwt-secret',         variable: 'TF_VAR_jwt_secret')
                ]) {
                    sh '''
                        cd ${TF_DIR}
                        terraform apply \
                            -auto-approve \
                            -input=false \
                            -var="aws_region=${AWS_REGION}" \
                            -var="environment=${ENVIRONMENT}"
                    '''
                }
            }
        }

        // ── 7. Capture Outputs ────────────────────────────────────────────────
        stage('Capture Outputs') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    script {
                        env.APP_IP = sh(
                            script: "cd ${TF_DIR} && terraform output -raw app_public_ip",
                            returnStdout: true
                        ).trim()

                        env.APP_URL = "http://${env.APP_IP}:${APP_PORT}"
                        echo "App deployed at: ${env.APP_URL}"
                    }
                }
            }
        }

        // ── 8. Health Check ───────────────────────────────────────────────────
        stage('Health Check') {
            steps {
                script {
                    echo "Waiting 90s for EC2 bootstrap to complete..."
                    sleep(90)

                    def healthy = false
                    for (int i = 1; i <= 10; i++) {
                        def status = sh(
                            script: "curl -s -o /dev/null -w '%{http_code}' ${env.APP_URL}/actuator/health || echo 000",
                            returnStdout: true
                        ).trim()

                        if (status == '200') {
                            echo "App is UP at ${env.APP_URL}"
                            healthy = true
                            break
                        }
                        echo "Attempt ${i}/10 — HTTP ${status} — retrying in 20s..."
                        sleep(20)
                    }

                    if (!healthy) {
                        error("Health check failed after 10 attempts — check EC2 user_data logs")
                    }
                }
            }
        }

        // ── 9. Smoke Test ─────────────────────────────────────────────────────
        stage('Smoke Test') {
            steps {
                sh """
                    echo "--- Register user ---"
                    curl -s -X POST ${env.APP_URL}/api/auth/register \
                        -H 'Content-Type: application/json' \
                        -d '{"name":"CI Test","email":"ci@test.com","password":"password123"}' \
                        | python3 -m json.tool || true

                    echo "--- Get restaurants ---"
                    TOKEN=\$(curl -s -X POST ${env.APP_URL}/api/auth/login \
                        -H 'Content-Type: application/json' \
                        -d '{"email":"ci@test.com","password":"password123"}' \
                        | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")

                    curl -s -H "Authorization: Bearer \$TOKEN" \
                        ${env.APP_URL}/api/restaurants | python3 -m json.tool || true
                """
            }
        }
    }

    post {
        success {
            echo """
            ╔══════════════════════════════════════════╗
            ║   DEPLOYMENT SUCCESSFUL                  ║
            ║   Build  : #${env.BUILD_NUMBER}
            ║   App URL: ${env.APP_URL ?: 'see Capture Outputs stage'}
            ╚══════════════════════════════════════════╝
            """
        }
        failure {
            echo "FAILED — Build #${env.BUILD_NUMBER} — check logs above"
        }
        always {
            sh 'docker logout || true'
            cleanWs()
        }
    }
}
