pipeline {
    agent any

    parameters {
        string(name: 'REPO_PATH', defaultValue: 'User/Repo', description: 'Chemin GitHub sous forme User/Repo')
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche à tester')
    }

    environment {
        SSH_CREDENTIALS_ID = 'github-ssh-key'
        DOCKER_IMAGE = 'ghcr.io/zowks/epitech-devcontainer:latest'
        CONTAINER_NAME = 'plazza-test'
        WORKDIR = "${env.WORKSPACE}/repo"
    }

    stages {
        stage('Checkout') {
            steps {
                sshagent (credentials: [env.SSH_CREDENTIALS_ID]) {
                    sh '''
                        git clone --branch ${BRANCH} git@github.com:${REPO_PATH}.git repo
                    '''
                }
            }
        }

        stage('Add Permissions') {
            steps {
                sh 'sudo chown -R 1000:1000 "${WORKDIR}"'
            }
        }

        stage('Start Docker') {
            steps {
                sh '''
                    docker run -dit --privileged \
                      --ulimit nofile=262144:262144 \
                      -v "${WORKDIR}:/workspace" \
                      -w /workspace \
                      --name ${CONTAINER_NAME} \
                      ${DOCKER_IMAGE} bash
                '''
            }
        }

        stage('Build Project') {
            steps {
                sh '''
                    docker exec ${CONTAINER_NAME} make
                '''
            }
        }

        stage('Run Functional Tests') {
            steps {
                script {
                    // Création d'un script de test simple pour tester les fonctionnalités de base
                    sh """
                        cat > ${WORKDIR}/test_plazza.sh << 'EOL'
#!/bin/bash
echo "Regina 1" | ./plazza 2
echo "Margarita 2" | ./plazza 3
echo "Americana 3" | ./plazza 1
echo "Fantasia 1" | ./plazza 2
echo "status" | ./plazza 2
echo "exit" | ./plazza 2
EOL
                    """

                    sh """
                        chmod +x ${WORKDIR}/test_plazza.sh
                    """

                    sh '''
                        docker exec ${CONTAINER_NAME} bash -c "chmod +x ./test_plazza.sh && ./test_plazza.sh"
                    '''
                }
            }
        }

        stage('Check Memory Leaks') {
            steps {
                sh '''
                    docker exec ${CONTAINER_NAME} bash -c "apt-get update && apt-get install -y valgrind"
                    docker exec ${CONTAINER_NAME} bash -c "echo 'Regina 1\\nexit' | valgrind --leak-check=full --show-leak-kinds=all ./plazza 2"
                '''
            }
        }

        stage('Clean Project') {
            steps {
                sh '''
                    docker exec ${CONTAINER_NAME} make fclean
                '''
            }
        }

        stage('Stop Docker') {
            steps {
                sh '''
                    docker stop ${CONTAINER_NAME}
                    docker rm ${CONTAINER_NAME}
                '''
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo "✅ Les tests du projet Plazza ont réussi"
        }
        failure {
            echo "❌ Les tests du projet Plazza ont échoué"
        }
    }
}
