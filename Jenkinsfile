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
    GITHUB_CHECK_ITS_NAME = 'Integration Tests'
    ITS_PIPELINE = 'apm-integration-tests-selector-mbp/master'
    MAVEN_CONFIG = '-Dmaven.repo.local=.m2'
    OPBEANS_REPO = 'opbeans-java'
  }
  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '20', daysToKeepStr: '30'))
    timestamps()
    ansiColor('xterm')
    disableResume()
    durabilityHint('PERFORMANCE_OPTIMIZED')
    rateLimitBuilds(throttle: [count: 60, durationName: 'hour', userBoost: true])
    quietPeriod(10)
  }
  triggers {
    issueCommentTrigger('(?i)(.*(?:jenkins\\W+)?run\\W+(?:the\\W+)?(?:matrix\\W+)?tests(?:\\W+please)?.*|^/test(?:\\W+.*)?$)')
  }
  parameters {
    string(name: 'MAVEN_CONFIG', defaultValue: '-B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn', description: 'Additional maven options.')
    booleanParam(name: 'Run_As_Master_Branch', defaultValue: false, description: 'Allow to run any steps on a PR, some steps normally only run on master branch.')
    booleanParam(name: 'test_ci', defaultValue: true, description: 'Enable test')
    booleanParam(name: 'smoketests_ci', defaultValue: true, description: 'Enable Smoke tests')
    booleanParam(name: 'bench_ci', defaultValue: true, description: 'Enable benchmarks')
    booleanParam(name: 'push_docker', defaultValue: false, description: 'Push Docker image during release stage')
  }
  stages {
    stage('Initializing'){
      options { skipDefaultCheckout() }
      environment {
        HOME = "${env.WORKSPACE}"
        JAVA_HOME = "${env.HUDSON_HOME}/.java/java10"
        PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
        MAVEN_CONFIG = "${params.MAVEN_CONFIG} ${env.MAVEN_CONFIG}"
      }
      stages {
        /**
         Checkout the code and stash it, to use it on other stages.
        */
        stage('Checkout') {
          steps {
            pipelineManager([ cancelPreviousRunningBuilds: [ when: 'PR' ] ])
            deleteDir()
            gitCheckout(basedir: "${BASE_DIR}", githubNotifyFirstTimeContributor: true, reference: '/var/lib/jenkins/.git-references/apm-agent-java.git')
            stash allowEmpty: true, name: 'source', useDefaultExcludes: false
          }
        }
        /**
        Build on a linux environment.
        */
        stage('Build') {
          steps {
            withGithubNotify(context: 'Build', tab: 'artifacts') {
              deleteDir()
              unstash 'source'
              dir("${BASE_DIR}"){
                sh """#!/bin/bash
                set -euxo pipefail
                ./mvnw clean install -DskipTests=true -Dmaven.javadoc.skip=true
                ./mvnw license:aggregate-third-party-report -Dlicense.excludedGroups=^co\\.elastic\\.
                """
              }
              stash allowEmpty: true, name: 'build', useDefaultExcludes: false
              archiveArtifacts allowEmptyArchive: true,
                artifacts: "${BASE_DIR}/elastic-apm-agent/target/elastic-apm-agent-*.jar,${BASE_DIR}/apm-agent-attach/target/apm-agent-attach-*.jar,\
                      ${BASE_DIR}/target/site/aggregate-third-party-report.html",
                onlyIfSuccessful: true
            }
          }
        }
      }
    }
    stage('Tests') {
      environment {
        MAVEN_CONFIG = "${params.MAVEN_CONFIG} ${env.MAVEN_CONFIG}"
      }
      failFast true
      parallel {
        /**
          Run only unit test.
        */
        stage('Unit Tests') {
          options { skipDefaultCheckout() }
          environment {
            HOME = "${env.WORKSPACE}"
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java10"
            PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
          }
          when {
            beforeAgent true
            expression { return params.test_ci }
          }
          steps {
            withGithubNotify(context: 'Unit Tests', tab: 'tests') {
              deleteDir()
              unstash 'build'
              dir("${BASE_DIR}"){
                sh """#!/bin/bash
                set -euxo pipefail
                ./mvnw test
                """
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
          Run smoke tests for different servers and databases.
        */
        stage('Smoke Tests 01') {
          agent { label 'linux && immutable' }
          options { skipDefaultCheckout() }
          environment {
            HOME = "${env.WORKSPACE}"
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java10"
            PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
          }
          when {
            beforeAgent true
            expression { return params.smoketests_ci }
          }
          steps {
            withGithubNotify(context: 'Smoke Tests 01', tab: 'tests') {
              deleteDir()
              unstash 'build'
              dir("${BASE_DIR}"){
                sh './scripts/jenkins/smoketests-01.sh'
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
          Run smoke tests for different servers and databases.
        */
        stage('Smoke Tests 02') {
          agent { label 'linux && immutable' }
          options { skipDefaultCheckout() }
          environment {
            HOME = "${env.WORKSPACE}"
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java10"
            PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
          }
          when {
            beforeAgent true
            expression { return params.smoketests_ci }
          }
          steps {
            withGithubNotify(context: 'Smoke Tests 02', tab: 'tests') {
              deleteDir()
              unstash 'build'
              dir("${BASE_DIR}"){
                sh './scripts/jenkins/smoketests-02.sh'
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
          Run the benchmarks and store the results on ES.
          The result JSON files are also archive into Jenkins.
        */
        stage('Benchmarks') {
          agent { label 'metal' }
          options { skipDefaultCheckout() }
          environment {
            HOME = "${env.WORKSPACE}"
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java10"
            PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
            NO_BUILD = "true"
          }
          when {
            beforeAgent true
            allOf {
              anyOf {
                branch 'master'
                branch "\\d+\\.\\d+"
                branch "v\\d?"
                tag "v\\d+\\.\\d+\\.\\d+*"
                expression { return params.Run_As_Master_Branch }
              }
              expression { return params.bench_ci }
            }
          }
          steps {
            withGithubNotify(context: 'Benchmarks', tab: 'artifacts') {
              deleteDir()
              unstash 'build'
              dir("${BASE_DIR}"){
                script {
                  env.COMMIT_ISO_8601 = sh(script: 'git log -1 -s --format=%cI', returnStdout: true).trim()
                  env.NOW_ISO_8601 = sh(script: 'date -u "+%Y-%m-%dT%H%M%SZ"', returnStdout: true).trim()
                  env.RESULT_FILE = "apm-agent-benchmark-results-${env.COMMIT_ISO_8601}.json"
                  env.BULK_UPLOAD_FILE = "apm-agent-bulk-${env.NOW_ISO_8601}.json"
                }
                sh './scripts/jenkins/run-benchmarks.sh'
              }
            }
          }
          post {
            always {
              archiveArtifacts(allowEmptyArchive: true,
                artifacts: "${BASE_DIR}/${RESULT_FILE}",
                onlyIfSuccessful: false)
              sendBenchmarks(file: "${BASE_DIR}/${BULK_UPLOAD_FILE}", index: "benchmark-java")
            }
          }
        }
        /**
          Build javadoc files.
        */
        stage('Javadoc') {
          agent { label 'linux && immutable' }
          options { skipDefaultCheckout() }
          environment {
            HOME = "${env.WORKSPACE}"
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java10"
            PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
          }
          when {
            beforeAgent true
            expression { return params.doc_ci }
          }
          steps {
            withGithubNotify(context: 'Javadoc') {
              deleteDir()
              unstash 'build'
              dir("${BASE_DIR}"){
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
    stage('Integration Tests') {
      agent none
      when {
        anyOf {
          changeRequest()
          expression { return !params.Run_As_Master_Branch }
        }
      }
      steps {
        log(level: 'INFO', text: 'Launching Async ITs')
        build(job: env.ITS_PIPELINE, propagate: false, wait: false,
              parameters: [string(name: 'AGENT_INTEGRATION_TEST', value: 'Java'),
                           string(name: 'BUILD_OPTS', value: "--java-agent-version ${env.GIT_BASE_COMMIT}"),
                           string(name: 'GITHUB_CHECK_NAME', value: env.GITHUB_CHECK_ITS_NAME),
                           string(name: 'GITHUB_CHECK_REPO', value: env.REPO),
                           string(name: 'GITHUB_CHECK_SHA1', value: env.GIT_BASE_COMMIT)])
        githubNotify(context: "${env.GITHUB_CHECK_ITS_NAME}", description: "${env.GITHUB_CHECK_ITS_NAME} ...", status: 'PENDING', targetUrl: "${env.JENKINS_URL}search/?q=${env.ITS_PIPELINE.replaceAll('/','+')}")
      }
    }
    stage('Unit Tests') {
      options { skipDefaultCheckout() }
      when {
        beforeAgent true
        anyOf {
          not { changeRequest() }
          expression { return params.Run_As_Master_Branch }
          expression { return env.GITHUB_COMMENT?.contains('matrix tests') }
        }
      }
      matrix {
        agent { label 'linux && immutable' }
        axes {
          axis {
            // the list of support java versions can be found in the infra repo (ansible/roles/java/defaults/main.yml)
            name 'JAVA_VERSION'
            values 'java11', 'java12', 'adoptopenjdk13', 'adoptopenjdk14', 'adoptopenjdk15', 'adoptopenjdk16'
          }
        }
        stages {
          stage('Test') {
            environment {
              HOME = "${env.WORKSPACE}"
              JAVA_HOME = "${env.HUDSON_HOME}/.java/${JAVA_VERSION}"
              PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
            }
            steps {
              withGithubNotify(context: "Unit Tests ${JAVA_VERSION}", tab: 'tests') {
                deleteDir()
                unstash 'build'
                dir("${BASE_DIR}"){
                  sh './mvnw -V -B test'
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
    stage('AfterRelease') {
      options { skipDefaultCheckout() }
      when {
        tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
      }
      stages {
        stage('Docker push') {
          when {
            beforeAgent true
            expression { return params.push_docker }
          }
          steps {
            sh(label: "Build Docker image", script: "scripts/jenkins/build_docker.sh")
            // Get Docker registry credentials
            dockerLogin(secret: "${ELASTIC_DOCKER_SECRET}", registry: 'docker.elastic.co')
            sh(label: "Push Docker image", script: "scripts/jenkins/push_docker.sh")
          }
        }
        stage('Opbeans') {
          environment {
            REPO_NAME = "${OPBEANS_REPO}"
          }
          steps {
            deleteDir()
            dir("${OPBEANS_REPO}"){
              git credentialsId: 'f6c7695a-671e-4f4f-a331-acdce44ff9ba',
                  url: "git@github.com:elastic/${OPBEANS_REPO}.git"
              // It's required to transform the tag value to the artifact version
              sh script: ".ci/bump-version.sh ${env.BRANCH_NAME.replaceAll('^v', '')}", label: 'Bump version'
              // The opbeans-java pipeline will trigger a release for the master branch
              gitPush()
              // The opbeans-java pipeline will trigger a release for the release tag
              gitCreateTag(tag: "${env.BRANCH_NAME}")
            }
          }
        }
      }
    }
  }
  post {
    cleanup {
      notifyBuildResult()
    }
  }
}

def reportTestResults(){
  junit(allowEmptyResults: true,
    keepLongStdio: true,
    testResults: "${BASE_DIR}/**/junit-*.xml,${BASE_DIR}/**/TEST-*.xml")
  codecov(repo: env.REPO, basedir: "${BASE_DIR}", secret: "${CODECOV_SECRET}")
}
