pipeline {
    agent any

    tools {
        maven 'M3.3.9'
    }

    triggers {
        pollSCM('H * * * *')
    }

    stages {
        stage('build') {
            steps {
                withMaven(options: [junitPublisher(ignoreAttachments: false), artifactsPublisher()]) {
//                    TODO remove -DskipTests when java problem is solved
                    sh 'mvn -DskipTests -DincludeSrcJavadocs clean source:jar install'
                }
            }
        }

    }


    post {
//        TODO deploy snapshot to maven repo
        failure {
            // notify users when the Pipeline fails
            mail to: 'steen@lundogbendsen.dk',
                    subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
                    body: "Something is wrong with ${env.BUILD_URL}"
        }
    }
}

