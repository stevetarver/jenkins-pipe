package com.makara.jenkins

import com.lesfurets.jenkins.unit.BasePipelineTest
import com.lesfurets.jenkins.unit.MethodCall

/**
 * Adds support for testing Jenkins Pipelines and shared library code that depends on
 * the Jenkins environment.
 */
class PipelineSupport extends BasePipelineTest {

    /**
     * Verify method was called N times. Pass a single args Object to verify the same args for each method invocation or
     * pass an args Object array so each invocation can be verified with different arguments. args should be a list if
     * multiple arguments are expected to be passed to method.
     *
     * @param count
     * @param method
     * @param args
     */
    void verify(int count=1, String method, Object... args) {
        def methodCalls = helper.callStack.findAll { it.methodName == method }

        verifyCallCount(count, method, methodCalls)

        methodCalls.eachWithIndex { call, i ->
            def actualArgs = call.args
            def actualArgsCount = actualArgs.size()
            def expectedArgs = (args.size() > 1 ? args[i] : args[0])
            def expectedArgsCount = 1

            if(Collection.isAssignableFrom(expectedArgs.getClass()) || expectedArgs.getClass().isArray()) {
                expectedArgsCount = expectedArgs.size()
            } else {
                actualArgs = actualArgs[0]
            }

            assert actualArgsCount == expectedArgsCount: "Invocation $i of '$method'"
            assert expectedArgs == actualArgs : "Invocation $i of '$method'"
        }
    }

    /**
     * Verify method was called N times. Pass one verifier to verify the same args for each method invocation or
     * pass a verifier array so each invocation can be verified with a different Closure. The verifier Closure gives the caller the ability to inspect
     * the args and return true if they are valid, false otherwise.
     *
     * @param count
     * @param method
     * @param verifiers
     */
    void verify(int count=1, String method, Closure<Boolean>... verifiers) {
        def methodCalls = helper.callStack.findAll { it.methodName == method }

        verifyCallCount(count, method, methodCalls)

        methodCalls.eachWithIndex { call, i ->
            def verifier = verifiers.size() > 1 ? verifiers[i] : verifiers[0]
            assert verifier(*call.args) : "Failed verification at invocation $i of '$method'"
        }
    }

    /**
     * Verify method was called N times without checking the arguments.
     *
     * @param method
     */
    void verify(int count= 1, String method) {
        verify(count, method) { true }
    }

    private verifyCallCount(int expectedCount, String method, List<MethodCall> methodCalls) {
        assert methodCalls?.size() == expectedCount: "Wanted $expectedCount but received ${methodCalls?.size()} invocation(s) of '$method'"
    }
}
