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
    stage('Run compatibility tests') {
      steps {
        build(job: 'apm-agent-java/apm-agent-java-mbp/master',
          parameters: [
            booleanParam(name: 'Run_As_Master_Branch', value: true),
            booleanParam(name: 'compatibility_ci', value: true)
          ],
          propagate: false,
          wait: false
        )
      }
    }
    stage('Run windows tests') {
      steps {
        build(job: 'apm-agent-java/apm-agent-java-mbp/master',
          parameters: [
            booleanParam(name: 'windows_ci', value: true),
            booleanParam(name: 'Run_As_Master_Branch', value: false),
            booleanParam(name: 'smoketests_ci', value: false),
            booleanParam(name: 'bench_ci', value: false),
            booleanParam(name: 'test_ci', value: false)
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
