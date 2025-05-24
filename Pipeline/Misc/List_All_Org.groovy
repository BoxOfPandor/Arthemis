pipeline {
    agent any
    environment {
        GITHUB_TOKEN = credentials('github-token')
    }
    stages {
        stage('Lister les organisations') {
            steps {
                sh '''
                    mkdir -p gh-cli
                    cd gh-cli

                    # Télécharger et installer gh si nécessaire
                    if [ ! -f bin/gh ]; then
                        echo "Téléchargement de GitHub CLI..."
                        curl -sL https://github.com/cli/cli/releases/download/v2.49.0/gh_2.49.0_linux_amd64.tar.gz -o gh.tar.gz
                        tar -xzf gh.tar.gz --strip-components=1
                        chmod +x bin/gh
                    fi

                    export PATH=$PWD/bin:$PATH

                    echo "Organisations GitHub associées à ce compte :"
                    gh api user/orgs --paginate --jq '.[].login'
                '''
            }
        }
    }
}
