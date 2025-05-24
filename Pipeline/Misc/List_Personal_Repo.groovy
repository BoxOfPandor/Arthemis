pipeline {
    agent any
    environment {
        GITHUB_TOKEN = credentials('github-token')
    }
    stages {
        stage('Télécharger GitHub CLI') {
            steps {
                sh '''
                    mkdir -p gh-cli
                    cd gh-cli

                    echo "Téléchargement de GitHub CLI..."
                    curl -sL https://github.com/cli/cli/releases/download/v2.49.0/gh_2.49.0_linux_amd64.tar.gz -o gh.tar.gz

                    echo "Extraction de l'archive..."
                    tar -xzf gh.tar.gz --strip-components=1 || { echo "Extraction échouée !"; file gh.tar.gz; exit 1; }

                    chmod +x bin/gh
                '''
            }
        }
        stage('Lister les dépôts GitHub') {
            steps {
                sh '''
                    export PATH=$PWD/gh-cli/bin:$PATH

                    echo "Statut de l'authentification GitHub :"
                    gh auth status || { echo "Échec auth."; exit 1; }

                    echo "Dépôts trouvés :"
                    gh repo list --limit 100
                '''
            }
        }
    }
}
