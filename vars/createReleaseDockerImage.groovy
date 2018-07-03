/**
 * Retag a proven good docker image with a semver appropriate tag
 */
def call(Closure body) {

    withEnv(['DUPLICATE_RELEASE_EXIT_CODE=126']) {
        withCredentials([usernamePassword(
                credentialsId: env.dockerJenkinsCreds,
                passwordVariable: 'DOCKER_REG_PASSWORD',
                usernameVariable: 'DOCKER_REG_USER')]) {

            retryWithDelay {
                env.DOCKER_BUILD_TIMESTAMP = sh(
                    script: '''
                    docker login -u ${DOCKER_REG_USER} -p ${DOCKER_REG_PASSWORD} ${dockerRegistryUrl} > /dev/null 2>&1
                    docker pull ${DOCKER_IMAGE_BASENAME}-${releaseType}:latest > /dev/null 2>&1

                    DOCKER_BUILD_TIMESTAMP=$(docker inspect --format='{{ index .ContainerConfig.Labels "clc.control.build.timestamp" }}' ${DOCKER_IMAGE_BASENAME}-${releaseType}:latest)
                    docker logout ${dockerRegistryUrl} > /dev/null 2>&1
                    echo $DOCKER_BUILD_TIMESTAMP
                ''', returnStdout: true).trim()
            }

            script {
                try {
                    sh '''
                        # ensure that we have not released this version yet
                        if [ "${LATEST_RELEASE_BUILD_TIMESTAMP}" = "${DOCKER_BUILD_TIMESTAMP}" ]; then
                            echo "${releaseType} v${LATEST_RELEASE_VER} has already been released."
                            echo "You can deploy this version using a redeploy of version ${LATEST_RELEASE_VER}."
                            exit ${DUPLICATE_RELEASE_EXIT_CODE}
                        fi
                    '''
                } catch (e) {
                    if (e.getMessage().contains("exit code " + env.DUPLICATE_RELEASE_EXIT_CODE)) {
                        currentBuild.result = "ABORTED"
                        error("Attempting duplicate release. Aborting.")
                    } else {
                        throw e
                    }
                }

            }

            retryWithDelay {
                sh '''
                    docker tag ${DOCKER_IMAGE_BASENAME}-${releaseType}:latest ${DOCKER_RELEASE_IMAGE_NAMETAG}
                    docker login -u ${DOCKER_REG_USER} -p ${DOCKER_REG_PASSWORD} ${dockerRegistryUrl}
                    docker push ${DOCKER_RELEASE_IMAGE_NAMETAG}
                    docker logout ${dockerRegistryUrl}
                '''
            }
        }
    }
}

return this
