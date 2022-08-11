pipeline {
    agent {
      label 'jdk17'
    }

    options {
        // Configure an overall timeout for the build.
        timeout(time: 2, unit: 'HOURS')
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
                sh 'mvn clean install -Dmaven.javadoc.skip=true -DskipTests -T1C -Dtarget.jdk=17'
            }
        }
        stage('Test') {
            steps {
                dir("tmail-backend") {
                    sh 'mvn -B surefire:test'
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

              // Load docker from tar file
              sh "docker load -i tmail-backend/apps/distributed/target/jib-image.tar"
              sh "docker load -i tmail-backend/apps/distributed-es6-backport/target/jib-image.tar"
              sh "docker load -i tmail-backend/apps/memory/target/jib-image.tar"

              // Temporary retag image names
              sh "docker tag linagora/tmail-backend-memory linagora/tmail-backend:memory-${env.DOCKER_TAG}"
              sh "docker tag linagora/tmail-backend-distributed linagora/tmail-backend:distributed-${env.DOCKER_TAG}"
              sh "docker tag linagora/tmail-backend-distributed-esv6 linagora/tmail-backend:distributed-esv6-${env.DOCKER_TAG}"

              def memoryImage = docker.image "linagora/tmail-backend:memory-${env.DOCKER_TAG}"
              def distributedImage = docker.image "linagora/tmail-backend:distributed-${env.DOCKER_TAG}"
              def distributedEs6Image = docker.image "linagora/tmail-backend:distributed-esv6-${env.DOCKER_TAG}"
              docker.withRegistry('', 'dockerHub') {
                memoryImage.push()
                distributedImage.push()
                distributedEs6Image.push()
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