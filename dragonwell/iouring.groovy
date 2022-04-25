def connections = [1, 16, 64, 128, 256, 512, 1024]
def threads = [4]
def duration = 30

pipeline {
    options {
        durabilityHint('PERFORMANCE_OPTIMIZED')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactDaysToKeepStr: '15'))
    }
    agent {
        label 'binaries'
    }
    parameters {
        choice(name: 'MODE', choices: 'epoll\niouring',
                description: 'which is better? we will see')
    }

    stages {
        stage('benchmark') {
            parallel {
                stage('server') {
                    agent {
                        label 'iouring_server'
                    }
                    steps {
                        script {
                            try {
                                def mode = params.MODE == "iouring" ? "true" : "false"
                                timeout(time: 5, unit: 'MINUTES') {
                                    sh "java -DtryIOUring=${mode} -jar netty-example-0.1-jar-with-dependencies.jar"
                                }
                            } catch (err) {
                                //ignore
                            }
                        }
                    }
                }
                stage('client') {
                    agent {
                        label 'iouring_client'
                    }
                    steps {
                        script {
                            sleep 10
                            for (c in connections) {
                                for (t in threads) {
                                    sh "/root/wrk/wrk " +
                                            "-H 'Host: 172.31.141.247'" +
                                            " -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7'" +
                                            " -H 'Connection: keep-alive' " +
                                            "--latency -d 10 -c ${c} --timeout 8 -t ${t} " +
                                            "http://192.168.0.235:8080/plaintext"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}