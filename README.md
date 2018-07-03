This project provides an opinionated Jenkins build pipeline described in [my blog](http://stevetarver.github.io/).

Each client `Jenkinsfile` points to a `jenkins-pipe` versioned release branch:

```
@Library('jenkins-pipe@release-1.0.0') _
```

This versioning strategy reflects interface changes, not functionality. New functionality is developed in the `master` branch and merged into the latest `release-*` branch in a non-breaking way. If the interface must change, a new `release-*` branch is created and projects can upgrade on their timeline.

See the release branch README.md for instructions on using that version.

See:

* the project Releases tab for release history
* the release branch `README.md` for instructions on using that version
* the `CHANGELOG.md` for changes and migration notes

## Add jenkins-pipe to Jenkins

This library is configured in Jenkins global configuration so it is automatically available to all projects.

1. Open Configure Jenkins
2. Add `jenkins-pipe` to Global Pipeline Libraries
3. Default version: master

## Your Jenkinsfile

Each project `Jenkinsfile` provides configuration to adjust pipeline behavior to suit that project. This is a standard `release-1.0.0` Jenkinsfile for a maven project:

```groovy
@Library('jenkins-pipe@release-1.0.0') _

containerPipeline([
    environment: [
        CANARY_LOCATION: 'us-east',
        PROD_LOCATIONS: 'us-west'
    ],
    pipeline: [
        dockerGroup: 'stevetarver',
        slackChannel: 'tarver-build',
        slackCredentialId: 'tarver-build-slack-token',
    ],
    stageCommands: [
        build: "./jenkins/scripts/build.sh",
        test: "./jenkins/scripts/test.sh",
        package: "./jenkins/scripts/package.sh",
        deploy: "./jenkins/scripts/deploy.sh",
        integrationTest: "./integration-test/run.sh -q -b -t integration -e lb1",
        canaryDeploy: "./jenkins/scripts/canary_deploy.sh",
        canaryTest: "./integration-test/run.sh -q -b -t smoke -e ${CANARY_LOCATION}",
        canaryRollback: "./jenkins/scripts/canary_rollback.sh",
        prodDeploy: "./jenkins/scripts/prod_deploy.sh",
        prodTest: "./integration-test/run.sh -q -b -t smoke -e ${PROD_LOCATIONS}",
    ],
    stages: [
        test: [
            unitTestResults: 'target/junit.xml',
            codeCoverageHtmlDir: 'target/htmlcov',
        ]
    ],
])
```

### environment

This config block defines variables exposed in your build environment as shell environment variables. 

* CANARY_LOCATION: the datacenter you deploy a canary in for testing the release during a prod deploy
* PROD_LOCATIONS: all datacenter targets for a prod deploy

The pipeline exposes exposes these environment variables as well:

```bash
#    BUILD_TIMESTAMP=20170823232923
#
#    DOCKER_BUILD_IMAGE_NAME=stevetarver/ms-ref-python-falcon-master
#    DOCKER_BUILD_IMAGE_NAMETAG=stevetarver/ms-ref-python-falcon-master:20171107231806845
#    DOCKER_BUILD_IMAGE_NAMETAG_LATEST=stevetarver/ms-ref-python-falcon-master:latest
#    DOCKER_DEPLOY_IMAGE_NAMETAG=stevetarver/ms-ref-python-falcon-master:20171107231806845
#    DOCKER_GROUP=stevetarver
#    DOCKER_IMAGE_BASENAME=stevetarver/ms-ref-python-falcon
#    DOCKER_PROJECT=ms-ref-python-falcon
#
#    GIT_BRANCH=master
#    GIT_COMMIT=9ee5cdf4a78e9f67b34172c5a136e44e9db8a769
#    GIT_ORG_REPO_NAME=stevetarver/ms-ref-python-falcon
#    GIT_REPO_NAME=pl-cloud-build
#    GIT_REPO_URL=https://github.com/stevetarver/ms-ref-python-falcon.git
```

### pipeline

This config block configures pipeline operation to suit your project:

* dockerGroup: the docker registry group your docker image lives in
* slackChannel: the slack channel that build/deploy notifications are posted in
* slackCredentialId: the Jenkins credentialsId holding slack creds allowing us to post to Slack

### stageCommands

This config block defines your project's implementation of each stage. For robust project management tools like `maven` and `gradle`, you can simply list the commands to run. When you need finer grained control, you can implement that in a bash script. Scripts reside in `jenkins/scripts` by convention.

### stages

This config block allows configuration for each stage.

* `test`
    * `unitTestResults`: a file glob capturing all xml junit4 test results produced
    * `codeCoverageHtmlDir`: a path to unit test results html report

## TODO

* Currently, pulling this repo by tag is broken in Jenkins. E.g. `@Library('pl-jenkins-lib@1.0.0') _` does not work. As a work around, we pull by branch name like `release-1.0.0`. Watch this bug and change our strategy when fixed.
