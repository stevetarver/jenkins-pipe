/**
 * I am the entry point to CI and CD portions of the container pipeline
 *
 * I parse and validate configuration, merge it into the 'env' map, and call
 * the appropriate pipeline: CI for lower production levels, CD for production.
 *
 * NOTES:
 * - Since a single Jenkinsfile will define all args for any container* pipeline,
 *   we validate all args on each build.
 * - All data made available to stages must be in a serializable container;
 *   Each stage can potentially run on a different agent and serialization is
 *   used to get it to that agent.
 */
def call(Map config) {

    echo '===== Pipeline Validation begin ===================================================================='
    def errorList = ['Your Jenkinsfile has errors that prevent building this project:']

    // environment block ----------------------------------------------------------------
    env.DOCKER_PROJECT = config?.environment?.DOCKER_PROJECT
    env.DOCKER_CI_IMAGE = config?.environment?.DOCKER_CI_IMAGE
    env.CANARY_LOCATION = config?.environment?.CANARY_LOCATION
    env.PROD_LOCATIONS = config?.environment?.PROD_LOCATIONS

    // pipeline block -------------------------------------------------------------------
    env.dockerGroup = config?.pipeline?.dockerGroup
    env.skipCanaryStage = config?.pipeline?.skipCanaryStage
    env.slackWorkspace = config?.pipeline?.slackWorkspace
    env.slackChannel = config?.pipeline?.slackChannel
    env.slackCredentialId = config?.pipeline?.slackCredentialId
    env.test_unitTestResults = config?.pipeline?.test?.unitTestResults
    env.test_codeCoverageHtmlDir = config?.pipeline?.test?.codeCoverageHtmlDir

    config?.pipeline?.facts = config?.pipeline?.facts ?: []
    config?.pipeline?.secrets = config?.pipeline?.secrets ?: []
    echo "configured facts: ${config?.pipeline?.facts}"
    echo "configured secrets: ${config?.pipeline?.secrets}"

    // pipeline block validations -------------------------------------------------------
    def skipCanary = env.skipCanaryStage == "true" || !config?.stageCommands?.canaryDeploy

    // Slack notifications are optional - notifications are omitted if all values are blank
    def requiredPipeline = ['slackWorkspace', 'slackChannel', 'slackCredentialId']
    if(env.slackWorkspace || env.slackChannel || env.slackCredentialId) {
        requiredPipeline.each {
            // NOTE: everything in env is a string - if you assign null, you will get "null"
            if (null == env[it] || 'null' == env[it] || '' == env[it]?.trim()) {
                currentBuild.result = 'ABORTED'
                errorList += "==> pipeline block variable '${it}' is required."
            }
        }
    } else {
        env.skipSlackNotifications = 'true'
    }

    def requiredEnvironment = []
    if(!skipCanary) {
        requiredEnvironment << "CANARY_LOCATION"
    }
    requiredEnvironment.each {
        // NOTE: everything in env is a string - if you assign null, you will get "null"
        if (null == env[it] || 'null' == env[it] || '' == env[it]?.trim()) {
            currentBuild.result = 'ABORTED'
            errorList += "==> environment block variable '${it}' is required."
        }
    }

    // stageCommands block --------------------------------------------------------------
    def requiredStageCommands = ['test', 'package', 'deploy', 'integrationTest', 'prodDeploy', 'prodTest']

    if (!skipCanary) {
        requiredStageCommands.addAll(['canaryDeploy', 'canaryTest', 'canaryRollback'])
    }

    if (config?.stageCommands == null || config?.stageCommands?.isEmpty()) {
        currentBuild.result = 'ABORTED'
        errorList += "==> You must provide stageCommands for the following stages: ${requiredStageCommands}."
    }
    else {
        requiredStageCommands.each {
            if (config?.stageCommands?.get(it) == null || config?.stageCommands?.get(it) == '') {
                currentBuild.result = 'ABORTED'
                errorList += "==> stageCommand block variable '$it' is required."
            }
        }
    }

    // If any validation failed, abort the build with detailed fix instructions ---------
    if('ABORTED' == currentBuild.result) {
        errorList += ''
        errorList += 'See the jenkins-pipe README for more information: https://github.com/stevetarver/jenkins-pipe'
        error(errorList.join('\n'))
    }

    echo '===== Pipeline Validation end   ===================================================================='

    // Execute the pipeline -------------------------------------------------------------
    // env.TARGET_ENV is set per Jenkins deployment to identify the deployment target environment
    // Set config.cd to truthy to allow testing the CD pipeline in pre-prod environments
    if (env.TARGET_ENV == 'prod' || config?.cd) {
        containerCdPipeline(config)
    } else {
        containerCiPipeline(config)
    }
}

return this
