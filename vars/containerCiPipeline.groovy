def call(config) {
    node {
        echo '===== Pipeline Initialization begin ================================================================'
        checkout scm

        initEnvVars()
        sh "env | sort"

        retryWithDelay {
            withCredentials([usernamePassword(
                    credentialsId: env.dockerJenkinsCreds,
                    passwordVariable: 'DOCKER_REG_PASSWORD',
                    usernameVariable: 'DOCKER_REG_USER')]) {
                sh """
                    echo 'eagerly fetching CI image...'
                    docker login -u ${DOCKER_REG_USER} -p ${DOCKER_REG_PASSWORD} ${env.dockerRegistryUrl}
                    docker pull ${env.DOCKER_CI_IMAGE}
                    docker logout ${env.dockerRegistryUrl}
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
                when { allOf { not { branch 'release' }; expression { config.stageCommands.get 'build'} } }
                steps {
                    echo '===== Build stage begin ============================================================================'
                    sh "rm -f ${gradleLock}"
                    sh "${config.stageCommands.get 'build'}"
                    echo '===== Build stage end   ============================================================================'
                }
            }
            stage('Test') {
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
                when { anyOf { branch 'master'; branch 'hotfix' } }
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
                when { anyOf { branch 'master'; branch 'hotfix' } }
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
                when { anyOf { branch 'master'; branch 'hotfix' } }
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
                when { anyOf { branch 'master'; branch 'hotfix' } }
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
                // TODO: move this to its own var
                echo '===== Post build cleanup begin ====================================================================='
                sh '''
                    ECHO_PREFIX='===>'
                    
                    # Get all containers for this project and branch
                    CONTAINERS=$(docker ps -a | grep ${DOCKER_BUILD_IMAGE_NAME} | cut -f1 -d' ')
                
                    if [ -n "${CONTAINERS}" ]; then
                        echo "${ECHO_PREFIX} Stopping and removing ${DOCKER_BUILD_IMAGE_NAME} containers"
                        docker rm -f -v ${CONTAINERS}
                    else
                        echo "${ECHO_PREFIX} No ${DOCKER_BUILD_IMAGE_NAME} containers running"
                    fi
                
                    IMAGES=$(docker images -q ${DOCKER_BUILD_IMAGE_NAME} | uniq)
                
                    if [ -n "${IMAGES}" ]; then
                        echo "${ECHO_PREFIX} Removing ${DOCKER_BUILD_IMAGE_NAME} images"
                        docker rmi -f ${IMAGES}
                    else
                        echo "${ECHO_PREFIX} No ${DOCKER_BUILD_IMAGE_NAME} images exist"
                    fi
                
                    echo "${ECHO_PREFIX} Removing orphaned docker volumes"
                    docker volume prune --force
                     
                    # see https://www.projectatomic.io/blog/2015/07/what-are-docker-none-none-images/
                    # Currently, this casts too wide a net
                    # DANGLING=$(docker images -q -f 'dangling=true')
                    # if [ -n "${DANGLING}" ]; then
                    #     echo "${ECHO_PREFIX} Removing dangling images"
                    #     docker rmi -f ${DANGLING}
                    # else
                    #     echo "${ECHO_PREFIX} No dangling images exist"
                    # fi
                 '''
                echo '===== Post build cleanup end   ====================================================================='
            }
        }
    }
}
