/*
 * ========================================================================
 * Plazza_CodingStyle.groovy
 * ========================================================================
 * Purpose:      Jenkins pipeline script for checking coding style in the Plazza project
 * Description:  This script checks out the repository, runs style checkers
 *               (clang-format) and static analysis (cppcheck) on the codebase
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
        CONTAINER_NAME = 'plazza-style'
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

        stage('Install Coding Style Tools') {
            steps {
                sh '''
                    docker exec ${CONTAINER_NAME} bash -c "apt-get update && apt-get install -y clang-format cppcheck"
                '''
            }
        }

        stage('Check Coding Style') {
            steps {
                script {
                    sh '''
                        docker exec ${CONTAINER_NAME} bash -c "find ./src ./include -name '*.cpp' -o -name '*.hpp' | xargs clang-format -style=gnu -n -Werror"
                    '''
                }
            }
        }

        stage('Static Analysis') {
            steps {
                sh '''
                    docker exec ${CONTAINER_NAME} bash -c "cppcheck --enable=all --suppress=missingInclude --error-exitcode=1 ./src ./include"
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
            echo "✅ La vérification du style de code pour le projet Plazza a réussi"
        }
        failure {
            echo "❌ La vérification du style de code pour le projet Plazza a échoué"
        }
    }
}
