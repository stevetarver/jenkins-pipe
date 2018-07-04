import static com.makara.jenkins.Utils.isStringNullOrEmpty

/**
 * I generate all base environment variables.
 *
 * Environment requirements:
 * - build job target cloned (scm checkout) - we use git information to generate image labels
 * - if performing a release (containerCdPipeline), env.releaseType set
 *
 * I must be called from actual pipeline code, after scm checkout, after release version
 * processing, to provide the base for names I create.
 *
 * Variables that may be safely used by clients are upper cased: BUILD_TIMESTAMP.
 * Things that we consider private to the pipeline will be camel-cased.
 *
 * NOTE: all vars are stored in the 'env' hash so they are serialized properly prior
 *       to hand off to a slave executor.
 */
def call(Closure unused) {

    // Our prime docker image identifier/version tag. A timestamp ensures no duplicate tags.
    env.BUILD_TIMESTAMP = (new Date().format("yyyyMMddHHmmssSSS", TimeZone.getTimeZone('UTC'))).toString()
    env.BUILD_VER = env.BUILD_TIMESTAMP

    // Credentials that must be set in Jenkins for this pipeline
    env.dockerJenkinsCreds = 'dockerhub-jenkins-account'
    env.githubJenkinsCreds = 'github-jenkins-account'
    env.nexusJenkinsCreds = 'nexus-jenkins-account'

    // Docker image labelling variables to help locate source from any runtime component
    env.GIT_REPO_URL = sh(script: 'git config remote.origin.url', returnStdout: true).trim()
    env.GIT_ORG_REPO = sh (script: 'git config remote.origin.url | cut -f4-5 -d"/" | cut -f1 -d"."', returnStdout: true).trim()
    env.GIT_REPO_NAME = sh(script: 'git config remote.origin.url | cut -d "/" -f5 | cut -d "." -f1', returnStdout: true).trim()
    env.COMMIT_HASH = sh (script: 'git rev-parse HEAD', returnStdout: true).trim()

    // The registry part of the fully qualified image name is omitted if isDockerHub
    // to simplify shell scripting based on image name. Docker omits any docker hub
    // registry prefix on 'docker images' so we can't find build artifacts during
    // post build cleanup.
    // Set to false if using any registry other than docker hub
    def isDockerhub = true
    // Used for tagging docker images: registry/group/repo:tag
    env.dockerRegistry = 'docker.io'
    // Used for registry logins - both pipeline agent declarations and shell
    env.dockerRegistryUrl = 'https://registry.hub.docker.com'
    // Docker CI image start options: link to the host docker sock, use host volume for cached items
    env.dockerCiArgs = '-v /root/.m2/repository:/root/.m2/repository -v /root/.gradle/caches/modules-2:/home/gradle/.gradle/caches/modules-2 -v /root/.gradle/wrapper:/home/gradle/.gradle/wrapper -v /var/run/docker.sock:/var/run/docker.sock'
    // This lock file prevents concurrent builds, used for caching.
    // We've had good success just deleting it on each build start.
    env.gradleLock = '/home/gradle/.gradle/caches/modules-2/modules-2.lock'

    // DOCKER_PROJECT is used to identify the docker image created, the 'repo', when
    // tagging the image, for slack notifications, etc. The default is the GitHub repo
    // name. We allow users to specify their own for cases where there are multiple docker
    // images in a single repo.
    if (isStringNullOrEmpty(env.DOCKER_PROJECT)) {
        env.DOCKER_PROJECT = env.GIT_REPO_NAME
    }

    // Ensure that docker names and tags are legal
    // Legal names are defined here: https://docs.docker.com/engine/reference/commandline/tag/
    if(isDockerhub) {
        env.DOCKER_IMAGE_BASENAME = "${env.dockerGroup}/${env.DOCKER_PROJECT}".toLowerCase()
    } else {
        env.DOCKER_IMAGE_BASENAME = "${env.dockerRegistry}/${env.dockerGroup}/${env.DOCKER_PROJECT}".toLowerCase()
    }

    env.DOCKER_BUILD_IMAGE_NAME = "${env.DOCKER_IMAGE_BASENAME}-${env.BRANCH_NAME}".toLowerCase()
    env.DOCKER_BUILD_IMAGE_NAMETAG = "${env.DOCKER_BUILD_IMAGE_NAME}:${env.BUILD_VER}"
    env.DOCKER_BUILD_IMAGE_NAMETAG_LATEST = "${env.DOCKER_BUILD_IMAGE_NAME}:latest"

    env.DOCKER_DEPLOY_IMAGE_NAMETAG = "${env.DOCKER_BUILD_IMAGE_NAME}:${env.BUILD_VER}"

    if (env.releaseType == 'redeploy') {
        env.RELEASE_VER = "${releaseVersion}"
    }
}

return this
