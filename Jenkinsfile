#!/usr/bin/env groovy

import com.cloudbees.groovy.cps.NonCPS

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
    MAVEN_CONFIG = '-Dmaven.ext.class.path=.mvn/opentelemetry-maven-extension.jar -Dmaven.repo.local=.m2'
    OPBEANS_REPO = 'opbeans-java'
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
    issueCommentTrigger('(?i)(.*(?:jenkins\\W+)?run\\W+(?:the\\W+)?(?:(compatibility|benchmark|integration)\\W+)?tests(?:\\W+please)?.*|^/test(?:\\W+.*)?$)')
  }
  parameters {
    string(name: 'MAVEN_CONFIG', defaultValue: '-V -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Dhttps.protocols=TLSv1.2 -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.wagon.httpconnectionManager.ttlSeconds=25', description: 'Additional maven options.')
    booleanParam(name: 'test_ci', defaultValue: true, description: 'Enable Unit tests')
    booleanParam(name: 'smoketests_ci', defaultValue: true, description: 'Enable Smoke tests')
    booleanParam(name: 'integrationtests_ci', defaultValue: true, description: 'Enable Integration tests')
    booleanParam(name: 'bench_ci', defaultValue: true, description: 'Enable benchmarks')
    booleanParam(name: 'compatibility_ci', defaultValue: false, description: 'Enable JDK compatibility tests')
  }
  stages {
    stage('Initializing'){
      options { skipDefaultCheckout() }
      environment {
        HOME = "${env.WORKSPACE}"
        JAVA_HOME = "${env.HUDSON_HOME}/.java/java11"
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
            gitCheckout(basedir: "${BASE_DIR}", githubNotifyFirstTimeContributor: true, shallow: false,
                        reference: '/var/lib/jenkins/.git-references/apm-agent-java.git')
            // Prepare the maven opentelemetry extension
            prepareMavenExtension()
            stash allowEmpty: true, name: 'source', useDefaultExcludes: false
            script {
              dir("${BASE_DIR}"){
                // Skip all the stages except docs for PR's with asciidoc and md changes only
                env.ONLY_DOCS = isGitRegionMatch(patterns: [ '.*\\.(asciidoc|md)' ], shouldMatchAll: true)
              }
            }
          }
        }
        /**
        Build on a linux environment.
        */
        stage('Build') {
          when {
            beforeAgent true
            expression { return env.ONLY_DOCS == "false" }
          }
          steps {
            withGithubNotify(context: 'Build', tab: 'artifacts') {
              deleteDir()
              unstash 'source'
              // prepare m2 repository with the existing dependencies
              whenTrue(fileExists('/var/lib/jenkins/.m2/repository')) {
                sh label: 'Prepare .m2 cached folder', returnStatus: true, script: 'cp -Rf /var/lib/jenkins/.m2/repository ${HOME}/.m2'
                sh label: 'Size .m2', returnStatus: true, script: 'du -hs .m2'
              }
              dir("${BASE_DIR}"){
                retryWithSleep(retries: 5, seconds: 10) {
                  mvnwOtel(name: 'mvn install', mvnGoals: "clean install -DskipTests=true -Dmaven.javadoc.skip=true")
                }
                mvnwOtel(name: 'mvn license', mvnGoals: "org.codehaus.mojo:license-maven-plugin:aggregate-third-party-report -Dlicense.excludedGroups=^co\\.elastic\\.")
              }
              stash allowEmpty: true, name: 'build', useDefaultExcludes: false
              archiveArtifacts allowEmptyArchive: true,
                artifacts: "${BASE_DIR}/elastic-apm-agent/target/elastic-apm-agent-*.jar,${BASE_DIR}/apm-agent-attach/target/apm-agent-attach-*.jar,\
                      ${BASE_DIR}/apm-agent-attach-cli/target/apm-agent-attach-cli-*.jar,${BASE_DIR}/apm-agent-api/target/apm-agent-api-*.jar,\
                      ${BASE_DIR}/target/site/aggregate-third-party-report.html",
                onlyIfSuccessful: true
            }
          }
        }
      }
    }
    stage('Tests') {
      when {
        beforeAgent true
        expression { return env.ONLY_DOCS == "false" }
      }
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
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java11"
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
                mvnwOtel(name: 'mvn test', mvnGoals: 'test')
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
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java11"
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
                withOtelEnv() {
                  sh './scripts/jenkins/smoketests-01.sh'
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
          Run smoke tests for different servers and databases.
        */
        stage('Smoke Tests 02') {
          agent { label 'linux && immutable' }
          options { skipDefaultCheckout() }
          environment {
            HOME = "${env.WORKSPACE}"
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java11"
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
                withOtelEnv() {
                  sh './scripts/jenkins/smoketests-02.sh'
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
          Run the benchmarks and store the results on ES.
          The result JSON files are also archive into Jenkins.
        */
        stage('Benchmarks') {
          agent { label 'metal' }
          options { skipDefaultCheckout() }
          environment {
            HOME = "${env.WORKSPACE}"
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java11"
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
                tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                expression { return env.GITHUB_COMMENT?.contains('benchmark tests') }
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
                withOtelEnv() {
                  sh './scripts/jenkins/run-benchmarks.sh'
                }
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
            JAVA_HOME = "${env.HUDSON_HOME}/.java/java11"
            PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
          }
          when {
            beforeAgent true
            expression { return env.ONLY_DOCS == "false" }
          }
          steps {
            withGithubNotify(context: 'Javadoc') {
              deleteDir()
              unstash 'build'
              dir("${BASE_DIR}"){
                mvnwOtel(name: 'mvn javadoc', mvnGoals: 'compile javadoc:javadoc')
              }
            }
          }
        }
      }
    }
    stage('Integration Tests') {
      agent none
      when {
        allOf {
          expression { return env.ONLY_DOCS == "false" }
          anyOf {
            changeRequest()
            expression { return params.integrationtests_ci }
            expression { return env.GITHUB_COMMENT?.contains('integration tests') }
          }
        }
      }
      steps {
        build(job: env.ITS_PIPELINE, propagate: false, wait: false,
              parameters: [string(name: 'INTEGRATION_TEST', value: 'Java'),
                           string(name: 'BUILD_OPTS', value: "--java-agent-version ${env.GIT_BASE_COMMIT} --opbeans-java-agent-branch ${env.GIT_BASE_COMMIT}"),
                           string(name: 'GITHUB_CHECK_NAME', value: env.GITHUB_CHECK_ITS_NAME),
                           string(name: 'GITHUB_CHECK_REPO', value: env.REPO),
                           string(name: 'GITHUB_CHECK_SHA1', value: env.GIT_BASE_COMMIT)])
        githubNotify(context: "${env.GITHUB_CHECK_ITS_NAME}", description: "${env.GITHUB_CHECK_ITS_NAME} ...", status: 'PENDING', targetUrl: "${env.JENKINS_URL}search/?q=${env.ITS_PIPELINE.replaceAll('/','+')}")
      }
    }
    stage('JDK Compatibility Tests') {
      options { skipDefaultCheckout() }
      when {
        beforeAgent true
        allOf {
          expression { return env.ONLY_DOCS == "false" }
          anyOf {
            expression { return params.compatibility_ci }
            expression { return env.GITHUB_COMMENT?.contains('compatibility tests') }
          }
        }
      }
      matrix {
        agent { label 'linux && immutable' }
        axes {
          axis {
            // the list of support java versions can be found in the infra repo (ansible/roles/java/defaults/main.yml)
            name 'JAVA_VERSION'
            values 'openjdk12', 'openjdk13', 'openjdk14', 'openjdk15', 'openjdk16'
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
                  mvnwOtel(name: "test for ${JAVA_VERSION}", mvnGoals: 'test')
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
    stage('Stable') {
      options { skipDefaultCheckout() }
      when {
        branch 'master'
      }
      steps {
        deleteDir()
        unstash 'source'
        dir("${BASE_DIR}"){
          setupAPMGitEmail(global: false)
          sh(label: "checkout ${BRANCH_NAME} branch", script: "git checkout -f '${BRANCH_NAME}'")
          sh(label: 'rebase stable', script: """
            git rev-parse --quiet --verify stable && git checkout stable || git checkout -b stable
            git rebase '${BRANCH_NAME}'
          """)
          gitPush()
        }
      }
    }
    stage('AfterRelease') {
      options { skipDefaultCheckout() }
      when {
        tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
      }
      stages {
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
      notifyBuildResult(analyzeFlakey: !isTag(), flakyReportIdx: 'reporter-apm-agent-java-apm-agent-java-master', flakyDisableGHIssueCreation: true)
    }
  }
}

def reportTestResults(){
  junit(allowEmptyResults: true,
    keepLongStdio: true,
    testResults: "${BASE_DIR}/**/junit-*.xml,${BASE_DIR}/**/TEST-*.xml")
  codecov(repo: env.REPO, basedir: "${BASE_DIR}", secret: "${CODECOV_SECRET}")
}

/**
* This method wraps the logic to run maven with the maven opentelemetry extension.
*/
@NonCPS
def mvnwOtel(Map args=[:]) {
  withOtelEnv() {
    sh(label: args.name, script: "./mvnw -Dmaven.ext.class.path=.mvn/opentelemetry-maven-extension.jar ${args.mvnGoals}")
  }
}

/**
* This method wraps the logic to fetch the maven opentelemetry extension.
*/
def prepareMavenExtension() {
  dir("${BASE_DIR}") {
    sh label: 'mvn extension', script: '.ci/otel-maven.sh'
  }
}
