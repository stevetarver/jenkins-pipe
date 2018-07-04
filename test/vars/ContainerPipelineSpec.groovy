package vars

import com.makara.jenkins.JenkinsSpecification

class ContainerPipelineSpec extends JenkinsSpecification {
    def env
    def pipeline
    def stageCommands
    def scriptUnderSpec
    def configMap

    def setup() {
        env = [
                TARGET_ENV: 'dev',
        ]
        stageCommands = [
                build          : 'build',
                test           : 'test',
                package        : 'package',
                deploy         : 'deploy',
                integrationTest: 'intTest',
                canaryDeploy   : 'canaryDeploy',
                canaryTest     : 'canaryTest',
                canaryRollback : 'canaryRollback',
                prodDeploy     : 'prodDeploy',
                prodTest       : 'prodTest'
        ]
        pipeline = [
                dockerGroup: 'makara',
                slackWorkspace: 'makaradesigngroup',
                slackChannel: 'build',
                slackCredentialId: 'makaradesigngroup-build-slack-token',
        ]
        configMap = [
                environment: [
                        DOCKER_CI_IMAGE: 'stevetarver/maven-java-ci:3.5.4-jdk-8-alpine-r0',
                        PROD_LOCATIONS: 'ut1',
                        CANARY_LOCATION: 'uc1',
                ],
                pipeline: pipeline,
                stageCommands: stageCommands
        ]

        binding.setVariable('env', env)
        binding.setVariable('stageCommands', stageCommands)

        scriptUnderSpec = loadScript('vars/containerPipeline.groovy')

        helper.registerAllowedMethod('containerCdPipeline', [Map], null)
        helper.registerAllowedMethod('containerCiPipeline', [Map], null)
        helper.registerAllowedMethod('error', [String], { throw new RuntimeException(it) })
    }

    def 'containerCdPipeline called when in prod'() {
        given:
        env = [TARGET_ENV: 'prod']
        binding.setVariable('env', env)

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify('containerCdPipeline', configMap)
        verify(0,'containerCiPipeline', configMap)
    }

    def 'containerCiPipeline called if not in prod'() {
        given: 'any environment'

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify('containerCiPipeline', configMap)
        verify(0,'containerCdPipeline', configMap)
    }

    def 'containerCdPipeline called when cd = true'() {
        given:
        configMap.cd = true

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify('containerCdPipeline', configMap)
    }

    def 'containerCiPipeline is called when cd = false'() {
        given:
        configMap.cd = false

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify('containerCiPipeline', configMap)
    }

    def 'containerPipeline validation fails when there are no stageCommands'() {
        given:
        configMap.remove('stageCommands')

        when:
        scriptUnderSpec.call(configMap)

        then:
        thrown RuntimeException
        binding.getVariable('currentBuild').result == 'ABORTED'
    }

    def 'containerPipeline validation fails when missing a required stageCommand'() {
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
        binding.getVariable('currentBuild').result == 'ABORTED'
    }

    def 'containerPipeline validation fails when missing required env variable'() {
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

    def 'containerPipeline validation fails when skipCanaryStage is true and CANARY_LOCATION env is missing'() {
        given:
        configMap.get('environment').remove('CANARY_LOCATION')
        configMap.get('pipeline').put('skipCanaryStage', 'true')

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify('containerCiPipeline', configMap)
    }

    def 'Create containerPipeline and skipCanaryStage by absence of canaryDeploy stageCommand'() {
        given: 'canaryDeploy stageCommand is not present'
        configMap.get('environment').remove('CANARY_LOCATION')
        configMap.get('stageCommands').remove('canaryDeploy')

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify('containerCiPipeline', configMap)
    }

    def 'Create containerPipeline with no facts or secrets'() {
        given: 'no files or secrets exist'
        configMap.get('pipeline').remove('facts')
        configMap.get('pipeline').remove('secrets')

        when:
        scriptUnderSpec.call(configMap)

        then: 'verify facts and secrets are empty arrays'
        verify('containerCiPipeline', configMap)
        configMap.pipeline.secrets.empty
        configMap.pipeline.facts.empty

    }

    def 'containerPipeline validation succeeds when no slack* fields specified'() {
        given: 'pipeline block has no slack* fields'
        configMap.get('pipeline').remove('slackWorkspace')
        configMap.get('pipeline').remove('slackChannel')
        configMap.get('pipeline').remove('slackCredentialId')

        when:
        scriptUnderSpec.call(configMap)

        then:
        verify('containerCiPipeline', configMap)
    }

    def 'containerPipeline validation fails when one slack* fields missing'() {
        given: 'pipeline block is missing one slack* field'
        configMap.get('pipeline').remove('slackWorkspace')

        when:
        scriptUnderSpec.call(configMap)

        then:
        Exception e = thrown()
        // we threw an exception
        e != null
        // the exception identified the stage missing the command
        e.message.contains("'slackWorkspace' is required")
        // the build was aborted (not failed, etc.)
        binding.getVariable('currentBuild').result == 'ABORTED'
    }

    def 'containerPipeline validation fails TARGET_ENV not defined'() {
        given: 'pipeline block is missing one slack* field'
        env = [TRGET_ENV: 'dev']
        binding.setVariable('env', env)

        when:
        scriptUnderSpec.call(configMap)

        then:
        Exception e = thrown()
        // we threw an exception
        e != null
        // the exception identified the stage missing the command
        e.message.contains("'TARGET_ENV' is required")
        // the build was aborted (not failed, etc.)
        binding.getVariable('currentBuild').result == 'ABORTED'
    }


}
