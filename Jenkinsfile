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
    MAVEN_CONFIG = '-Dmaven.repo.local=.m2'
    JAVA_VERSION = "${params.JAVA_VERSION}"
    JOB_GCS_BUCKET_STASH = 'apm-ci-temp'
    JOB_GCS_CREDENTIALS = 'apm-ci-gcs-plugin'
  }
  options {
    timeout(time: 120, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '20', daysToKeepStr: '30'))
    timestamps()
    ansiColor('xterm')
    disableResume()
    durabilityHint('PERFORMANCE_OPTIMIZED')
    rateLimitBuilds(throttle: [count: 60, durationName: 'hour', userBoost: true])
    quietPeriod(10)
  }
  triggers {
    issueCommentTrigger("(${obltGitHubComments()}|^run benchmark tests)")
  }
  parameters {
    string(name: 'JAVA_VERSION', defaultValue: 'jdk17', description: 'Java version to build & test')
    string(name: 'MAVEN_CONFIG', defaultValue: '-V -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dhttps.protocols=TLSv1.2 -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.wagon.httpconnectionManager.ttlSeconds=25', description: 'Additional maven options.')

    // Note about GH checks and optional steps
    //
    // All the steps executed by default are included in GH checks
    // All the mandatory steps not included by default need to be added in GH branch protection rules.

    // disabled by default, not required for merge
    // opt-in with 'ci:benchmarks' tag on PR
    booleanParam(name: 'bench_ci', defaultValue: false, description: 'Enable benchmarks')

  }
  stages {
    stage('Checkout') {
      options { skipDefaultCheckout() }
      steps {
        pipelineManager([ cancelPreviousRunningBuilds: [ when: 'PR' ] ])
        deleteDir()
        // reference repo causes issues while running on Windows with the git-commit-id-maven-plugin
        gitCheckout(basedir: "${BASE_DIR}", githubNotifyFirstTimeContributor: true, shallow: false)
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

            if (env.ONLY_DOCS == "true") {
              // those GH checks are required, and docs build skips them we artificially make them as OK
              githubCheck(name: "Application Server integration tests", status: 'neutral');
              githubCheck(name: "Non-Application Server integration tests", status: 'neutral');
            }
          }
        }
      }
    }
    stage('Builds') {
      options { skipDefaultCheckout() }
      environment {
        HOME = "${env.WORKSPACE}"
        JAVA_HOME = "${env.HUDSON_HOME}/.java/${env.JAVA_VERSION}"
        MAVEN_CONFIG = "${params.MAVEN_CONFIG} ${env.MAVEN_CONFIG}"
      }
      stages {
        /**
         * Build on a linux environment.
         */
        stage('Build') {
          when {
            beforeAgent true
            expression { return env.ONLY_DOCS == "false" }
          }
          environment {
            PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
          }
          steps {
            withGithubNotify(context: "${STAGE_NAME}", tab: 'artifacts') {
              deleteDir()
              unstash 'source'
              dir("${BASE_DIR}"){
                withOtelEnv() {
                  retryWithSleep(retries: 5, seconds: 10) {
                    sh label: 'mvn install', script: "./mvnw clean install -DskipTests=true -Dmaven.javadoc.skip=true"
                  }
                }
              }
              stashV2(name: 'build', bucket: "${JOB_GCS_BUCKET_STASH}", credentialsId: "${JOB_GCS_CREDENTIALS}")
            }
          }
        }
        stage('Benchmarks') {
          agent { label 'microbenchmarks-pool' }
          options { skipDefaultCheckout() }
          environment {
            NO_BUILD = "true"
            PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
          }
          when {
            beforeAgent true
            allOf {
              expression { return env.ONLY_DOCS == "false" }
              anyOf {
                branch 'main'
                expression { return env.GITHUB_COMMENT?.contains('benchmark tests') }
                expression { matchesPrLabel(label: 'ci:benchmarks') }
                expression { return params.bench_ci }
              }
            }
          }
          steps {
            withGithubNotify(context: "${STAGE_NAME}", tab: 'artifacts') {
              deleteDir()
              unstashV2(name: 'build', bucket: "${JOB_GCS_BUCKET_STASH}", credentialsId: "${JOB_GCS_CREDENTIALS}")
              dir("${BASE_DIR}"){
                withOtelEnv() {
                  sh './scripts/jenkins/run-benchmarks.sh'
                }
              }
            }
          }
          post {
            cleanup {
              deleteDir()
            }
            always {
              archiveArtifacts(allowEmptyArchive: true,
                artifacts: "${BASE_DIR}/${RESULT_FILE}",
                onlyIfSuccessful: false)
              sendBenchmarks(file: "${BASE_DIR}/${BULK_UPLOAD_FILE}", index: "benchmark-java")
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
