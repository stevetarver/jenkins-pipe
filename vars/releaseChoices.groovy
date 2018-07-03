import groovy.json.JsonSlurper

def call(body) {
    def GIT_ORG_REPO = ''

    node {
        checkout scm
        GIT_ORG_REPO = sh(script: 'git config remote.origin.url | cut -f4-5 -d"/" | cut -f1 -d"."', returnStdout: true).trim()
    }

    //defaults
    // TODO: use toConfigMap and change GIT_ORG_REPO to a proper variable name
    def config = [maxChoices: 3, repo: GIT_ORG_REPO]

    if (body) {
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
        body()
    }

    def response = httpRequest authentication: env.githubJenkinsCreds, url: "https://api.github.com/repos/${config.repo}/releases?per_page=${config.maxChoices}"
    def jsonResponse = new JsonSlurper().parseText(response.content)
    def previousReleases = jsonResponse.inject([]) { result, r -> result << r.name }
    def choices = previousReleases;

    if (env.releaseType == "redeploy") {
        if (env.releaseVersion !="" && !(env.releaseVersion in previousReleases)) {
            currentBuild.result = "ABORTED"
            error("Selected releaseVersion ${env.releaseVersion} does not exist. There may have been an error during a previous release. Officially released versions are: " + previousReleases)
        }
    } else {
        choices = [RELEASE_VER] + previousReleases
    }

    ([''] + choices).join('\n')
}
