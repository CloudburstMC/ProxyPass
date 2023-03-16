pipeline {
    agent any
    tools {
        jdk 'Java 17'
    }
    options {
        buildDiscarder(logRotator(artifactNumToKeepStr: '5'))
    }
    stages {
        stage ('Build') {
            steps {
                sh './gradlew shadowJar'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'build/libs/ProxyPass.jar', fingerprint: true
                }
            }
        }
    }
}
