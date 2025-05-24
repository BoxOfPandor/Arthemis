/*
 * ========================================================================
 * Plazza_Compile.groovy
 * ========================================================================
 * Purpose:      Jenkins pipeline script for compiling the Plazza project
 * Description:  This script checks out the repository, builds the project in a
 *               Docker container, verifies the binary exists, and cleans up
 * Author:       Heathcliff - Arthemis Team
 * Created:      May 24, 2025
 * Updated:      May 24, 2025
 * Version:      1.0
 * Repository:   Arthemis/Pipeline/Plazza
 * ========================================================================
 */

pipeline {
    agent any

    parameters {
        string(name: 'REPO_PATH', defaultValue: 'User/Repo', description: 'Chemin GitHub sous forme User/Repo')
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche à tester')
    }

    environment {
        SSH_CREDENTIALS_ID = 'github-ssh-key'
        DOCKER_IMAGE = 'ghcr.io/zowks/epitech-devcontainer:latest'
        CONTAINER_NAME = 'plazza-build'
        WORKDIR = "${env.WORKSPACE}/repo"
        EXPECTED_BINARY = 'plazza'
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
                script {
                    def binaryExists = sh(
                        script: """
                            docker exec ${CONTAINER_NAME} bash -c 'if [ -f "${EXPECTED_BINARY}" ]; then echo "true"; else echo "false"; fi'
                        """,
                        returnStdout: true
                    ).trim()

                    if (binaryExists == "false") {
                        echo "❌ Binaire ${EXPECTED_BINARY} introuvable dans le conteneur"
                        error "Échec: Le binaire ${EXPECTED_BINARY} n'existe pas après la compilation"
                    } else {
                        echo "✅ Binaire ${EXPECTED_BINARY} trouvé avec succès"
                    }
                }
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
            echo "✅ La compilation du projet Plazza a réussi"
        }
        failure {
            echo "❌ La compilation du projet Plazza a échoué"
        }
    }
}
