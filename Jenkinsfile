pipeline {
    agent any

    environment {
        DOCKER_HUB_CREDENTIAL = credentials('dockerHub')
    }

    options {
        // Configure an overall timeout for the build.
        timeout(time: 3, unit: 'HOURS')
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
                    sh 'mvn -B surefire:test'
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
        stage('Deliver Docker images') {
          when {
            anyOf {
              branch 'postgresql'
              buildingTag()
            }
          }
          steps {
            script {
              // build and push docker images
              dir("tmail-backend") {
                sh 'mvn -Pci jib:build -Djib.to.auth.username=$DOCKER_HUB_CREDENTIAL_USR -Djib.to.auth.password=$DOCKER_HUB_CREDENTIAL_PSW -Djib.to.image=linagora/tmail-backend:postgresql-experimental -Djib.to.tags=postgresql-experimental -pl apps/postgres -X'
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