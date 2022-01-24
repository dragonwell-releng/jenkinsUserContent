pipeline {
    agent {
        label "FrameworkBenchmarks"
    }
    parameters {
        string(name: 'TEST',
                defaultValue: "netty",
                description: "framework test")
        string(name: 'DOCKER',
                defaultValue: "11-nightly",
                description: "test docker image")
        string(name: 'JAVAARGS',
                defaultValue: "",
                description: "customized jvm arguments")

    }
    options {
        ansiColor('xterm')
    }
    stages {
        stage('framework') {
            steps {
                script {
                    dir("/root/FrameworkBenchmarks") {
                        sh "git checkout && git pull"
                        if (params.JAVAARGS != "") {
                            sh "./javaArgs ${params.JAVAARGS} frameworks/Java/${params.TEST}/${params.TEST}.dockerfile"
                        }
                        sh "./dragonwell ${params.DOCKER} frameworks/Java/${params.TEST}/${params.TEST}.dockerfile"
                        sh "./tfb  --test ${TEST}"

                    }


                }
            }
        }
    }
}