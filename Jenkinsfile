pipeline {
    agent any

    tools {
        jdk 'jdk_25'
    }

    environment {
        DOCKER_HUB_CREDENTIAL = credentials('dockerHub')
        GITHUB_CREDENTIAL = credentials('github')
    }

    options {
        // Configure an overall timeout for the build.
        timeout(time: 4, unit: 'HOURS')
        disableConcurrentBuilds()
    }
    
    stages {
        stage('Git submodule init') {
            steps {
                sh 'git submodule init'
                sh 'git submodule update'
            }
        }
        stage('Compile') {
            steps {
                sh 'mvn clean install -Dmaven.javadoc.skip=true -DskipTests -T1C'
            }
        }
        stage('Test') {
            steps {
                dir("tmail-backend") {
                    sh 'mvn -B -Dapi.version=1.43 surefire:test'
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
              if (env.CHANGE_FORK) {
                def forkOwner = env.CHANGE_FORK
                def memberStatus = sh(
                  script: """curl -s -o /dev/null -w "%{http_code}" \
                    -H "Authorization: token \${GITHUB_CREDENTIAL_PSW}" \
                    "https://api.github.com/orgs/linagora/members/${forkOwner}" """,
                  returnStdout: true
                ).trim()
                echo "GitHub org membership check returned HTTP ${memberStatus} for '${forkOwner}'"
                if (memberStatus == '204') {
                  echo "Fork owner '${forkOwner}' is a linagora org member, proceeding."
                } else if (memberStatus == '404') {
                  echo "Fork owner '${forkOwner}' is not a member of the linagora organization."
                  def approvedByMember = false
                  def commentsJson = sh(
                    script: """curl -s \
                      -H "Authorization: token \${GITHUB_CREDENTIAL_PSW}" \
                      "https://api.github.com/repos/linagora/tmail-backend/issues/\${CHANGE_ID}/comments" """,
                    returnStdout: true
                  ).trim()
                  def comments = readJSON text: commentsJson
                  for (comment in comments) {
                    if (comment.body.trim() == 'Build this please') {
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
                  if (!approvedByMember) {
                    echo "No linagora member approval found. Skipping PR image delivery."
                    return
                  }
                } else if (memberStatus == '401' || memberStatus == '403') {
                  error("Authentication/permission error validating fork owner: ${memberStatus}")
                } else {
                  error("GitHub API error ${memberStatus} while checking membership for '${forkOwner}'")
                }
              }

              dir("tmail-backend") {
                sh 'mvn -Pci jib:build -Djib.to.image=linagora/tmail-backend-pr -Djib.to.tags=$CHANGE_ID -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -pl apps/distributed'
              }
              sh """
                HTTP_STATUS=\$(curl -s -o /tmp/gh_comment_response.json -w "%{http_code}" -X POST \\
                  -H "Authorization: token \${GITHUB_CREDENTIAL_PSW}" \\
                  -H "Content-Type: application/json" \\
                  -d "{\\"body\\": \\"Docker image published for this PR: linagora/tmail-backend-pr:\${CHANGE_ID}\\"}" \\
                  "https://api.github.com/repos/linagora/tmail-backend/issues/\${CHANGE_ID}/comments")
                if [ "\$HTTP_STATUS" -lt 200 ] || [ "\$HTTP_STATUS" -ge 300 ]; then
                  echo "WARNING: GitHub API comment failed with HTTP \$HTTP_STATUS"
                  cat /tmp/gh_comment_response.json
                fi
              """
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
              dir("tmail-backend") {
                sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.tags=distributed-$DOCKER_TAG -pl apps/distributed -X'
                sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.tags=memory-$DOCKER_TAG -pl apps/memory -X'
                sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.tags=postgresql-$DOCKER_TAG -pl apps/postgres -X'

                // Build tmail distributed AI image
                sh 'cp tmail-third-party/ai-bot/target/tmail-ai-bot-jar-with-dependencies.jar apps/distributed/src/main/extensions-jars'
                sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.tags=distributed-ai-$DOCKER_TAG -pl apps/distributed -X'
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
        always {
            deleteDir() /* clean up our workspace */
        }
        success {
            script {
                if (env.BRANCH_NAME == "master") {
                    build (job: 'Gatling Imap build/master', propagate: false, wait: false)
                    build (job: 'James Gatling build/master', propagate: false, wait: false)
                }
            }
        }
    }
}