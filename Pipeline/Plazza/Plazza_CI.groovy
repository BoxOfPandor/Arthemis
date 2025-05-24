/*
 * ========================================================================
 * Plazza_CI.groovy
 * ========================================================================
 * Purpose:      Jenkins pipeline orchestrator for the Plazza project
 * Description:  This script coordinates the execution of other Plazza pipeline scripts
 *               based on boolean parameters, allowing flexible CI workflows
 * Author:       Heathcliff - Arthemis Team
 * Created:      May 24, 2025
 * Updated:      May 24, 2025
 * Version:      1.1
 * Repository:   Arthemis/Pipeline/Plazza
 * ========================================================================
 */

pipeline {
    agent any

    parameters {
        string(name: 'REPO_PATH', defaultValue: 'User/Repo', description: 'Chemin GitHub sous forme User/Repo')
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche à tester')
        booleanParam(name: 'RUN_COMPILE', defaultValue: true, description: 'Exécuter le pipeline de compilation')
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Exécuter le pipeline de tests')
        booleanParam(name: 'RUN_CODING_STYLE', defaultValue: true, description: 'Exécuter le pipeline de vérification du style de code')
        booleanParam(name: 'RUN_DOCUMENTATION_CHECK', defaultValue: true, description: 'Vérifier la présence de documentation')
        booleanParam(name: 'GENERATE_DOXYGEN', defaultValue: false, description: 'Générer la documentation Doxygen')
    }

    environment {
        // Variables globales communes à tous les pipelines
        GLOBAL_REPO_PATH = "${params.REPO_PATH}"
        GLOBAL_BRANCH = "${params.BRANCH}"
    }

    stages {
        stage('Compilation') {
            when {
                expression { return params.RUN_COMPILE }
            }
            steps {
                echo "🚀 Démarrage du pipeline de compilation"
                build job: 'Plazza_Compile', parameters: [
                    string(name: 'REPO_PATH', value: "${params.REPO_PATH}"),
                    string(name: 'BRANCH', value: "${params.BRANCH}")
                ]
                echo "✅ Pipeline de compilation terminé avec succès"
            }
        }

        stage('Tests') {
            when {
                expression { return params.RUN_TESTS }
            }
            steps {
                echo "🚀 Démarrage du pipeline de tests"
                build job: 'Plazza_Tests', parameters: [
                    string(name: 'REPO_PATH', value: "${params.REPO_PATH}"),
                    string(name: 'BRANCH', value: "${params.BRANCH}")
                ]
                echo "✅ Pipeline de tests terminé avec succès"
            }
        }

        stage('Vérification du style de code') {
            when {
                expression { return params.RUN_CODING_STYLE }
            }
            steps {
                echo "🚀 Démarrage du pipeline de vérification du style de code"
                build job: 'Plazza_CodingStyle', parameters: [
                    string(name: 'REPO_PATH', value: "${params.REPO_PATH}"),
                    string(name: 'BRANCH', value: "${params.BRANCH}")
                ]
                echo "✅ Pipeline de vérification du style de code terminé avec succès"
            }
        }

        stage('Vérification de la documentation') {
            when {
                expression { return params.RUN_DOCUMENTATION_CHECK }
            }
            steps {
                echo "🔍 Vérification de la documentation dans le code source"

                // Création d'un workspace temporaire pour la vérification
                sh "mkdir -p ${env.WORKSPACE}/doc_check"
                dir("${env.WORKSPACE}/doc_check") {
                    // Clone du repo
                    sshagent(['github-ssh-key']) {
                        sh """
                            git clone --branch ${params.BRANCH} git@github.com:${params.REPO_PATH}.git repo
                        """
                    }

                    // Vérification de la présence de commentaires de documentation
                    script {
                        def hasComments = sh(
                            script: """
                                cd repo && grep -r "/\\*\\*" --include="*.hpp" ./include || true
                            """,
                            returnStdout: true
                        ).trim()

                        if (hasComments.isEmpty()) {
                            echo "⚠️ Peu ou pas de commentaires de documentation détectés dans les headers"
                        } else {
                            echo "✅ Commentaires de documentation trouvés dans les headers"
                        }
                    }

                    // Nettoyage
                    deleteDir()
                }
            }
        }

        stage('Génération de documentation Doxygen') {
            when {
                expression { return params.GENERATE_DOXYGEN }
            }
            steps {
                echo "📚 Démarrage du pipeline de génération de documentation Doxygen"
                build job: 'Plazza_Doxygen', parameters: [
                    string(name: 'REPO_PATH', value: "${params.REPO_PATH}"),
                    string(name: 'BRANCH', value: "${params.BRANCH}"),
                    string(name: 'PROJECT_NAME', value: "Plazza"),
                    string(name: 'PROJECT_VERSION', value: "1.0"),
                    string(name: 'OUTPUT_DIRECTORY', value: "doxygen-docs")
                ]
                echo "✅ Pipeline de génération de documentation Doxygen terminé avec succès"
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo "✅ Le pipeline CI orchestrateur pour le projet Plazza a réussi"
            echo "🔍 Récapitulatif des pipelines exécutés :"
            if (params.RUN_COMPILE) { echo "  ✓ Compilation" }
            if (params.RUN_TESTS) { echo "  ✓ Tests" }
            if (params.RUN_CODING_STYLE) { echo "  ✓ Vérification du style de code" }
            if (params.RUN_DOCUMENTATION_CHECK) { echo "  ✓ Vérification de la documentation" }
            if (params.GENERATE_DOXYGEN) { echo "  ✓ Génération de documentation Doxygen" }
        }
        failure {
            echo "❌ Le pipeline CI orchestrateur pour le projet Plazza a échoué"
            echo "🔍 Vérifiez les logs des étapes individuelles pour plus de détails"
        }
    }
}
