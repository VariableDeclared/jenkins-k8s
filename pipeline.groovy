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
    stages {
        stage('Bootstrap juju') {
            environment {
                JUJU_ACCESS_KEY = credentials("aws-access-key")
                JUJU_ACCESS_SECRET = credentials("aws-access-secret")
            }
            steps {
                sh 'sudo snap install juju || true'
                sh 'mkdir -p /home/jenkins/.local/share || true' 
        
                writeFile file: CFG_PATH, text: renderTemplate(JUJU_CREDENTIAL_TEMPLATE, ['access_key': JUJU_ACCESS_KEY, 'access_secret': JUJU_ACCESS_SECRET])
                sh 'juju add-credential --client aws -f ./credentials.yaml'
                sh 'juju bootstrap aws/eu-west-1'
            }
        }
    }
}