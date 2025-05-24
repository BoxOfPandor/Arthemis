/*
 * ========================================================================
 * Plazza_CI.groovy
 * ========================================================================
 * Purpose:      Jenkins pipeline script for complete CI process of the Plazza project
 * Description:  This script performs a comprehensive CI workflow including:
 *               code style checking, static analysis, compilation, functional testing,
 *               memory leak detection, and documentation validation
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
        CONTAINER_NAME = 'plazza-ci'
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

        stage('Install Tools') {
            steps {
                sh '''
                    docker exec ${CONTAINER_NAME} bash -c "apt-get update && apt-get install -y clang-format cppcheck valgrind"
                '''
            }
        }

        stage('Check Coding Style') {
            steps {
                script {
                    try {
                        sh '''
                            docker exec ${CONTAINER_NAME} bash -c "find ./src ./include -name '*.cpp' -o -name '*.hpp' | xargs clang-format -style=gnu -n -Werror || true"
                        '''
                        echo "Note: Des problèmes de style de code peuvent être présents mais n'empêchent pas la suite du pipeline"
                    } catch (Exception e) {
                        echo "Problèmes de style de code détectés, mais on continue le pipeline"
                    }
                }
            }
        }

        stage('Static Analysis') {
            steps {
                script {
                    try {
                        sh '''
                            docker exec ${CONTAINER_NAME} bash -c "cppcheck --enable=warning,style,performance --suppress=missingInclude ./src ./include || true"
                        '''
                        echo "Note: Des problèmes d'analyse statique peuvent être présents mais n'empêchent pas la suite du pipeline"
                    } catch (Exception e) {
                        echo "Problèmes d'analyse statique détectés, mais on continue le pipeline"
                    }
                }
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

        stage('Run Functional Tests') {
            steps {
                script {
                    // Création d'un script de test simple pour tester les fonctionnalités de base
                    sh """
                        cat > ${WORKDIR}/test_plazza.sh << 'EOL'
#!/bin/bash
echo "Testing basic functionality..."
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
                script {
                    try {
                        sh '''
                            docker exec ${CONTAINER_NAME} bash -c "echo 'Regina 1\\nexit' | valgrind --leak-check=full --show-leak-kinds=all --error-exitcode=1 ./plazza 2 || true"
                        '''
                        echo "Note: Des fuites mémoire peuvent être présentes mais n'empêchent pas la suite du pipeline"
                    } catch (Exception e) {
                        echo "Fuites mémoire détectées, mais on continue le pipeline"
                    }
                }
            }
        }

        stage('Documentation Check') {
            steps {
                script {
                    def hasComments = sh(
                        script: """
                            docker exec ${CONTAINER_NAME} bash -c 'grep -r "/\\*\\*" --include="*.hpp" ./include || true'
                        """,
                        returnStdout: true
                    ).trim()

                    if (hasComments.isEmpty()) {
                        echo "⚠️ Peu ou pas de commentaires de documentation détectés dans les headers"
                    } else {
                        echo "✅ Commentaires de documentation trouvés dans les headers"
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
            echo "✅ Le pipeline CI complet pour le projet Plazza a réussi"
        }
        failure {
            echo "❌ Le pipeline CI pour le projet Plazza a échoué"
        }
    }
}
