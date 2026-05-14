pipeline {

    agent any

    tools {
        maven 'Default Maven'
        jdk   'JDK17'
    }

    environment {
        DOCKER_IMAGE = "prathamesh2019/food-delivery"
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        AWS_REGION   = "us-east-1"
        APP_PORT     = "8080"
        SNS_EMAIL    = "patilpratham16902@gmail.com"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        timeout(time: 30, unit: 'MINUTES')
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

        // ── 2. Build ──────────────────────────────────────────────────────────
        stage('Build') {
            steps {
                bat 'mvn clean package -DskipTests --batch-mode -q'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        // ── 3. Docker Build & Push ────────────────────────────────────────────
        stage('Docker Build & Push') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId:    'docker-creds',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    bat """
                        docker build -t %DOCKER_IMAGE%:%IMAGE_TAG% -t %DOCKER_IMAGE%:latest .
                        echo %DOCKER_PASS%| docker login -u %DOCKER_USER% --password-stdin
                        docker push %DOCKER_IMAGE%:%IMAGE_TAG%
                        docker push %DOCKER_IMAGE%:latest
                        docker rmi %DOCKER_IMAGE%:%IMAGE_TAG% %DOCKER_IMAGE%:latest || exit 0
                    """
                }
            }
        }

        // ── 4. SNS Setup ──────────────────────────────────────────────────────
        stage('SNS Setup') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key', variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    bat """
                        set AWS_ACCESS_KEY_ID=%AWS_ACCESS_KEY_ID%
                        set AWS_SECRET_ACCESS_KEY=%AWS_SECRET_ACCESS_KEY%
                        set AWS_DEFAULT_REGION=%AWS_REGION%

                        for /f "tokens=*" %%i in ('aws sns create-topic --name food-delivery-notifications --region %AWS_REGION% --query TopicArn --output text') do set TOPIC_ARN=%%i

                        echo SNS Topic ARN: %TOPIC_ARN%

                        aws sns subscribe --topic-arn %TOPIC_ARN% --protocol email --notification-endpoint %SNS_EMAIL% --region %AWS_REGION% || exit 0

                        echo %TOPIC_ARN% > %TEMP%\\sns_topic_arn.txt
                        echo SNS setup complete. Check email to confirm subscription.
                    """
                }
            }
        }

        // ── 5. Deploy to EC2 via SSH ──────────────────────────────────────────
        stage('Deploy to EC2') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key',  variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-key',  variable: 'AWS_SECRET_ACCESS_KEY'),
                    string(credentialsId: 'ec2-host',        variable: 'EC2_HOST'),
                    sshUserPrivateKey(
                        credentialsId:   'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable:'SSH_USER'
                    ),
                    usernamePassword(
                        credentialsId:    'docker-creds',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    script {
                        // Read SNS ARN saved in previous stage
                        def snsArn = ""
                        try {
                            snsArn = readFile("${env.TEMP}\\sns_topic_arn.txt").trim()
                        } catch (e) {
                            snsArn = ""
                        }

                        // Write deploy script to a temp file
                        def deployScript = """#!/bin/bash
set -e

# Install Docker if missing
if ! command -v docker &>/dev/null; then
    sudo dnf install -y docker
    sudo systemctl start docker
    sudo systemctl enable docker
    sudo usermod -aG docker ec2-user
fi

# Install + start MySQL if missing
if ! systemctl is-active --quiet mysqld 2>/dev/null; then
    sudo dnf install -y mysql-server
    sudo systemctl start mysqld
    sudo systemctl enable mysqld
    sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root'; FLUSH PRIVILEGES;" 2>/dev/null || true
fi

# Create database
mysql -u root -proot -e "CREATE DATABASE IF NOT EXISTS food_delivery CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null || true

# DockerHub login and pull
echo "${env.DOCKER_PASS_PLACEHOLDER}" | docker login -u "${env.DOCKER_USER_PLACEHOLDER}" --password-stdin
docker pull ${env.DOCKER_IMAGE}:latest

# Write env file
cat > /home/ec2-user/food-delivery.env << 'ENVEOF'
SPRING_PROFILES_ACTIVE=prod
DB_HOST=localhost
DB_PORT=3306
DB_NAME=food_delivery
DB_USERNAME=root
DB_PASSWORD=root
AWS_REGION=${env.AWS_REGION}
AWS_ACCESS_KEY_ID=${env.AWS_KEY_PLACEHOLDER}
AWS_SECRET_ACCESS_KEY=${env.AWS_SECRET_PLACEHOLDER}
SNS_TOPIC_ARN=${snsArn}
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
ENVEOF
chmod 600 /home/ec2-user/food-delivery.env

# Stop old container
docker stop food-delivery 2>/dev/null || true
docker rm   food-delivery 2>/dev/null || true

# Run new container
docker run -d \\
    --name food-delivery \\
    --restart unless-stopped \\
    -p 8080:8080 \\
    --network host \\
    --env-file /home/ec2-user/food-delivery.env \\
    ${env.DOCKER_IMAGE}:latest

echo "Deployment complete!"
"""
                        // Replace placeholders
                        deployScript = deployScript
                            .replace('${env.DOCKER_PASS_PLACEHOLDER}', env.DOCKER_PASS)
                            .replace('${env.DOCKER_USER_PLACEHOLDER}', env.DOCKER_USER)
                            .replace('${env.AWS_KEY_PLACEHOLDER}',     env.AWS_ACCESS_KEY_ID)
                            .replace('${env.AWS_SECRET_PLACEHOLDER}',  env.AWS_SECRET_ACCESS_KEY)

                        // Write script to temp file on Windows
                        writeFile file: 'deploy.sh', text: deployScript

                        // Copy and run on EC2 using Windows ssh/scp
                        bat """
                            scp -i "%SSH_KEY%" -o StrictHostKeyChecking=no deploy.sh %SSH_USER%@%EC2_HOST%:/tmp/deploy.sh
                            ssh -i "%SSH_KEY%" -o StrictHostKeyChecking=no %SSH_USER%@%EC2_HOST% "bash /tmp/deploy.sh"
                        """
                    }
                }
            }
        }

        // ── 6. Health Check ───────────────────────────────────────────────────
        stage('Health Check') {
            steps {
                withCredentials([string(credentialsId: 'ec2-host', variable: 'EC2_HOST')]) {
                    bat """
                        echo Waiting 30 seconds for app to start...
                        timeout /t 30 /nobreak > nul

                        set ATTEMPTS=0
                        :RETRY
                        set /a ATTEMPTS+=1
                        if %ATTEMPTS% GTR 10 (
                            echo Health check failed after 10 attempts
                            exit /b 1
                        )
                        curl -s -o nul -w "%%{http_code}" http://%EC2_HOST%:%APP_PORT%/actuator/health > %TEMP%\\health_status.txt
                        set /p STATUS=<%TEMP%\\health_status.txt
                        if "%STATUS%"=="200" (
                            echo App is UP at http://%EC2_HOST%:%APP_PORT%
                            exit /b 0
                        )
                        echo Attempt %ATTEMPTS%/10 - HTTP %STATUS% - retrying in 15s...
                        timeout /t 15 /nobreak > nul
                        goto RETRY
                    """
                }
            }
        }

        // ── 7. SNS Test ───────────────────────────────────────────────────────
        stage('SNS Test') {
            steps {
                withCredentials([string(credentialsId: 'ec2-host', variable: 'EC2_HOST')]) {
                    bat """
                        curl -s -X POST http://%EC2_HOST%:%APP_PORT%/api/sns/test -w "\\nSNS test HTTP: %%{http_code}\\n"
                    """
                }
            }
        }
    }

    post {
        success {
            echo "DEPLOYMENT SUCCESSFUL - Build #${env.BUILD_NUMBER}"
        }
        failure {
            echo "FAILED at stage: ${env.STAGE_NAME} - Build #${env.BUILD_NUMBER}"
        }
        always {
            bat 'docker logout || exit 0'
            cleanWs()
        }
    }
}
