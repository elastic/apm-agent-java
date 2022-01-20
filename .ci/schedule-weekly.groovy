@Library('apm@current') _

pipeline {
  agent none
  environment {
    NOTIFY_TO = credentials('notify-to')
    PIPELINE_LOG_LEVEL='INFO'
  }
  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '20'))
    timestamps()
    ansiColor('xterm')
    disableResume()
    durabilityHint('PERFORMANCE_OPTIMIZED')
  }
  triggers {
    cron('H H(1-4) * * 1')
  }
  stages {
    stage('Agent weekly exhaustive test') {
      steps {
        build(job: 'apm-agent-java/apm-agent-java-mbp/master',
          parameters: [
            booleanParam(name: 'jdk_compatibility_ci', value: true),
            booleanParam(name: 'end_to_end_tests_ci', value: true),
            booleanParam(name: 'agent_integration_tests_ci', value: true),
          ],
          propagate: false,
          wait: false
        )
      }
    }
  }
  post {
    cleanup {
      notifyBuildResult()
    }
  }
}
