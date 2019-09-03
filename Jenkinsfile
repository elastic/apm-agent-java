#!/usr/bin/env groovy

@Library('apm@current') _

pipeline {
  agent any
  environment {
    REPO = 'apm-agent-java'
    BASE_DIR = "src/github.com/elastic/${env.REPO}"
    NOTIFY_TO = credentials('notify-to')
    JOB_GCS_BUCKET = credentials('gcs-bucket')
    DOCKERHUB_SECRET = 'secret/apm-team/ci/elastic-observability-dockerhub'
    CODECOV_SECRET = 'secret/apm-team/ci/apm-agent-java-codecov'
    GITHUB_CHECK_ITS_NAME = 'Integration Tests'
    ITS_PIPELINE = 'apm-integration-tests-selector-mbp/master'
    MAVEN_CONFIG = '-Dmaven.repo.local=.m2'
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
    issueCommentTrigger('(?i).*(?:jenkins\\W+)?run\\W+(?:the\\W+)?tests(?:\\W+please)?.*')
  }
  parameters {
    string(name: 'MAVEN_CONFIG', defaultValue: "-B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn", description: "Additional maven options.")
    /** TODO revert changes after tests*/
    booleanParam(name: 'Run_As_Master_Branch', defaultValue: true, description: 'Allow to run any steps on a PR, some steps normally only run on master branch.')
    booleanParam(name: 'test_ci', defaultValue: false, description: 'Enable test')
    booleanParam(name: 'smoketests_ci', defaultValue: false, description: 'Enable Smoke tests')
    booleanParam(name: 'bench_ci', defaultValue: false, description: 'Enable benchmarks')
  }

  stages {
    stage('Initializing'){
      agent { label 'linux && immutable' }
      options { skipDefaultCheckout() }
      environment {
        HOME = "${env.WORKSPACE}"
        JAVA_HOME = "${env.HUDSON_HOME}/.java/java10"
        PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
        MAVEN_CONFIG = "${params.MAVEN_CONFIG} ${env.MAVEN_CONFIG}"
      }
      stages(){
        /**
         Checkout the code and stash it, to use it on other stages.
        */
        stage('Checkout') {
          steps {
            deleteDir()
            gitCheckout(basedir: "${BASE_DIR}", githubNotifyFirstTimeContributor: true)
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
          agent { label 'linux && immutable' }
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
        beforeAgent true
        allOf {
          anyOf {
            environment name: 'GIT_BUILD_CAUSE', value: 'pr'
            expression { return !params.Run_As_Master_Branch }
          }
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
    stage('Release') {
      agent { label 'linux && immutable' }
      options { skipDefaultCheckout() }
      environment {
        HOME = "${env.WORKSPACE}"
        JAVA_HOME = "${env.HUDSON_HOME}/.java/java10"
        PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
        TMP_WORKSPACE="${env.WORKSPACE}/@tmp"
        KEY_FILE="${env.WORKSPACE}/private.key"
        GNUPGHOME="${env.TMP_WORKSPACE}/keyring"
      }
      input {
        message 'Should we release a new version?'
        ok 'Yes, we should.'
      }
      when {
        beforeAgent true
        anyOf {
          tag pattern: 'v\\d+\\.\\d+\\.\\d+.*', comparator: 'REGEXP'
          expression { return params.Run_As_Master_Branch }
        }
      }
      steps {
        deleteDir()
        unstash 'build'
        dir("${BASE_DIR}"){
          releasePackages()
        }
      }
      post {
        always {
          deleteDir()
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

def releasePackages(){
  //Prepare a secure temp folder not shared between other jobs to store the key ring
  sh(label: "Prepare a secure temp folder", script: """
  mkdir -p ${GNUPGHOME}
  chmod -R 700 ${TMP_WORKSPACE}
  """)

  //Signing keys
  def jsonKey = getVaultSecret(secret: 'secret/release/signing')
  writeFile(file: "${KEY_FILE}",text: jsonKey.data.key)

  //Nexus credentials
  def jsonValue = getVaultSecret(secret: 'secret/release/nexus')

  //Username to release
  def jsonReleaseUser = getVaultSecret(secret: 'secret/apm-team/ci/apm-agent-java-release')

  wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [
    [var: 'AUTH_USER', password: jsonValue.data.username],
    [var: 'AUTH_PASSWORD', password: jsonValue.data.password],
    [var: 'KEYPASS', password: jsonKey.data.passphrase],
    [var: 'RELEASE_USER', password: jsonReleaseUser.data.user]
  ]]) {
    withEnv([
      "AUTH_USER=${jsonValue.data.user}",
      "AUTH_PASSWORD=${jsonValue.data.password}"],
      "KEYPASS=${jsonKey.data.passphrase}",
      "RELEASE_USER=${jsonReleaseUser.data.user}") {
      sh(label: "Release packages", script: '''
      set +x
      # Jenkins checks out a detached HEAD but the maven release plugin requires to be on a branch
      git checkout -f ${BRANCH_NAME}

      # Import the key into the keyring
      echo ${KEYPASS} | gpg --batch --import ${KEY_FILE}

      # Enable this job to push the tags and commits created by the maven-release-plugin
      /usr/local/bin/ssh-add-from-vault ${RELEASE_USER}

      # load keychain environment variables
      [[ -z ${HOSTNAME} ]] && HOSTNAME=$(uname -n)
      source "${HOME}/.keychain/${HOSTNAME}-sh" 2> /dev/null

      # Release the binaries
      # providing settings in arguments to make sure they are propagated to the forked maven release process
      ./mvnw release:prepare release:perform --settings .ci/settings.xml -Darguments="--settings .ci/settings.xml" --batch-mode
      ''')
    }
  }
}
