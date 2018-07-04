def call(config) {
    node {
        echo '===== Pipeline Initialization begin ================================================================'
        checkout scm

        initPipelineVars()
        getProdReleaseVer()
        sh "env | sort"

        retryWithDelay {
            withCredentials([usernamePassword(
                    credentialsId: env.dockerJenkinsCreds,
                    passwordVariable: 'DOCKER_REG_PASSWORD',
                    usernameVariable: 'DOCKER_REG_USER')]) {
                sh """
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
        parameters {
            choice(choices: "master\nhotfix\nredeploy", description: 'Choose ReleaseType', name: 'releaseType')
            choice(choices: releaseChoices(), description: 'Required when ReleaseType is "redeploy"', name: 'releaseVersion')
        }

        environment {
            BRANCH_NAME = 'release'
            DOCKER_REDEPLOY_IMAGE_NAMETAG="${DOCKER_IMAGE_BASENAME}:${releaseVersion}"
        }

        stages {
            stage('Create Release') {
                when { expression { params.releaseType == 'master' ||  params.releaseType == 'hotfix'}}
                steps {
                    echo '===== Create Release stage begin ==================================================================='
                    createReleaseDockerImage()
                    createGitHubRelease()
                    echo '===== Create Release stage end   ==================================================================='
                }
            }
            stage('Canary Deploy') {
                when { not { environment name: 'skipCanaryStage', value: 'true' } }
                steps {
                    withCredentials(config?.pipeline?.secrets) {
                        configFileProvider(config?.pipeline?.facts) {
                            echo '===== Canary Deploy stage begin ===================================================================='
                            script {
                                env.ROLLBACK_REVISION = sh(script: 'helm history ${DOCKER_PROJECT}-${CANARY_LOCATION} --max=1 | awk \'BEGIN {FS="\\t"}; FNR==2{print $1}\'', returnStdout: true).trim()

                                // TODO: Clean up this ugliness
                                if (params.releaseType == 'redeploy') {
                                    if (!params.releaseVersion || params.releaseVersion == '') {
                                        currentBuild.result = 'ABORTED'
                                        error("releaseVersion is required for redeploy.")
                                    }
                                    env.DOCKER_DEPLOY_IMAGE_NAMETAG = "${DOCKER_REDEPLOY_IMAGE_NAMETAG}"
                                } else {
                                    env.DOCKER_DEPLOY_IMAGE_NAMETAG = "${DOCKER_RELEASE_IMAGE_NAMETAG}"
                                }
                            }
                            sh "${config.stageCommands.get 'canaryDeploy'}"
                            echo '===== Canary Deploy stage end   ===================================================================='
                        }
                    }
                }
            }
            stage('Canary Test') {
                agent {
                    docker {
                        image DOCKER_CI_IMAGE
                        registryUrl env.dockerRegistryUrl
                        registryCredentialsId env.dockerJenkinsCreds
                        alwaysPull false
                        reuseNode true
                        args dockerCiArgs
                    }
                }
                when { not { environment name: 'skipCanaryStage', value: 'true' } }
                steps {
                    echo '===== Canary Test stage begin ======================================================================'
                    script {
                        try {
                            env.ROLLBACK_MODE = false
                            // Allow clients to pull other images for testing
                            withCredentials([usernamePassword(
                                    credentialsId: env.dockerJenkinsCreds,
                                    passwordVariable: 'DOCKER_REG_PASSWORD',
                                    usernameVariable: 'DOCKER_REG')]) {
                                sh "rm -f ${gradleLock}"
                                sh "${config.stageCommands.get 'canaryTest'}"
                            }
                        } catch (Exception exception) {
                            echo 'Canary test had a problem: ' + exception.toString()
                            currentBuild.result = "UNSTABLE"
                            env.ROLLBACK_MODE = true
                        }

                        def message = 'Release to all production locations?'
                        def button = 'Deploy'

                        if (env.ROLLBACK_MODE == 'true') {
                            message = 'Canary testing had failures.'
                            button = 'Execute a Rollback'
                        }

                        timeout(time: 1, unit: 'HOURS') {
                            input(id: 'userInput', message: message, parameters: [], ok: button)
                        }
                    }
                    echo '===== Canary Test stage end   ======================================================================'
                }
            }
            stage('Canary Rollback') {
                when {  environment name: 'ROLLBACK_MODE', value: 'true' }
                steps {
                    echo '===== Canary Rollback stage begin =================================================================='
                    sh "${config.stageCommands.get 'canaryRollback'}"
                    echo '===== Canary Rollback stage end   =================================================================='
                }
            }
            stage('Prod Deploy') {
                when { not {environment name: 'ROLLBACK_MODE', value: 'true'}}
                steps {
                    withCredentials(config?.pipeline?.secrets) {
                        configFileProvider(config?.pipeline?.facts) {
                            echo '===== Prod Deploy stage begin ======================================================================'
                            script {
                                if (params.releaseType == 'redeploy') {
                                    if (!params.releaseVersion || params.releaseVersion == '') {
                                        currentBuild.result = 'ABORTED'
                                        error("releaseVersion is required for redeploy.")
                                    }
                                    env.DOCKER_DEPLOY_IMAGE_NAMETAG = "${DOCKER_REDEPLOY_IMAGE_NAMETAG}"
                                } else {
                                    env.DOCKER_DEPLOY_IMAGE_NAMETAG = "${DOCKER_RELEASE_IMAGE_NAMETAG}"
                                }
                            }
                            sh "${config.stageCommands.get 'prodDeploy'}"
                            echo '===== Prod Deploy stage end   ======================================================================'
                        }
                    }
                }
            }
            stage('Prod Test') {
                agent {
                    docker {
                        image DOCKER_CI_IMAGE
                        registryUrl env.dockerRegistryUrl
                        registryCredentialsId env.dockerJenkinsCreds
                        alwaysPull false
                        reuseNode true
                        args dockerCiArgs
                    }
                }
                when { not { environment name: 'ROLLBACK_MODE', value: 'true' } }
                steps {
                    // Allow clients to pull other images for testing
                    withCredentials([usernamePassword(
                            credentialsId: env.dockerJenkinsCreds,
                            passwordVariable: 'DOCKER_REG_PASSWORD',
                            usernameVariable: 'DOCKER_REG_USER')]) {
                        echo '===== Prod Test stage begin ========================================================================'
                        sh "rm -f ${gradleLock}"
                        sh "${config.stageCommands.get 'prodTest'}"
                        echo '===== Prod Test stage end   ========================================================================'
                    }
                }
            }
        }

        post {
            success {
                script {
                    if ('true' != env.skipSlackNotifications) {
                        retryWithDelay {
                            slackSend failOnError: true, color: 'good', teamDomain: "${env.slackWorkspace}", channel: "${env.slackChannel}", tokenCredentialId: "${env.slackCredentialId}", message: "<${env.JOB_DISPLAY_URL}|*${env.DOCKER_PROJECT}*>: Built ${env.BRANCH_NAME} v${env.RELEASE_VER}"
                        }
                    }
                }
            }
            unstable {
                script {
                    if ('true' != env.skipSlackNotifications) {
                        retryWithDelay {
                            slackSend failOnError: true, color: 'warning', teamDomain: "${env.slackWorkspace}", channel: "${env.slackChannel}", tokenCredentialId: "${env.slackCredentialId}", message: "<${env.JOB_DISPLAY_URL}|*${env.DOCKER_PROJECT}*>: Built ${env.BRANCH_NAME} v${env.RELEASE_VER} with test failures"
                        }
                    }
                }
            }
            failure {
                script {
                    if ('true' != env.skipSlackNotifications) {
                        retryWithDelay {
                            slackSend failOnError: true, color: 'danger', teamDomain: "${env.slackWorkspace}", channel: "${env.slackChannel}", tokenCredentialId: "${env.slackCredentialId}", message: "<${env.JOB_DISPLAY_URL}|*${env.DOCKER_PROJECT}*>: Build failed ${env.BRANCH_NAME} v${env.RELEASE_VER}"
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
