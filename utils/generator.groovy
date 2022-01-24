pipeline {
    agent {
        label 'serverless.worker'
    }

    parameters {
        string(name: 'REPO',
                defaultValue: "spring-petclinic",
                description: 'repo')
        string(name: 'BRANCH',
                defaultValue: "master",
                description: 'git branch')
        string(name: 'JAVA_HOME',
                defaultValue: "/home/joeylee.lz/jdk",
                description: 'java home')
    }

    stages {
        stage('Run') {
            agent {
                label 'serverless.worker'
            }
            steps {
                script {
                    echo "Prepare test materials"
                    sh "rm -rf spring-petclinic"
                    sh "git clone git@github.com:spring-projects/spring-petclinic.git"
                    dir("spring-petclinic") {
                        sh "ls"
                        sh "java -version"
                        sh "JAVA_HOME=${params.JAVA_HOME} MAVEN_OPTS=-agentlib:native-image-agent=config-output-dir=run-config LD_LIBRARY_PATH=${params.JAVA_HOME}/lib/sat/libs/ ./mvnw package || true"
                        sh "JAVA_HOME=${params.JAVA_HOME} LD_LIBRARY_PATH=${params.JAVA_HOME}/lib/sat/libs/ ${params.JAVA_HOME}/bin/java -agentlib:native-image-agent=config-merge-dir=run-configs -jar target/*.jar &"
                        sleep 10
                        sh "ps -ef | grep agentlib|  grep run-configs  | awk '{ print \$2 }' | xargs kill -9"
                        sh "tar czvf config.tar.gz run-configs"
                        sh "osscmd --host=oss-cn-hangzhou.aliyuncs.com --id=LTAI4FzxWYJNGoNLd9XJB42p    --key=4GGrozq5zH8lMkJnZO46ryCXnljnbZ put  config.tar.gz oss://reflection-configs/${params.REPO}-${params.BRANCH}.tar.gz"

                    }
                }

            }
        }
    }
}
