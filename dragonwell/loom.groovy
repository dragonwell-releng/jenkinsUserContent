pipeline {
    options {
        durabilityHint('PERFORMANCE_OPTIMIZED')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactDaysToKeepStr: '15'))
    }
    parameters {
        choice(name: 'ThreadMode', choices: 'Virtual\nSystem',
                description: 'Use virtual or system')
    }
    agent {
        label 'TFB:loom'
    }
    stages {
        stage('iterateHistory') {
            agent {
                label 'TFB:loom'
            }
            steps {
                script {
                    def branch = params.ThreadMode == "Virtual" ? "master" : "system"
                    checkout([$class                           : 'GitSCM',
                              branches                         : [[name: "*/${branch}"]],
                              doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [],
                              userRemoteConfigs                : [[url: 'https://github.com/joeyleeeeeee97/FrameworkBenchmarks.git']]])
                    sh "./tfb --network-mode='host'  --database-host='172.31.141.248' --client-host='172.31.141.248' --server-host='172.31.141.247' --test netty"
                }
            }
        }
    }
}