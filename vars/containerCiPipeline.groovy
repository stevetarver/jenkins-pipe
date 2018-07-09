def call(config) {

    def dockerCiAgent = {
        docker {
            image DOCKER_CI_IMAGE
            registryUrl env.dockerRegistryUrl
            registryCredentialsId env.dockerJenkinsCreds
            alwaysPull false
            reuseNode true
            args env.dockerCiArgs
        }
    }

    node {
        echo '===== Pipeline Initialization begin ================================================================'
        checkout scm

        initPipelineVars()
        sh "env | sort"

        retryWithDelay {
            withCredentials([usernamePassword(
                    credentialsId: env.dockerJenkinsCreds,
                    passwordVariable: 'DOCKER_REG_PASSWORD',
                    usernameVariable: 'DOCKER_REG_USER')]) {
                sh """
                    echo 'eagerly fetching CI image...'
                    docker login -u ${DOCKER_REG_USER} -p ${DOCKER_REG_PASSWORD} ${dockerRegistryUrl}
                    docker pull ${DOCKER_CI_IMAGE}
                    docker logout ${dockerRegistryUrl}
                """
            }
        }
        echo '===== Pipeline Initialization end   ================================================================'
    }

    pipeline {
        agent any
        options { timestamps() }

        stages {
            stage('Build') {
                agent { dockerCiAgent() }
                when { allOf { not { branch 'release' }; expression { config.stageCommands.get 'build'} } }
                steps {
                    echo '===== Build stage begin ============================================================================'
                    sh "rm -f ${gradleLock}"
                    sh "${config.stageCommands.get 'build'}"
                    echo '===== Build stage end   ============================================================================'
                }
            }
            stage('Test') {
                agent { dockerCiAgent() }
                when { not { branch 'release' } }
                steps {
                    // Allow clients to pull other images for testing
                    withCredentials([usernamePassword(
                            credentialsId: env.dockerJenkinsCreds,
                            passwordVariable: 'DOCKER_REG_PASSWORD',
                            usernameVariable: 'DOCKER_REG_USER')]) {
                        echo '===== Test stage begin ============================================================================='
                        sh "rm -f ${gradleLock}"
                        sh "${config.stageCommands.get 'test'}"
                        echo '===== Test stage end   ============================================================================='
                    }
                }
                post {
                    always {
                        junit allowEmptyResults: true, testResults: env.test_unitTestResults
                        publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: env.test_codeCoverageHtmlDir, reportFiles: 'index.html', reportName: "Code Coverage", reportTitles: ''])
                    }
                }
            }
            stage('Package') {
                agent { dockerCiAgent() }
                when { anyOf {
                    allOf { branch 'master'; environment name: 'TARGET_ENV', value: 'dev' }
                    allOf { branch 'candidate'; environment name: 'TARGET_ENV', value: 'pre-prod' }
                    allOf { branch 'hotfix'; environment name: 'TARGET_ENV', value: 'pre-prod' }}}
                steps {
                    withCredentials([usernamePassword(
                            credentialsId: env.nexusJenkinsCreds,
                            passwordVariable: 'NEXUS_PASSWORD',
                            usernameVariable: 'NEXUS_USER')]) {
                        echo '===== Package stage begin =========================================================================='
                        sh "rm -f ${gradleLock}"
                        sh "${config.stageCommands.get 'package'}"
                        echo '===== Package stage end   =========================================================================='
                    }
                }
            }
            stage('Archive') {
                when { anyOf {
                    allOf { branch 'master'; environment name: 'TARGET_ENV', value: 'dev' }
                    allOf { branch 'candidate'; environment name: 'TARGET_ENV', value: 'pre-prod' }
                    allOf { branch 'hotfix'; environment name: 'TARGET_ENV', value: 'pre-prod' }}}
                steps {
                    withCredentials([usernamePassword(
                            credentialsId: env.dockerJenkinsCreds,
                            passwordVariable: 'DOCKER_REG_PASSWORD',
                            usernameVariable: 'DOCKER_REG_USER')]) {
                        echo '===== Archive stage begin =========================================================================='
                        retryWithDelay {
                            sh '''
                                docker login -u ${DOCKER_REG_USER} -p ${DOCKER_REG_PASSWORD} ${dockerRegistryUrl}
                                docker push ${DOCKER_BUILD_IMAGE_NAMETAG}
                                docker push ${DOCKER_BUILD_IMAGE_NAMETAG_LATEST}
                                docker logout ${dockerRegistryUrl}
                            '''
                        }
                        echo '===== Archive stage end =========================================================================='
                    }
                }
            }
            stage('Deploy') {
                when { anyOf {
                    allOf { branch 'master'; environment name: 'TARGET_ENV', value: 'dev' }
                    allOf { branch 'candidate'; environment name: 'TARGET_ENV', value: 'pre-prod' }
                    allOf { branch 'hotfix'; environment name: 'TARGET_ENV', value: 'pre-prod' }}}
                steps {
                    withCredentials(config?.pipeline?.secrets) {
                        configFileProvider(config?.pipeline?.facts) {
                            echo '===== Deploy stage begin ==========================================================================='
                            sh "${config.stageCommands.get 'deploy'}"
                            echo '===== Deploy stage end   ==========================================================================='
                        }
                    }
                }
            }
            stage('Integration Test') {
                agent { dockerCiAgent() }
                when { anyOf {
                    allOf { branch 'master'; environment name: 'TARGET_ENV', value: 'dev' }
                    allOf { branch 'candidate'; environment name: 'TARGET_ENV', value: 'pre-prod' }
                    allOf { branch 'hotfix'; environment name: 'TARGET_ENV', value: 'pre-prod' }}}
                steps {
                    // Allow clients to pull other images for testing
                    withCredentials([usernamePassword(
                            credentialsId: env.dockerJenkinsCreds,
                            passwordVariable: 'DOCKER_REG_PASSWORD',
                            usernameVariable: 'DOCKER_REG_USER')]) {
                        echo '===== Integration Test stage begin ================================================================='
                        sh "rm -f ${gradleLock}"
                        sh "${config.stageCommands.get 'integrationTest'}"
                        echo '===== Integration Test stage end   ================================================================='
                    }
                }
            }
        }
        post {
            success {
                script {
                    if ('true' != env.skipSlackNotifications) {
                        retryWithDelay {
                            slackSend failOnError: true, color: 'good', teamDomain: "${env.slackWorkspace}", channel: "${env.slackChannel}", tokenCredentialId: "${env.slackCredentialId}", message: "<${env.JOB_DISPLAY_URL}|*${env.DOCKER_PROJECT}*>: Built ${env.BRANCH_NAME} v${env.BUILD_VER}"
                        }
                    }
                }
            }
            unstable {
                script {
                    if ('true' != env.skipSlackNotifications) {
                        retryWithDelay {
                            slackSend failOnError: true, color: 'warning', teamDomain: "${env.slackWorkspace}", channel: "${env.slackChannel}", tokenCredentialId: "${env.slackCredentialId}", message: "<${env.JOB_DISPLAY_URL}|*${env.DOCKER_PROJECT}*>: Built ${env.BRANCH_NAME} v${env.BUILD_VER} with test failures"
                        }
                    }
                }
            }
            failure {
                script {
                    if ('true' != env.skipSlackNotifications) {
                        retryWithDelay {
                            slackSend failOnError: true, color: 'danger', teamDomain: "${env.slackWorkspace}", channel: "${env.slackChannel}", tokenCredentialId: "${env.slackCredentialId}", message: "<${env.JOB_DISPLAY_URL}|*${env.DOCKER_PROJECT}*>: Build failed ${env.BRANCH_NAME} v${env.BUILD_VER}"
                        }
                    }
                }
            }
            always {
                echo '===== Post build cleanup begin ====================================================================='
                postBuildDockerCleanup()
                echo '===== Post build cleanup end   ====================================================================='
            }
        }
    }
}
