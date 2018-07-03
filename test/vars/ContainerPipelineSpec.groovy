package vars

import com.makara.jenkins.JenkinsSpecification

class ContainerPipelineSpec extends JenkinsSpecification {
    def env
    def stageCommands
    def scriptUnderSpec
    def configMap

    def setup() {
        env = [TARGET_ENV: "DEV"]
        stageCommands = [
                build          : "build",
                test           : "test",
                package        : "package",
                deploy         : "deploy",
                integrationTest: "intTest",
                canaryDeploy   : "canaryDeploy",
                canaryTest     : "canaryTest",
                canaryRollback : "canaryRollback",
                prodDeploy     : "prodDeploy",
                prodTest       : "prodTest"
        ]

        configMap = [
                environment: [
                        PROD_LOCATIONS: 'ut1',
                        CANARY_LOCATION: 'uc1',
                ],
                pipeline: [
                        dockerGroup: 'makara',
                        slackChannel: 'tarver-build',
                        slackCredentialId: 'tarver-build-slack-token',
                ],
                stageCommands: stageCommands
        ]

        binding.setVariable('env', env)
        binding.setVariable('stageCommands', stageCommands)

        scriptUnderSpec = loadScript("vars/containerPipeline.groovy")

        helper.registerAllowedMethod("containerCdPipeline", [Map], null)
        helper.registerAllowedMethod("containerCiPipeline", [Map], null)
        helper.registerAllowedMethod("error", [String], { throw new RuntimeException(it) })
    }

    def 'Create containerPipeline in prod'() {
        given:
        env = [TARGET_ENV: "prod"]
        binding.setVariable('env', env)

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify("containerCdPipeline", configMap)
    }

    def 'Create containerPipeline in non prod env'() {
        given: 'any environment'

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify("containerCiPipeline", configMap)
    }

    def 'Create containerPipeline with cd override as true'() {
        given:
        configMap.cd = true

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify("containerCdPipeline", configMap)
    }

    def 'Create containerPipeline with cd override as false'() {
        given:
        configMap.cd = false

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify("containerCiPipeline", configMap)
    }

    def 'Create containerPipeline without stageCommands'() {
        given:
        configMap.remove('stageCommands')

        when:
        scriptUnderSpec.call(configMap)

        then:
        thrown RuntimeException
        binding.getVariable('currentBuild').result == "ABORTED"
    }

    def 'Create containerPipeline with missing a required stageCommand'() {
        given: 'stageCommands is missing a required stage'
        binding.setVariable('stageCommands', stageCommands.remove('test'))

        when:
        scriptUnderSpec.call(configMap)

        then:
        RuntimeException e = thrown()
        // we threw an exception
        e != null
        // the exception identified the stage missing the command
        e.message.contains("'test' is required")
        // the build was aborted (not failed, etc.)
        binding.getVariable('currentBuild').result == "ABORTED"
    }

    def 'Create containerPipeline with missing required env variable'() {
        given: 'CANARY_LOCATION is missing'
        configMap.get('environment').remove('CANARY_LOCATION')

        when:
        scriptUnderSpec.call(configMap)

        then:
        RuntimeException e = thrown()
        // we threw an exception
        e != null
        // the missing env var is identified
        e.message.contains("'CANARY_LOCATION' is required")
        // the build was aborted (not failed, etc.)
        binding.getVariable('currentBuild').result == "ABORTED"
    }

    def 'Create containerPipeline and skipCanaryStage'() {
        given: 'skipCanaryStage is true and CANARY_LOCATION env is missing'
        configMap.get('environment').remove('CANARY_LOCATION')
        configMap.get('pipeline').put('skipCanaryStage', 'true')

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify("containerCiPipeline", configMap)
    }

    def 'Create containerPipeline and skipCanaryStage by absence of canaryDeploy stageCommand'() {
        given: 'canaryDeploy stageCommand is not present'
        configMap.get('environment').remove('CANARY_LOCATION')
        configMap.get('stageCommands').remove('canaryDeploy')

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify("containerCiPipeline", configMap)
    }

    def 'Create containerPipeline with no facts or secrets'() {
        given: 'no files or secrets exist'
        configMap.get('pipeline').remove('facts')
        configMap.get('pipeline').remove('secrets')

        when:
        scriptUnderSpec.call(configMap)

        then: 'verify facts and secrets are empty arrays'
        verify("containerCiPipeline", configMap)
        configMap.pipeline.secrets.empty
        configMap.pipeline.facts.empty

    }
}
