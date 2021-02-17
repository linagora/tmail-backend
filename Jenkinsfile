pipeline {
    agent any 
    
    stages {
        stage('Git checkout') {
            steps {
                git 'https://github.com/linagora/openpaas-james'
                sh 'git submodule init'
                sh 'git submodule update'
            }
        }
        stage('Compile') {
            sh 'mvn install -Dmaven.javadoc.skip=true -DskipTests -T1C'
        }
        stage('Test') {
            steps {
                dir("openpaas-james") {
                    sh 'mvn -B surefire:test'
                }
            }
        }
    }
}