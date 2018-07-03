/**
 * I build this library and alert on test failures
 **/
pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                sh './gradlew build'
            }
        }
    }
    post {
        success {
            slackSend failOnError: true, color: 'good', teamDomain:'makara', channel: "tarver-build", tokenCredentialId: "tarver-build-slack-token", message: "<${env.JOB_DISPLAY_URL}|*${env.JOB_NAME}*>: succeeded"
        }
        unstable {
            slackSend failOnError: true, color: 'warning', teamDomain:'makara', channel: "tarver-build", tokenCredentialId: "tarver-build-slack-token", message: "<${env.JOB_DISPLAY_URL}|*${env.JOB_NAME}*>: has test failures"
        }
        failure {
            slackSend failOnError: true, color: 'danger', teamDomain:'makara', channel: "tarver-build", tokenCredentialId: "tarver-build-slack-token", message: "<${env.JOB_DISPLAY_URL}|*${env.JOB_NAME}*>: failed"
        }
    }
}