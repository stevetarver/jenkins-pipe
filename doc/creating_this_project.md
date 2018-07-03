General notes on creating and configuring this project.

## Add groovy linting

[CodeNarc](http://codenarc.sourceforge.net/) provides a combination of PMD, Checkstyle and basic linting for groovy projects. Here's how to add a starter configuration to your project.

1. Add the [CodeNarcPlugin](https://docs.gradle.org/current/userguide/codenarc_plugin.html): In `build.gradle`, add `apply plugin: 'codenarc'`

2. Configure the [CodeNarcExtension](https://docs.gradle.org/current/dsl/org.gradle.api.plugins.quality.CodeNarcExtension.html): All plugin configuration is done in `build.gradle`.
   This configuration separates `main` and `test` source set concerns:

    ```
   codenarcMain {
       configFile = rootProject.file("jenkins/codenarc/ruleset-main.groovy")
       ignoreFailures = false

       maxPriority1Violations = 0
       maxPriority2Violations = 10
       maxPriority3Violations = 20
   }

   codenarcTest {
       configFile = rootProject.file("jenkins/codenarc/ruleset-test.groovy")
       ignoreFailures = true

       maxPriority1Violations = 0
       maxPriority2Violations = 10
       maxPriority3Violations = 20
   }
    ```

3. Configure rulesets: CodeNarc can have multiple configuration files so we are storing all in the standard build tool directory; in `jenkins/codenarc`. It is interesting to start with the 'All RuleSet' and work through what CodeNarc thinks you should be doing versus what you think is important. Copy the contents of the [All RuleSet](http://codenarc.sourceforge.net/StarterRuleSet-AllRulesByCategory.groovy.txt) to `ruleset-main.groovy` and `ruleset-test.groovy`.

4. Run CodeNarc: `gradle check`.

5. View the report: Open the html reports under: `build/reports/codenarc/`

