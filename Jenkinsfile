pipeline {
    agent any

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
                sh 'mvn clean install -Dmaven.javadoc.skip=true -DskipTests -T1C'
            }
        }
        stage('Test') {
            steps {
                dir("openpaas-james") {
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
              env.DOCKER_TAG = 'openpaas-branch-master'
              if (env.TAG_NAME) {
                env.DOCKER_TAG = env.TAG_NAME
              }

              echo "Docker tag: ${env.TAG_NAME}"

              // Temporary retag image names
              sh 'docker tag linagora/openpaas-james-memory linagora/james-memory'
              sh 'docker tag linagora/openpaas-james-distributed linagora/james-rabbitmq-project'

              def memoryImage = docker.image 'linagora/james-memory'
              def distributedImage = docker.image 'linagora/james-rabbitmq-project'
              docker.withRegistry('', 'dockerHub') {
                memoryImage.push(env.DOCKER_TAG)
                distributedImage.push(env.DOCKER_TAG)
              }
            }
          }
          post {
              always {
                  deleteDir() /* clean up our workspace */
              }
          }
        }
    }
}