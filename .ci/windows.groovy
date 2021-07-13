#!/usr/bin/env groovy

@Library('apm@current') _

pipeline {
  agent { label 'windows-2019-docker-immutable' }
  environment {
    REPO = 'apm-agent-java'
    BASE_DIR = "src/github.com/elastic/${env.REPO}"
    NOTIFY_TO = credentials('notify-to')
    MAVEN_CONFIG = "${params.MAVEN_CONFIG} -Dmaven.repo.local=.m2"
    JAVA_VERSION = "${params.JAVA_VERSION}"
    REFSPEC = "${params.REFSPEC}"
  }
  options {
    timeout(time: 2, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '20', daysToKeepStr: '30'))
    timestamps()
    ansiColor('xterm')
    disableResume()
    durabilityHint('PERFORMANCE_OPTIMIZED')
    rateLimitBuilds(throttle: [count: 60, durationName: 'hour', userBoost: true])
    quietPeriod(10)
  }
  triggers {
    cron('H H(4-5) * * 0')
  }
  parameters {
    string(name: 'REFSPEC', defaultValue: 'master', description: 'What git reference?')
    string(name: 'JAVA_VERSION', defaultValue: 'java11', description: 'What Java version?')
    string(name: 'MAVEN_CONFIG', defaultValue: '-V -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dhttps.protocols=TLSv1.2 -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.wagon.httpconnectionManager.ttlSeconds=25', description: 'Additional maven options.')
  }
  stages {
    stage('Checkout') {
      steps {
        deleteDir()
        gitCheckout(basedir: "${BASE_DIR}", githubNotifyFirstTimeContributor: true, shallow: false,
                    branch: "${REFSPEC}", reference: '/var/lib/jenkins/.git-references/apm-agent-java.git')
      }
    }
    stage('Windows Build') {
      steps {
        deleteDir()
        unstash 'source'
        dir("${BASE_DIR}"){
          retryWithSleep(retries: 5, seconds: 10) {
            bat "mvnw clean install -DskipTests=true -Dmaven.javadoc.skip=true -Dmaven.gitcommitid.skip=true"
          }
        }
      }
    }
    stage('Windows Verify') {
      steps {
        dir("${BASE_DIR}"){
          bat "mvnw verify"
        }
      }
      post {
        always {
          junit(allowEmptyResults: true, keepLongStdio: true, testResults: "${BASE_DIR}/**/junit-*.xml,${BASE_DIR}/**/TEST-*.xml")
        }
      }
    }
  }
  post {
    cleanup {
      notifyBuildResult()
    }
    unsuccessful {
      slackSend(channel: '#apm-agent-java',
                color: 'danger',
                message: "[${env.REPO}] Windows build failed. (<${env.RUN_DISPLAY_URL}|Open>)",
                tokenCredentialId: 'jenkins-slack-integration-token')
    }
  }
}
