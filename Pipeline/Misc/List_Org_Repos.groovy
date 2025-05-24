pipeline {
    agent any
    parameters {
        string(name: 'ORG_NAME', defaultValue: 'MaSuperOrganisation', description: 'Nom de l’organisation GitHub')
        string(name: 'YEAR', defaultValue: '2025', description: 'Année de création des dépôts à filtrer')
    }
    environment {
        GITHUB_TOKEN = credentials('github-token')
    }
    stages {
        stage("Préparer l’environnement") {
            steps {
                sh '''
                    # Installer jq si absent
                    if ! command -v jq &> /dev/null
                    then
                        echo "Installation de jq..."
                        apt update && apt install -y jq
                    fi

                    # Installer GitHub CLI si absent
                    if [ ! -f gh-cli/bin/gh ]; then
                        echo "Téléchargement de GitHub CLI..."
                        mkdir -p gh-cli
                        cd gh-cli
                        curl -sL https://github.com/cli/cli/releases/download/v2.49.0/gh_2.49.0_linux_amd64.tar.gz -o gh.tar.gz
                        tar -xzf gh.tar.gz --strip-components=1
                        chmod +x bin/gh
                        cd ..
                    fi

                    export PATH=$PWD/gh-cli/bin:$PATH
                '''
            }
        }
        stage("Lister les dépôts GitHub") {
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
                    sh '''
                        export PATH=$PWD/gh-cli/bin:$PATH

                        echo "Repos créés en ${ANNEE} dans l’organisation ${ORG_NAME}:"
                        gh repo list "${ORG_NAME}" --json name,createdAt --limit 1000 | \
                        jq -r --arg year "${ANNEE}" '.[] | select(.createdAt | startswith($year)) | .name'
                    '''
                }
            }
        }
    }
}
