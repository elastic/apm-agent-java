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
    issueCommentTrigger("(${obltGitHubComments()}|^run (jdk compatibility|benchmark|integration|windows) tests)")
  }
  parameters {
    string(name: 'JAVA_VERSION', defaultValue: 'jdk17', description: 'Java version to build & test')
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

    // disabled by default, not required for merge
    // opt-in with 'ci:benchmarks' tag on PR
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
              // prepare m2 repository with the existing dependencies
              whenTrue(fileExists('/var/lib/jenkins/.m2/repository')) {
                sh label: 'Prepare .m2 cached folder', returnStatus: true, script: 'cp -Rf /var/lib/jenkins/.m2/repository ${HOME}/.m2'
                sh label: 'Size .m2', returnStatus: true, script: 'du -hs .m2'
              }
              dir("${BASE_DIR}"){
                withOtelEnv() {
                  retryWithSleep(retries: 5, seconds: 10) {
                    sh label: 'mvn install', script: "./mvnw clean install -DskipTests=true -Dmaven.javadoc.skip=true"
                  }
                  sh label: 'mvn license', script: "./mvnw org.codehaus.mojo:license-maven-plugin:aggregate-third-party-report -Dlicense.excludedGroups=^co\\.elastic\\."
                }
              }
              stashV2(name: 'build', bucket: "${JOB_GCS_BUCKET_STASH}", credentialsId: "${JOB_GCS_CREDENTIALS}")
              stash(allowEmpty: true, name: 'snapshoty', includes: "${BASE_DIR}/.ci/snapshoty.yml,${BASE_DIR}/elastic-apm-agent/target/*,${BASE_DIR}/apm-agent-attach/target/*,${BASE_DIR}/apm-agent-attach-cli/target/*,${BASE_DIR}/apm-agent-api/target/*", useDefaultExcludes: false)
              archiveArtifacts allowEmptyArchive: true,
                artifacts: "\
                  ${BASE_DIR}/elastic-apm-agent/target/elastic-apm-agent-*.jar,\
                  ${BASE_DIR}/elastic-apm-agent/target/elastic-apm-java-aws-lambda-layer-*.zip,\
                  ${BASE_DIR}/apm-agent-attach/target/apm-agent-attach-*.jar,\
                  ${BASE_DIR}/apm-agent-attach-cli/target/apm-agent-attach-cli-*.jar,\
                  ${BASE_DIR}/apm-agent-api/target/apm-agent-api-*.jar,\
                  ${BASE_DIR}/target/site/aggregate-third-party-report.html",
                onlyIfSuccessful: true
            }
          }
        }
        stage('Tests') {
          when {
            beforeAgent true
            expression { return env.ONLY_DOCS == "false" }
          }
          failFast true
          parallel {
            /**
             * Run only unit tests
             */
            stage('Unit Tests') {
              options { skipDefaultCheckout() }
              when {
                beforeAgent true
                expression { return params.test_ci }
              }
              environment {
                PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
              }
              steps {
                withGithubNotify(context: "${STAGE_NAME}", tab: 'tests') {
                  deleteDir()
                  unstashV2(name: 'build', bucket: "${JOB_GCS_BUCKET_STASH}", credentialsId: "${JOB_GCS_CREDENTIALS}")
                  dir("${BASE_DIR}") {
                    withOtelEnv() {
                      sh label: 'mvn test', script: './mvnw test'
                    }
                  }
                }
              }
              post {
                always {
                  reportTestResults()
                }
              }
            }
            /** *
             * Build & Test on Windows environment
             */
            stage('Build & Test Windows') {
              agent { label 'windows-2019-docker-immutable' }
              options { skipDefaultCheckout() }
              when {
                beforeAgent true
                allOf {
                  expression { return params.test_ci }
                  anyOf {
                    expression { return params.windows_ci }
                    expression { return env.GITHUB_COMMENT?.contains('windows tests') }
                    expression { matchesPrLabel(label: 'ci:windows') }
                  }
                }
              }
              environment {
                JAVA_HOME = "C:\\Users\\jenkins\\.java\\${env.JAVA_VERSION}"
                PATH = "${env.JAVA_HOME}\\bin;${env.PATH}"
              }
              steps {
                withGithubNotify(context: 'Build & Test Windows') {
                  deleteDir()
                  unstash 'source'
                  dir("${BASE_DIR}") {
                    echo "${env.PATH}"
                    retryWithSleep(retries: 5, seconds: 10) {
                      bat label: 'mvn clean install', script: "mvnw clean install -DskipTests=true -Dmaven.javadoc.skip=true -Dmaven.gitcommitid.skip=true"
                    }
                    bat label: 'mvn test', script: "mvnw test"
                  }
                }
              }
              post {
                always {
                  reportTestResults()
                }
              }
            }
            stage('Non-Application Server integration tests') {
              agent { label 'linux && immutable' }
              options { skipDefaultCheckout() }
              when {
                beforeAgent true
                anyOf {
                  expression { return params.agent_integration_tests_ci }
                  expression { return env.GITHUB_COMMENT?.contains('integration tests') }
                  expression { matchesPrLabel(label: 'ci:agent-integration') }
                  expression { return env.CHANGE_ID != null && !pullRequest.draft }
                  not { changeRequest() }
                }
              }
              environment {
                PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
              }
              steps {
                withGithubNotify(context: "${STAGE_NAME}", tab: 'tests') {
                  deleteDir()
                  unstashV2(name: 'build', bucket: "${JOB_GCS_BUCKET_STASH}", credentialsId: "${JOB_GCS_CREDENTIALS}")
                  dir("${BASE_DIR}") {
                    withOtelEnv() {
                      sh './mvnw -q -P ci-non-application-server-integration-tests verify'
                    }
                  }
                }
              }
              post {
                always {
                  reportTestResults()
                }
              }
            }
            stage('Application Server integration tests') {
              agent { label 'linux && immutable' }
              options { skipDefaultCheckout() }
              when {
                beforeAgent true
                anyOf {
                  expression { return params.agent_integration_tests_ci }
                  expression { return env.GITHUB_COMMENT?.contains('integration tests') }
                  expression { matchesPrLabel(label: 'ci:agent-integration') }
                  expression { return env.CHANGE_ID != null && !pullRequest.draft }
                  not { changeRequest() }
                }
              }
              environment {
                PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
              }
              steps {
                withGithubNotify(context: "${STAGE_NAME}", tab: 'tests') {
                  deleteDir()
                  unstashV2(name: 'build', bucket: "${JOB_GCS_BUCKET_STASH}", credentialsId: "${JOB_GCS_CREDENTIALS}")
                  dir("${BASE_DIR}") {
                    withOtelEnv() {
                      sh './mvnw -q -P ci-application-server-integration-tests verify'
                    }
                  }
                }
              }
              post {
                always {
                  reportTestResults()
                }
              }
            }
            /**
             * Run the benchmarks and store the results on ES.
             * The result JSON files are also archive into Jenkins.
             */
            stage('Benchmarks') {
              agent { label 'microbenchmarks-pool' }
              options { skipDefaultCheckout() }
              environment {
                NO_BUILD = "true"
                PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
              }
              when {
                beforeAgent true
                anyOf {
                  branch 'main'
                  expression { return env.GITHUB_COMMENT?.contains('benchmark tests') }
                  expression { matchesPrLabel(label: 'ci:benchmarks') }
                  expression { return params.bench_ci }
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
            /**
             * Build javadoc
             */
            stage('Javadoc') {
              agent { label 'linux && immutable' }
              options { skipDefaultCheckout() }
              environment {
                PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
              }
              steps {
                withGithubNotify(context: "${STAGE_NAME}") {
                  deleteDir()
                  unstashV2(name: 'build', bucket: "${JOB_GCS_BUCKET_STASH}", credentialsId: "${JOB_GCS_CREDENTIALS}")
                  dir("${BASE_DIR}"){
                    withOtelEnv() {
                      sh """#!/bin/bash
                      set -euxo pipefail
                      ./mvnw compile javadoc:javadoc
                      """
                    }
                  }
                }
              }
            }
          }
        }
        stage('JDK Compatibility Tests') {
          options { skipDefaultCheckout() }
          when {
            beforeAgent true
            allOf {
              expression { return env.ONLY_DOCS == "false" }
              anyOf {
                expression { return params.jdk_compatibility_ci }
                expression { return env.GITHUB_COMMENT?.contains('jdk compatibility tests') }
                expression { matchesPrLabel(label: 'ci:jdk-compatibility') }
              }
            }
          }
          environment {
            PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
          }
          matrix {
            agent { label 'linux && immutable' }
            axes {
              axis {
                // the list of support java versions can be found in the infra repo (ansible/roles/java/defaults/main.yml)
                name 'JDK_VERSION'
                // 'openjdk18'  disabled for now see https://github.com/elastic/apm-agent-java/issues/2328
                values 'openjdk17'
              }
            }
            stages {
              stage('JDK Unit Tests') {
                steps {
                  withGithubNotify(context: "${STAGE_NAME} ${JDK_VERSION}", tab: 'tests') {
                    deleteDir()
                    unstashV2(name: 'build', bucket: "${JOB_GCS_BUCKET_STASH}", credentialsId: "${JOB_GCS_CREDENTIALS}")
                    dir("${BASE_DIR}"){
                      withOtelEnv() {
                        sh(label: "./mvnw test for ${JDK_VERSION}", script: './mvnw test')
                      }
                    }
                  }
                }
                post {
                  always {
                    reportTestResults()
                  }
                }
              }
            }
          }
        }
        stage('Publish snapshots') {
          agent { label 'linux && immutable' }
          options { skipDefaultCheckout() }
          environment {
            BUCKET_NAME = 'oblt-artifacts'
            DOCKER_REGISTRY = 'docker.elastic.co'
            DOCKER_REGISTRY_SECRET = 'secret/observability-team/ci/docker-registry/prod'
            GCS_ACCOUNT_SECRET = 'secret/observability-team/ci/snapshoty'
          }
          when { branch 'main' }
          steps {
            withGithubNotify(context: 'Publish snapshot packages') {
              deleteDir()
              unstash(name: 'snapshoty')
              dir(env.BASE_DIR) {
                snapshoty(
                  bucket: env.BUCKET_NAME,
                  gcsAccountSecret: env.GCS_ACCOUNT_SECRET,
                  dockerRegistry: env.DOCKER_REGISTRY,
                  dockerSecret: env.DOCKER_REGISTRY_SECRET
                )
              }
            }
          }
        }
      }
    }
    stage('Releases') {
      when {
        branch 'main'
      }
      stages {
        stage('Stable') {
          options { skipDefaultCheckout() }
          when {
            branch 'main'
          }
          steps {
            deleteDir()
            unstash 'source'
            dir("${BASE_DIR}"){
              setupAPMGitEmail(global: false)
              sh(label: "checkout ${BRANCH_NAME} branch", script: "git checkout -f '${BRANCH_NAME}'")
              sh(label: 'rebase stable', script: """
                git checkout -f -b stable
                git rebase '${BRANCH_NAME}'
                git --no-pager log -n1 --pretty=oneline
                git rev-parse --abbrev-ref HEAD
              """)
              gitPush()
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
