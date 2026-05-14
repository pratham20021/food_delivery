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
                sh 'mvn clean package -DskipTests --batch-mode -q'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        // ── 3. Docker Build & Push to DockerHub ───────────────────────────────
        stage('Docker Build & Push') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'docker-creds',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh """
                        docker build -t ${DOCKER_IMAGE}:${IMAGE_TAG} -t ${DOCKER_IMAGE}:latest .
                        echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin
                        docker push ${DOCKER_IMAGE}:${IMAGE_TAG}
                        docker push ${DOCKER_IMAGE}:latest
                        docker rmi ${DOCKER_IMAGE}:${IMAGE_TAG} ${DOCKER_IMAGE}:latest || true
                    """
                }
            }
        }

        // ── 4. Create SNS Topic & Subscribe Email ─────────────────────────────
        stage('SNS Setup') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key', variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    sh """
                        export AWS_ACCESS_KEY_ID=\${AWS_ACCESS_KEY_ID}
                        export AWS_SECRET_ACCESS_KEY=\${AWS_SECRET_ACCESS_KEY}
                        export AWS_DEFAULT_REGION=${AWS_REGION}

                        TOPIC_ARN=\$(aws sns create-topic \
                            --name food-delivery-notifications \
                            --region ${AWS_REGION} \
                            --query 'TopicArn' --output text)

                        echo "SNS Topic ARN: \$TOPIC_ARN"

                        aws sns subscribe \
                            --topic-arn \$TOPIC_ARN \
                            --protocol email \
                            --notification-endpoint patilpratham16902@gmail.com \
                            --region ${AWS_REGION} || true

                        echo \$TOPIC_ARN > /tmp/sns_topic_arn.txt
                        echo "SNS setup done. Confirm subscription email before orders trigger notifications."
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
                    sh '''
                        SNS_TOPIC_ARN=$(cat /tmp/sns_topic_arn.txt 2>/dev/null || echo "")

                        cat > /tmp/deploy.sh << DEPLOY
#!/bin/bash
set -e

# Install Docker if missing
if ! command -v docker &>/dev/null; then
    sudo dnf install -y docker
    sudo systemctl start docker
    sudo systemctl enable docker
    sudo usermod -aG docker ec2-user
fi

# Install MySQL if missing
if ! systemctl is-active --quiet mysqld 2>/dev/null; then
    sudo dnf install -y mysql-server
    sudo systemctl start mysqld
    sudo systemctl enable mysqld
    mysql -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root'; FLUSH PRIVILEGES;" 2>/dev/null || true
fi

# Create database
mysql -u root -proot -e "CREATE DATABASE IF NOT EXISTS food_delivery CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null || true

# DockerHub login and pull
echo "''' + '${DOCKER_PASS}' + '''" | docker login -u "''' + '${DOCKER_USER}' + '''" --password-stdin
docker pull ''' + '${DOCKER_IMAGE}' + ''':latest

# Write env file
cat > /home/ec2-user/food-delivery.env << ENVEOF
SPRING_PROFILES_ACTIVE=prod
DB_HOST=localhost
DB_PORT=3306
DB_NAME=food_delivery
DB_USERNAME=root
DB_PASSWORD=root
AWS_REGION=''' + '${AWS_REGION}' + '''
AWS_ACCESS_KEY_ID=''' + '${AWS_ACCESS_KEY_ID}' + '''
AWS_SECRET_ACCESS_KEY=''' + '${AWS_SECRET_ACCESS_KEY}' + '''
SNS_TOPIC_ARN=${SNS_TOPIC_ARN}
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
    ''' + '${DOCKER_IMAGE}' + ''':latest

echo "Deploy complete"
DEPLOY

                        chmod +x /tmp/deploy.sh

                        scp -i ${SSH_KEY} -o StrictHostKeyChecking=no \
                            /tmp/deploy.sh ${SSH_USER}@${EC2_HOST}:/tmp/deploy.sh

                        ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no \
                            ${SSH_USER}@${EC2_HOST} "bash /tmp/deploy.sh"
                    '''
                }
            }
        }

        // ── 6. Health Check ───────────────────────────────────────────────────
        stage('Health Check') {
            steps {
                withCredentials([string(credentialsId: 'ec2-host', variable: 'EC2_HOST')]) {
                    sh """
                        echo "Waiting 30s for app to start..."
                        sleep 30
                        for i in \$(seq 1 10); do
                            STATUS=\$(curl -s -o /dev/null -w "%{http_code}" \
                                http://\${EC2_HOST}:${APP_PORT}/actuator/health 2>/dev/null || echo "000")
                            if [ "\$STATUS" = "200" ]; then
                                echo "App is UP at http://\${EC2_HOST}:${APP_PORT}"
                                exit 0
                            fi
                            echo "Attempt \$i/10 — HTTP \$STATUS — retrying in 15s..."
                            sleep 15
                        done
                        echo "Health check failed"
                        exit 1
                    """
                }
            }
        }

        // ── 7. SNS Test Notification ──────────────────────────────────────────
        stage('SNS Test') {
            steps {
                withCredentials([string(credentialsId: 'ec2-host', variable: 'EC2_HOST')]) {
                    sh """
                        curl -s -X POST \
                            "http://\${EC2_HOST}:${APP_PORT}/api/sns/test" \
                            -w "\\nSNS test HTTP status: %{http_code}\\n"
                    """
                }
            }
        }
    }

    post {
        success {
            withCredentials([string(credentialsId: 'ec2-host', variable: 'EC2_HOST')]) {
                echo """
                ========================================
                DEPLOYMENT SUCCESSFUL
                App      : http://${EC2_HOST}:${APP_PORT}
                Health   : http://${EC2_HOST}:${APP_PORT}/actuator/health
                SNS      : http://${EC2_HOST}:${APP_PORT}/api/sns/status
                Build    : #${env.BUILD_NUMBER}
                ========================================
                """
            }
        }
        failure {
            echo "FAILED at stage: ${env.STAGE_NAME} — Build #${env.BUILD_NUMBER}"
        }
        always {
            sh 'docker logout || true'
            cleanWs()
        }
    }
}
