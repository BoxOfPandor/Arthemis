pipeline {
    agent any

    parameters {
        string(name: 'REPO_PATH', defaultValue: 'User/Repo', description: 'Chemin GitHub sous forme User/Repo')
        string(name: 'EXPECTED_BINARY', defaultValue: 'my_binary', description: 'Nom du binaire attendu après build')
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche à tester')
    }

    environment {
        SSH_CREDENTIALS_ID = 'github-ssh-key'
        DOCKER_IMAGE = 'ghcr.io/zowks/epitech-devcontainer:latest'
        CONTAINER_NAME = 'epitech-build'
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
                    docker exec ${CONTAINER_NAME} make re
                '''
            }
        }

        stage('Check Binary') {
            steps {
                sh '''
                    docker exec ${CONTAINER_NAME} bash -c 'if [ ! -f ${EXPECTED_BINARY} ]; then echo "❌ Binaire ${EXPECTED_BINARY} introuvable"; exit 1; fi'
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
    }
}
