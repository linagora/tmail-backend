// Files that the pipeline itself executes, or that decide what it executes.
// Jenkins loads the Jenkinsfile from the trusted revision - the target branch -
// so a pull request can never change the pipeline definition. Everything else
// below is read from the workspace, which holds the contributor's code, so a
// pull request changing them would be running its own code inside our CI.
def protectedCiPaths() {
    return ['Jenkinsfile', 'ci/', '.mvn/', '.gitmodules']
}

// A contributor is trusted when they can already push to the repository: either
// the change request comes from a branch of linagora/tmail-backend, or from a
// fork owned by a member of the linagora organisation.
def isTrustedContributor() {
    if (!env.CHANGE_FORK) {
        return true
    }

    def forkOwner = env.CHANGE_FORK.split('/')[0]
    def status = null
    withCredentials([usernamePassword(credentialsId: 'github',
            usernameVariable: 'GITHUB_CREDENTIAL_USR', passwordVariable: 'GITHUB_CREDENTIAL_PSW')]) {
        status = sh(
            script: """curl -s -o /dev/null -w "%{http_code}" \
              -H "Authorization: token \${GITHUB_CREDENTIAL_PSW}" \
              "https://api.github.com/orgs/linagora/members/${forkOwner}" """,
            returnStdout: true
        ).trim()
    }

    echo "GitHub org membership check returned HTTP ${status} for '${forkOwner}'"
    if (status == '204') {
        return true
    }
    if (status == '404') {
        return false
    }
    if (status == '401' || status == '403') {
        error("Authentication/permission error validating fork owner: ${status}")
    }
    error("GitHub API error ${status} while checking membership for '${forkOwner}'")
}

// Makes the target branch reachable as origin/<target> so that the change
// request can be diffed against it, whatever pull request discovery strategy
// the job is configured with.
def fetchChangeTarget() {
    if (!env.CHANGE_TARGET) {
        error('CHANGE_TARGET is not set: cannot determine what this change request modifies.')
    }
    sh "git fetch --no-tags --quiet origin '+refs/heads/${env.CHANGE_TARGET}:refs/remotes/origin/${env.CHANGE_TARGET}'"
}

def changedCiFiles() {
    def changed = sh(
        script: "git diff --name-only 'origin/${env.CHANGE_TARGET}...HEAD'",
        returnStdout: true
    )

    def offending = []
    for (String path : changed.split('\n')) {
        String candidate = path.trim()
        if (candidate.isEmpty()) {
            continue
        }
        for (String protectedPath : protectedCiPaths()) {
            if (candidate == protectedPath || candidate.startsWith(protectedPath)) {
                offending.add(candidate)
                break
            }
        }
    }
    return offending
}

// The report script is handed the GitHub token, so it has to be the reviewed
// version of it: for a change request the workspace copy belongs to the
// contributor, and the target branch one is the one we trust. The guard stage
// already refuses untrusted changes to `ci/`, this is the second lock.
def trustedReportScript() {
    def scriptPath = "${env.WORKSPACE_TMP ?: '/tmp'}/report-build-failure-${env.BUILD_NUMBER}.sh"
    if (env.CHANGE_ID) {
        fetchChangeTarget()
        sh "git show 'origin/${env.CHANGE_TARGET}:ci/report-build-failure.sh' > '${scriptPath}'"
    } else {
        sh "cp ci/report-build-failure.sh '${scriptPath}'"
    }
    return scriptPath
}

// Posts the failing tests - or, when none was reported, the tail of the failing
// stage output - back to the pull request, so that a coding agent can pick the
// build failure up and drive it back to green on its own.
//
// Only the stages that hold no credential are teed to ci-logs: the tee step
// captures the raw stream, before the console log filter that masks secrets, so
// a teed log of a stage handling credentials would carry them in clear text -
// straight into a public pull request comment.
def reportBuildFailure() {
    archiveArtifacts artifacts: 'ci-logs/*.log', allowEmptyArchive: true, fingerprint: false

    def reportScript = trustedReportScript()
    withCredentials([usernamePassword(credentialsId: 'github',
            usernameVariable: 'GITHUB_CREDENTIAL_USR', passwordVariable: 'GITHUB_TOKEN')]) {
        sh "bash '${reportScript}' || true"
    }
    sh "rm -f '${reportScript}'"
}

pipeline {
    agent {
        label 'heavy'
    }

    tools {
        jdk 'jdk_25'
    }

    options {
        // Configure an overall timeout for the build.
        timeout(time: 4, unit: 'HOURS')
        disableConcurrentBuilds()
    }

    stages {
        // First stage on purpose: nothing from the change request has been run
        // yet at this point, not even `git submodule update`.
        stage('Validate CI files') {
            when {
                changeRequest()
            }
            steps {
                script {
                    fetchChangeTarget()
                    def offending = changedCiFiles()
                    if (offending.isEmpty()) {
                        echo 'No CI file touched by this change request.'
                    } else if (isTrustedContributor()) {
                        echo "Trusted contributor, changes to ${offending.join(', ')} allowed."
                    } else {
                        error("""This change request modifies files the CI executes: ${offending.join(', ')}.
Contributors outside the linagora organisation cannot change them, as the build would then run unreviewed code with access to the CI credentials.
Please drop these changes from the pull request, or ask a linagora member to carry them.""")
                    }
                }
            }
        }
        stage('Git submodule init') {
            steps {
                sh 'mkdir -p ci-logs'
                tee('ci-logs/Git submodule init.log') {
                    sh 'git submodule init'
                    sh 'git submodule update'
                }
            }
        }
        stage('Compile') {
            steps {
                tee('ci-logs/Compile.log') {
                    sh 'mvn clean install -Dmaven.javadoc.skip=true -DskipTests -T1C'
                }
            }
        }
        stage('Test') {
            steps {
                tee('ci-logs/Test.log') {
                    dir("tmail-backend") {
                        sh 'mvn -B -Dapi.version=1.43 surefire:test'
                    }
                }
            }
            post {
                always {
                    junit(testResults: '**/surefire-reports/*.xml', allowEmptyResults: false)
                }
                failure {
                    archiveArtifacts artifacts: '**/target/test-run.log' , fingerprint: true
                    archiveArtifacts artifacts: '**/surefire-reports/*' , fingerprint: true
                }
            }
        }
        stage('Deliver Docker images for PR') {
          when {
            changeRequest()
          }
          steps {
            script {
              if (env.CHANGE_FORK && !isTrustedContributor()) {
                def approvedByMember = false
                withCredentials([usernamePassword(credentialsId: 'github',
                        usernameVariable: 'GITHUB_CREDENTIAL_USR', passwordVariable: 'GITHUB_CREDENTIAL_PSW')]) {
                  def commentsJson = sh(
                    script: """curl -s \
                      -H "Authorization: token \${GITHUB_CREDENTIAL_PSW}" \
                      "https://api.github.com/repos/linagora/tmail-backend/issues/\${CHANGE_ID}/comments" """,
                    returnStdout: true
                  ).trim()
                  def comments = new groovy.json.JsonSlurper().parseText(commentsJson)
                  for (comment in comments) {
                    if (comment.body.trim().toLowerCase() == 'build this please') {
                      def commenter = comment.user.login
                      def commenterStatus = sh(
                        script: """curl -s -o /dev/null -w "%{http_code}" \
                          -H "Authorization: token \${GITHUB_CREDENTIAL_PSW}" \
                          "https://api.github.com/orgs/linagora/members/${commenter}" """,
                        returnStdout: true
                      ).trim()
                      if (commenterStatus == '204') {
                        echo "Build approved by linagora member '${commenter}', proceeding."
                        approvedByMember = true
                        break
                      }
                    }
                  }
                }
                if (!approvedByMember) {
                  echo "No linagora member approval found. Skipping PR image delivery."
                  return
                }
              }

              // jib runs the change request's own pom.xml, so the DockerHub
              // credential is only bound once the delivery is approved above.
              withCredentials([usernamePassword(credentialsId: 'dockerHub',
                      usernameVariable: 'DOCKER_HUB_CREDENTIAL_USR', passwordVariable: 'DOCKER_HUB_CREDENTIAL_PSW')]) {
                dir("tmail-backend") {
                  sh 'mvn -Pci jib:build -Djib.to.image=linagora/tmail-backend-pr -Djib.to.tags=$CHANGE_ID -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -pl apps/distributed'
                  sh 'mvn -Pci jib:build -Djib.to.image=linagora/tmail-migration-proxy-pr -Djib.to.tags=$CHANGE_ID -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -pl apps/migration-proxy'
                  // Build tmail distributed AI PR image
                  sh 'cp tmail-third-party/ai-bot/target/tmail-ai-bot-jar-with-dependencies.jar apps/distributed/src/main/extensions-jars'
                  sh 'mvn -Pci jib:build -Djib.to.image=linagora/tmail-backend-distributed-pr -Djib.to.tags=ai-$CHANGE_ID -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -pl apps/distributed'
                }
              }

              withCredentials([usernamePassword(credentialsId: 'github',
                      usernameVariable: 'GITHUB_CREDENTIAL_USR', passwordVariable: 'GITHUB_CREDENTIAL_PSW')]) {
                sh """
                  HTTP_STATUS=\$(curl -s -o /tmp/gh_comment_response.json -w "%{http_code}" -X POST \\
                    -H "Authorization: token \${GITHUB_CREDENTIAL_PSW}" \\
                    -H "Content-Type: application/json" \\
                    -d "{\\"body\\": \\"Docker images published for this PR:\\\\n - linagora/tmail-backend-pr:\${CHANGE_ID}\\\\n - linagora/tmail-backend-distributed-pr:ai-\${CHANGE_ID}\\\\n - linagora/tmail-migration-proxy-pr:\${CHANGE_ID}\\"}" \\
                    "https://api.github.com/repos/linagora/tmail-backend/issues/\${CHANGE_ID}/comments")
                  if [ "\$HTTP_STATUS" -lt 200 ] || [ "\$HTTP_STATUS" -ge 300 ]; then
                    echo "WARNING: GitHub API comment failed with HTTP \$HTTP_STATUS"
                    cat /tmp/gh_comment_response.json
                  fi
                """
              }
            }
          }
        }
        stage('Deliver Docker images') {
          when {
            anyOf {
              branch 'master'
              buildingTag()
            }
          }
          steps {
            script {
              env.DOCKER_TAG = 'branch-master'
              if (env.TAG_NAME) {
                env.DOCKER_TAG = env.TAG_NAME
              }

              echo "Docker tag: ${env.DOCKER_TAG}"
              // build and push docker images
              withCredentials([usernamePassword(credentialsId: 'dockerHub',
                      usernameVariable: 'DOCKER_HUB_CREDENTIAL_USR', passwordVariable: 'DOCKER_HUB_CREDENTIAL_PSW')]) {
                dir("tmail-backend") {
                  sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.tags=distributed-$DOCKER_TAG -pl apps/distributed -X'
                  sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.tags=memory-$DOCKER_TAG -pl apps/memory -X'
                  sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.tags=postgresql-$DOCKER_TAG -pl apps/postgres -X'
                  sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.tags=$DOCKER_TAG -pl apps/migration-proxy -X'

                  // Build tmail distributed AI image
                  sh 'cp tmail-third-party/ai-bot/target/tmail-ai-bot-jar-with-dependencies.jar apps/distributed/src/main/extensions-jars'
                  sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.tags=distributed-ai-$DOCKER_TAG -pl apps/distributed -X'
                }
              }
            }
          }
          post {
              always {
                  script {
                      if (env.BRANCH_NAME == "master") {
                          emailext(
                                  subject: "[${currentBuild.result}]: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]'",
                                  body: """
${currentBuild.result}: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]:
Check console output at "<a href="${env.BUILD_URL}">${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]</a>'"
""",
                                  to: "openpaas-james@linagora.com"
                          )
                      }
                  }
              }
          }
        }
    }
    post {
        failure {
            reportBuildFailure()
        }
        unstable {
            reportBuildFailure()
        }
        success {
            script {
                if (env.BRANCH_NAME == "master") {
                    build (job: 'Gatling Imap build/master', propagate: false, wait: false)
                    build (job: 'James Gatling build/master', propagate: false, wait: false)
                }
            }
        }
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}
