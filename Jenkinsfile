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
    issueCommentTrigger('(?i).*(?:jenkins\\W+)?run\\W+(?:the\\W+)?(?:benchmark\\W+)?tests(?:\\W+please)?.*')
  }
  parameters {
    string(name: 'MAVEN_CONFIG', defaultValue: '-B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn', description: 'Additional maven options.')
    booleanParam(name: 'Run_As_Master_Branch', defaultValue: false, description: 'Allow to run any steps on a PR, some steps normally only run on master branch.')
    booleanParam(name: 'test_ci', defaultValue: true, description: 'Enable test')
    booleanParam(name: 'smoketests_ci', defaultValue: true, description: 'Enable Smoke tests')
    booleanParam(name: 'bench_ci', defaultValue: true, description: 'Enable benchmarks')
    booleanParam(name: 'push_docker', defaultValue: false, description: 'Push Docker image during release stage')
    // FIXME: Remove before PR
    booleanParam(name: 'release_stage', defaultValue: false, description: 'Ungate the release stage')
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
                tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                expression { return params.Run_As_Master_Branch }
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
            expression { return env.ONLY_DOCS == "false" }
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
        allOf {
          expression { return env.ONLY_DOCS == "false" }
          anyOf {
            changeRequest()
            expression { return !params.Run_As_Master_Branch }
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
    stage('Release') {
      options { skipDefaultCheckout () }
      when {
        // FIXME: Remove before PR
        // tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
        expression { return params.release_stage }
      }
      steps {

        // 1. Check to see if oss.sonatype.org is up. We throw an exception and fail the job if there is no response.
        httpRequest(url: "https://oss.sonatype.org/#welcome")

        // 2. Review project version.
        script {
          // Show the select input modal
          def INPUT_PARAMS = input message: 'Do you wish to update the version?', ok: 'Next', parameters: [choice(name: 'UPDATE_CHOICE', choices:['Yes', 'No'], description: "This will run `mvn release:update-versions`")]
          echo INPUT_PARAMS
          if (INPUT_PARAMS == 'Yes') {
              // FIXME We might not yet have the right credentials loaded to do this
              sh mvn release:update-versions
          } else {
              echo 'Skipping version update'
          }
        }
        // 3. Execute the release Jenkins job on the internal ci server.
            // For now we assume that the release has been pushed to staging already on the internal CI
        // 4. Login to oss.sonatype.org, go to Staging Repositories, close and release the staged artifacts.
        script {
          def foundStagingId = nexusFindStagingId(stagingProfileId: "TODO MOVE TO VAULT", description: "TODO")
          nexusCloseStagingRepository(stagingProfileId: "TODO MOVE TO VAULT", stagingId: foundStagingId)
          nexusReleaseStagingRepository(stagingProfileId: "TODO MOVE TO VAULT", stagingId: foundStagingId)
        }
        // 5. Fetch and checkout the latest tag e.g. git fetch origin
          // We already have this :)
        // 6. If this was a major release, create a new branch for the major.
        script {
          def isMajor = sh(script: 'git tag|tail -1|cut -f2-3 -d "."|{ read ver; test $ver == "0.0"; }', returnStatus: true)
          if (isMajor == 0) {
            echo "This appears to be a major version. We are creating a new branch in the apm-agent-java repo on GitHub."
            // We need to create a new branch. First get the name of the branch
            def newBranchName = sh(script: 'git tag|tail -1|sed s/v//|cut -f1 -d "."|awk \'{print $1".x"}\'', returnStdout: true)
            echo "This appears to be a major version. We are creating a new branch for $newBranchName in the apm-agent-java repo on GitHub."
            // Now we need to branch
            sh(script: "git checkout -b $newBranchName")
            // And push
            githubEnv()
            gitPush() // FIXME. Does it need credentials?
            // 6.1 Add the new branch to the conf.yaml in the docs repo
            input message: "This was a major version release. Please update the conf.yml in the docs repo before continuing", ok "Continue"
          } else {  // This was a minor release
            // 7. If this was a minor release, reset the current major branch (1.x, 2.x etc) to point to the current tag, e.g. git branch -f 1.x v1.1.0
            // Determine current tag
            def curTag = sh(script: "git tag|tail -1", returnStdout: true)
            def targetBranch = sh(script: 'git tag|tail -1|sed s/v//|cut -f1 -d '.'|awk \'{print $1".x"}\'', returnStdout: true)
            sh(script: "git branch -f $targetBranch $curTag")
          }
        }
        // 8. Update CHANGELOG.asciidoc to reflect version release. Go over PRs or git log and add bug fixes and features. 

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
