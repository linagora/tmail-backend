pipeline {
    agent any 
    
    stages {
        stage('Git checkout') {
            steps {
                git 'https://github.com/linagora/openpaas-james'
            }
        }
        stage('Test') {
            steps {
                sh 'git submodule init'
                sh 'git submodule update'
                sh 'mvn install -Dmaven.javadoc.skip=true -DskipTests -T1C'
                dir("openpaas-james") {
                    sh 'mvn -B test'
                }
            }
        }
    }
}