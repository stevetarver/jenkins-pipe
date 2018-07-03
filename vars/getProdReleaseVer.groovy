import groovy.json.JsonSlurper

def call(Closure body) {

    script {
        def response = retryWithDelay {
            httpRequest authentication: env.githubJenkinsCreds, url: "https://api.github.com/repos/${GIT_ORG_REPO}/releases/latest", validResponseCodes: '200,404'
        }

        if(response.status == 404) {
            env.LATEST_RELEASE_VER = ""
            env.LATEST_RELEASE_BUILD_TIMESTAMP = "0"
            env.RELEASE_VER = "1.0.0"
        } else {
            def latestRelease = new JsonSlurper().parseText(response.content)
            env.LATEST_RELEASE_VER = latestRelease.name
            env.LATEST_RELEASE_BUILD_TIMESTAMP = latestRelease.assets.find { k, v -> k.name.startsWith('build.') }?.name?.tokenize(".")?.last()
            latestRelease = null // must discard due to not being serializable

            env.RELEASE_VER = sh(script: '''
                if [ "${releaseType}" = 'master' ]; then
                    TEMP_VER=$(echo ${LATEST_RELEASE_VER} | cut -f1,2 -d".")
                    RELEASE_VER="${TEMP_VER%.*}.$((${TEMP_VER##*.}+1)).0"
                elif [ "${releaseType}" = 'hotfix' ]; then
                    RELEASE_VER="${LATEST_RELEASE_VER%.*}.$((${LATEST_RELEASE_VER##*.}+1))"
                fi
                echo ${RELEASE_VER}
            ''', returnStdout: true).trim()
        }

        env.DOCKER_RELEASE_IMAGE_NAMETAG = "${DOCKER_IMAGE_BASENAME}:${RELEASE_VER}"
    }
}

return this
