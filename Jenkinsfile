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
                    echo 'skip tests for script docker push testing'
                }
            }
        }
        stage('Deliver Docker images') {
          when {
            branch 'master'
          }
          steps {
            script {
              // Temporary retag image names
              sh 'docker tag linagora/openpaas-james-memory linagora/james-memory'
              sh 'docker tag linagora/openpaas-james-distributed linagora/james-rabbitmq-project'

              def memoryImage = docker.image 'linagora/james-memory'
              def distributedImage = docker.image 'linagora/james-rabbitmq-project'
              docker.withRegistry('', 'dockerHub') {
                memoryImage.push('openpaas-branch-master')
                distributedImage.push('openpaas-branch-master')
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