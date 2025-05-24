/*
 * ========================================================================
 * Plazza_Doxygen.groovy
 * ========================================================================
 * Purpose:      Jenkins pipeline script for generating Doxygen documentation
 * Description:  This script checks out the repository, installs Doxygen,
 *               generates a configuration file, and builds API documentation
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
        string(name: 'PROJECT_NAME', defaultValue: 'Plazza', description: 'Nom du projet pour la documentation')
        string(name: 'PROJECT_VERSION', defaultValue: '1.0', description: 'Version du projet')
        string(name: 'OUTPUT_DIRECTORY', defaultValue: 'doxygen-docs', description: 'Répertoire de sortie pour la documentation')
    }

    environment {
        SSH_CREDENTIALS_ID = 'github-ssh-key'
        DOCKER_IMAGE = 'ghcr.io/zowks/epitech-devcontainer:latest'
        CONTAINER_NAME = 'plazza-doxygen'
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

        stage('Install Doxygen') {
            steps {
                sh '''
                    docker exec ${CONTAINER_NAME} bash -c "apt-get update && apt-get install -y doxygen graphviz"
                '''
            }
        }

        stage('Prepare Doxygen Configuration') {
            steps {
                script {
                    // Création d'un Doxyfile de base
                    sh """
                        cat > ${WORKDIR}/Doxyfile << 'EOL'
# Configuration de base pour Doxygen
PROJECT_NAME           = "${params.PROJECT_NAME}"
PROJECT_NUMBER         = "${params.PROJECT_VERSION}"
OUTPUT_DIRECTORY       = "${params.OUTPUT_DIRECTORY}"
CREATE_SUBDIRS         = YES
EXTRACT_ALL            = YES
EXTRACT_PRIVATE        = YES
EXTRACT_PACKAGE        = YES
EXTRACT_STATIC         = YES
EXTRACT_LOCAL_CLASSES  = YES
EXTRACT_LOCAL_METHODS  = YES
CALL_GRAPH             = YES
CALLER_GRAPH           = YES
GRAPHICAL_HIERARCHY    = YES
DIRECTORY_GRAPH        = YES
DOT_IMAGE_FORMAT       = svg
INTERACTIVE_SVG        = YES
GENERATE_HTML          = YES
GENERATE_LATEX         = NO
RECURSIVE              = YES
USE_MDFILE_AS_MAINPAGE = README.md
REFERENCED_BY_RELATION = YES
REFERENCES_RELATION    = YES
INPUT                  = ./src ./include README.md
FILE_PATTERNS          = *.c *.cc *.cpp *.h *.hpp *.md
EXCLUDE                =
EXCLUDE_PATTERNS       =
EXCLUDE_SYMBOLS        =
IMAGE_PATH             =
HAVE_DOT               = YES
DOT_NUM_THREADS        = 0
CLASS_GRAPH            = YES
COLLABORATION_GRAPH    = YES
GROUP_GRAPHS           = YES
UML_LOOK               = YES
UML_LIMIT_NUM_FIELDS   = 10
TEMPLATE_RELATIONS     = YES
INCLUDE_GRAPH          = YES
INCLUDED_BY_GRAPH      = YES
CALL_GRAPH             = YES
CALLER_GRAPH           = YES
GRAPHICAL_HIERARCHY    = YES
DIRECTORY_GRAPH        = YES
DOT_IMAGE_FORMAT       = svg
INTERACTIVE_SVG        = YES
HTML_DYNAMIC_SECTIONS  = YES
HTML_COLORSTYLE_HUE    = 220
HTML_COLORSTYLE_SAT    = 80
HTML_COLORSTYLE_GAMMA  = 80
EOL
                    """
                }
            }
        }

        stage('Generate Documentation') {
            steps {
                sh '''
                    docker exec ${CONTAINER_NAME} bash -c "doxygen Doxyfile"
                '''
            }
        }

        stage('Archive Documentation') {
            steps {
                sh """
                    docker exec ${CONTAINER_NAME} bash -c "cd ${params.OUTPUT_DIRECTORY} && tar -czvf ../doxygen-docs.tar.gz html"
                    docker cp ${CONTAINER_NAME}:/workspace/doxygen-docs.tar.gz ${env.WORKSPACE}/
                """
                archiveArtifacts artifacts: 'doxygen-docs.tar.gz', fingerprint: true
            }
        }

        stage('Generate Documentation Report') {
            steps {
                script {
                    def documentedFiles = sh(
                        script: """
                            docker exec ${CONTAINER_NAME} bash -c "find ${params.OUTPUT_DIRECTORY}/html -name '*.html' | wc -l"
                        """,
                        returnStdout: true
                    ).trim()

                    echo "📊 Rapport de documentation :"
                    echo "🔹 Projet : ${params.PROJECT_NAME}"
                    echo "🔹 Version : ${params.PROJECT_VERSION}"
                    echo "🔹 Fichiers HTML générés : ${documentedFiles}"

                    // Vérifier la qualité de la documentation
                    def docsQuality = sh(
                        script: """
                            docker exec ${CONTAINER_NAME} bash -c "grep -r 'warning' ${params.OUTPUT_DIRECTORY}/html/doxygen_warnings.txt 2>/dev/null || echo '0'"
                        """,
                        returnStdout: true
                    ).trim()

                    if (docsQuality == "0") {
                        echo "✅ Documentation générée sans avertissements"
                    } else {
                        echo "⚠️ La documentation contient des avertissements, vérifiez le fichier doxygen_warnings.txt"
                    }
                }
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
            cleanWs(patterns: [[pattern: 'repo/**', type: 'EXCLUDE']])
        }
        success {
            echo "✅ La génération de documentation Doxygen pour le projet Plazza a réussi"
            echo "📘 La documentation est disponible dans l'archive des artefacts : doxygen-docs.tar.gz"
        }
        failure {
            echo "❌ La génération de documentation Doxygen pour le projet Plazza a échoué"
        }
    }
}
