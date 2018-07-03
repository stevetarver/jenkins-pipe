package com.makara.jenkins

import spock.lang.Specification

import static Utils.toConfigMap

class UtilsSpec extends Specification {

    def 'Can convert a closure to a config map'() {
        given: 'a closure with key-value pairs'
        def closure = {
            item1 = 1
            item2 = 2
        }

        when: 'converted to a config map'
        def configMap = toConfigMap(closure)

        then: 'the key-value pairs in the closure exist in the config map'
        configMap.item1 == 1
        configMap.item2 == 2
    }

    def 'Can merge defaults and overrides to produce a config map'() {
        given: 'a map of defaults and a closure containing overrides'
        def defaults = [item1: 1, item2: 2, item3: 3]
        def overrides = {
            item1 = 100
            item2 = 25
        }

        when: 'converted to a config map'
        def configMap = toConfigMap(defaults, overrides)

        then: 'the configMap is properly merged, defaults are unchanged, and configMap is not the defaults object'
        configMap.item1 == 100
        configMap.item2 == 25
        configMap.item3 == 3
        defaults.item1 == 1
        defaults.item2 == 2
        defaults.item3 == 3
        !configMap.is(defaults)
    }

    def 'Does not fail with an empty override closure'() {
        given: 'a map of defaults and an empty override closure'
        def defaults = [item1: 1, item2: 2, item3: 3]
        def overrides = { }

        when: 'converted to a config map'
        def configMap = toConfigMap(defaults, overrides)

        then: 'the empty override closure does not cause problems'
        configMap.item1 == 1
        configMap.item2 == 2
        configMap.item3 == 3
        defaults.item1 == 1
        defaults.item2 == 2
        defaults.item3 == 3
        !configMap.is(defaults)
    }
}
