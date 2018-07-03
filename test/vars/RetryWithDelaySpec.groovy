package vars

import com.makara.jenkins.JenkinsSpecification

class RetryWithDelaySpec extends JenkinsSpecification {
    def scriptUnderSpec

    def setup() {
        scriptUnderSpec = loadScript("vars/retryWithDelay.groovy")

        helper.registerAllowedMethod("retry", [Integer.class, Closure.class], null)
    }

    def 'Call retry with defaults'() {
        given:

        when:
        scriptUnderSpec.call({ println "test" })

        then:
        verify("retry", { count, closure -> count == 12 })
    }

    def 'Call retry with a count and delay'() {
        given:

        when:
        scriptUnderSpec.call(5, 10, { println "test" })

        then:
        verify("retry", { count, closure -> count == 5 })
    }

    def 'Call retry with closure that throws exception'() {
        given:

        when:
        scriptUnderSpec.call(5, 10, { throw new RuntimeException("test") })

        then:
        RuntimeException e = thrown()
        e.message == "test"
        verify("retry", { count, closure -> count == 5 })
    }
}
