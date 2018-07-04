package com.makara.jenkins

/**
 * General utilities for vars
 */
class Utils {

    static toConfigMap(defaults, Closure body) {
        def config = defaults.clone()
        if (body) {
            body.resolveStrategy = Closure.DELEGATE_FIRST
            body.delegate = config
            body()
        }
        return config
    }

    static toConfigMap(Closure body) {
        return toConfigMap([:], body)
    }

    // NOTE: everything in env is a string - if you assign null, you will get "null"
    static isStringNullOrEmpty(String str) {
        return (null == str || 'null' == str || '' == str?.trim())
    }

    static stringHasContent(String str) {
        return !isStringNullOrEmpty(str)
    }
}
