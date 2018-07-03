package com.makara.jenkins

import spock.lang.Specification

/**
 * Spock Specification with support for testing Jenkins pipeline functionality
 */
class JenkinsSpecification extends Specification {

    @Delegate
    PipelineSupport pipelineSupport

    def setup() {
        pipelineSupport = new PipelineSupport()
        pipelineSupport.setUp()
    }
}
