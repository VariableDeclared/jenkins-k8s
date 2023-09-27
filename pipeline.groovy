final JUJU_CREDENTIAL_TEMPLATE = '''
credentials:
  aws:
    aws-juju-user:
      access-key: ${access_key}
      auth-type: access-key
      secret-key: ${access_secret}
'''

final CFG_PATH = './credentials.yaml'

import groovy.text.StreamingTemplateEngine

def renderTemplate(input, variables) {
    def engine = new StreamingTemplateEngine()
    return engine.createTemplate(input).make(variables).toString()
}

pipeline {
    agent any
    parameters {
        string(name: 'UBUNTU_IMAGE_ID', defaultValue: 'none', description: 'Openstack image ID')
        string(name: 'OS_SERIES', defaultValue: 'jammy', description: 'Ubuntu OS Series')
        string(name: 'OPENSTACK_REGION', defaultValue: 'az1', description: 'Openstack regions')
        string(name: 'OPENSTACK_URL', defaultValue: 'https://localhost:9000/auth/v3', description: 'Keystone URL')
        string(name: 'GIT_REPO', defaultValue: 'https://github.com/variabledeclared/blah', description: 'Keystone URL')
    }
    stages {
        stage('Git Checkout') {
            steps {
                checkout scmGit(branches: [[name: 'main']],
                    userRemoteConfigs: [
                        [url: params.GIT_REPO]
                    ],
                    extensions: [ RelativeTargetDirectory: './jenkins-terraform']
                    )
            }
        }
        stage('Build Openstack Environment') {
            steps {
                dir('jenkins-terraform') {
                    sh 'sudo snap install terraform || true'
                    sh 'terraform init || true'
                    sh 'terraform apply -auto-approve'
                    // sh './scripts/generate-jujumetadata.sh'
                }
            }
        }
        stage('Bootstrap juju') {
            environment {
                NOVA_RC_BASE64 = credentials('jenkins-novarc')
            }
            steps {
                sh 'sudo snap install juju || true'
                

                sh 'mkdir -p /home/jenkins/.local/share || true'

                writeFile file: CFG_PATH, text: renderTemplate(JUJU_CREDENTIAL_TEMPLATE, ['access_key': JUJU_ACCESS_KEY, 'access_secret': JUJU_ACCESS_SECRET])
                sh 'juju add-credential --client aws -f ./credentials.yaml'
                sh 'juju bootstrap aws/eu-west-1'
            }
        }
        stage('Deploy Kubernetes') {
            steps {
                sh 'juju add-model k8s-jenkins'
                sh 'juju deploy charmed-kubernetes'
            }
        }
    }
}
