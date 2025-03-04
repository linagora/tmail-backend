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
                dir("twake-calendar-side-service") {
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
    }
    post {
        always {
            deleteDir() /* clean up our workspace */
        }
    }
}