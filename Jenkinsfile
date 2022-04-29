#!/usr/bin/env groovy

@Library('apm@current') _

pipeline {
  agent { label 'linux && immutable' }
  environment {
    REPO = 'apm-agent-java'
    BASE_DIR = "src/github.com/elastic/${env.REPO}"
    NOTIFY_TO = credentials('notify-to')
    JOB_GCS_BUCKET = credentials('gcs-bucket')
    DOCKERHUB_SECRET = 'secret/apm-team/ci/elastic-observability-dockerhub'
    ELASTIC_DOCKER_SECRET = 'secret/apm-team/ci/docker-registry/prod'
    CODECOV_SECRET = 'secret/apm-team/ci/apm-agent-java-codecov'
    GITHUB_CHECK_ITS_NAME = 'End-To-End Integration Tests'
    ITS_PIPELINE = 'apm-integration-tests-selector-mbp/main'
    MAVEN_CONFIG = '-Dmaven.repo.local=.m2'
    OPBEANS_REPO = 'opbeans-java'
    JAVA_VERSION = "${params.JAVA_VERSION}"
    JOB_GCS_BUCKET_STASH = 'apm-ci-temp'
    JOB_GCS_CREDENTIALS = 'apm-ci-gcs-plugin'
  }
  options {
    timeout(time: 90, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '20', daysToKeepStr: '30'))
    timestamps()
    ansiColor('xterm')
    disableResume()
    durabilityHint('PERFORMANCE_OPTIMIZED')
    rateLimitBuilds(throttle: [count: 60, durationName: 'hour', userBoost: true])
    quietPeriod(10)
  }
  triggers {
    issueCommentTrigger("(${obltGitHubComments()}|^run (jdk compatibility|benchmark|integration|end-to-end|windows) tests)")
  }
  parameters {
    string(name: 'JAVA_VERSION', defaultValue: 'java11', description: 'Java version to build & test')
    string(name: 'MAVEN_CONFIG', defaultValue: '-V -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dhttps.protocols=TLSv1.2 -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.wagon.httpconnectionManager.ttlSeconds=25', description: 'Additional maven options.')

    // Note about GH checks and optional steps
    //
    // All the steps executed by default are included in GH checks
    // All the mandatory steps not included by default need to be added in GH branch protection rules.

    // enabled by default, required for merge through default GH check
    booleanParam(name: 'test_ci', defaultValue: true, description: 'Enable Unit tests')

    // disabled by default, but required for merge, there are two GH checks:
    // - Non-Application Server integration tests
    // - Application Server integration tests
    // opt-in with 'ci:agent-integration'
    booleanParam(name: 'agent_integration_tests_ci', defaultValue: false, description: 'Enable Agent Integration tests')

    // disabled by default, but required for merge, GH check name is ${GITHUB_CHECK_ITS_NAME}
    // opt-in with 'ci:end-to-end' tag on PR
    booleanParam(name: 'end_to_end_tests_ci', defaultValue: false, description: 'Enable APM End-to-End tests')

    // disabled by default, not required for merge
    booleanParam(name: 'bench_ci', defaultValue: false, description: 'Enable benchmarks')

    // disabled by default, not required for merge
    // opt-in with 'ci:jdk-compatibility' tag on PR
    booleanParam(name: 'jdk_compatibility_ci', defaultValue: false, description: 'Enable JDK compatibility tests')

    // disabled by default, not required for merge
    // opt-in with 'ci:windows' tag on PR
    booleanParam(name: 'windows_ci', defaultValue: false, description: 'Enable Windows build & tests')
  }
  stages {
    stage('Checkout') {
      options { skipDefaultCheckout() }
      steps {
        pipelineManager([ cancelPreviousRunningBuilds: [ when: 'PR' ] ])
        deleteDir()
        gitCheckout(basedir: "${BASE_DIR}", githubNotifyFirstTimeContributor: true, shallow: false,
                    reference: '/var/lib/jenkins/.git-references/apm-agent-java.git')
        stash allowEmpty: true, name: 'source', useDefaultExcludes: false
        script {
          dir("${BASE_DIR}"){
            // Skip all the stages except docs for PR's with asciidoc and md changes only
            env.ONLY_DOCS = isGitRegionMatch(patterns: [ '.*\\.(asciidoc|md)' ], shouldMatchAll: true)
            // Prepare the env variables for the benchmark results
            env.COMMIT_ISO_8601 = sh(script: 'git log -1 -s --format=%cI', returnStdout: true).trim()
            env.NOW_ISO_8601 = sh(script: 'date -u "+%Y-%m-%dT%H%M%SZ"', returnStdout: true).trim()
            env.RESULT_FILE = "apm-agent-benchmark-results-${env.COMMIT_ISO_8601}.json"
            env.BULK_UPLOAD_FILE = "apm-agent-bulk-${env.NOW_ISO_8601}.json"
          }
        }
      }
    }
    stage('Builds') {
      options { skipDefaultCheckout() }
      when {
        // Tags are not required to be built/tested.
        not {
          tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
        }
      }
      environment {
        HOME = "${env.WORKSPACE}"
        JAVA_HOME = "${env.HUDSON_HOME}/.java/${env.JAVA_VERSION}"
        MAVEN_CONFIG = "${params.MAVEN_CONFIG} ${env.MAVEN_CONFIG}"
      }
      stages {

        stage('experiment-gh-notify') {
          steps {
            withGithubNotify(context: "${STAGE_NAME}") {

            }
          }
        }
        stage('experiment-skipped') {
          when {
            beforeAgent true
            expression { return false }
          }
          steps {
            withGithubNotify(context: "${STAGE_NAME}") {

            }
          }
          post {
            always {
              steps {
                echo "always executed even if skipped ?"
                // if yes, then we just have to detect when it's skipped
              }
            }
          }
        }
      }
    }
  }
  post {
    cleanup {
      notifyBuildResult(analyzeFlakey: !isTag(), flakyReportIdx: 'reporter-apm-agent-java-apm-agent-java-main', flakyDisableGHIssueCreation: true)
    }
  }
}

def reportTestResults(){
  junit(allowEmptyResults: true,
    keepLongStdio: true,
    testResults: "${BASE_DIR}/**/junit-*.xml,${BASE_DIR}/**/TEST-*.xml")

  // disable codecov for now as it's not supported for windows
  //  codecov(repo: env.REPO, basedir: "${BASE_DIR}", secret: "${CODECOV_SECRET}")
}
