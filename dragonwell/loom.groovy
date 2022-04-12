pipeline {
    options {
        durabilityHint('PERFORMANCE_OPTIMIZED')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactDaysToKeepStr: '15'))
    }
    parameters {
        choice(name: 'ThreadMode', choices: 'Virtual\nSystem\nOptimized',
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
                    currentBuild.displayName = "netty ${params.ThreadMode}"

                    def test = ""
                    switch (params.ThreadMode) {
                        case "Virtual": test = 'netty-virtual'; break;
                        case "System":  test = 'netty'; break;
                        case "Optimized":  test = 'netty-optimized'; break;
                    }
                    checkout([$class                           : 'GitSCM',
                              branches                         : [[name: "*/master"]],
                              doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [],
                              userRemoteConfigs                : [[url: 'https://github.com/joeyleeeeeee97/FrameworkBenchmarks.git']]])
                    sh "git clean -ffdx"
                    sh "./tfb --network-mode='host'  --database-host='172.31.141.248' --client-host='172.31.141.248' --server-host='172.31.141.247' --test ${test}"
                    def results = findFiles(glob: 'results/**/results.json')
                    sh "python3 prettytable_result_parser.py --files ${results[0].path} --data latencyAvg latencyMax"
                    archiveArtifacts artifacts: 'results/**/results.json', followSymlinks: false
                }
            }
        }
    }
}