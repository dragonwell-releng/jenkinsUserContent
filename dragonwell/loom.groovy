pipeline {
    options {
        durabilityHint('PERFORMANCE_OPTIMIZED')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactDaysToKeepStr: '15'))
    }
    parameters {
        choice(name: 'ThreadMode', choices: 'Virtual\nSystem\nOptimized',
                description: 'Use virtual or system')
        choice(name: 'FrameworkMode', choices: 'Sync\nAsync',
                description: 'Submit to a biz executor or not')
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
                    currentBuild.displayName = "netty ${params.ThreadMode} ${params.FrameworkMode}"

                    def test = ""
                    switch (params.ThreadMode) {
                        case "Virtual":
                            if (params.FrameworkMode == "Sync")
                                test = 'netty-virtual';
                            else
                                test = 'netty-asyncv'
                            break;
                        case "System":
                            if (params.FrameworkMode == "Sync")
                                test = 'netty';
                            else
                                test = 'netty-asyncp'
                            break;
                        case "Optimized":
                            if (params.FrameworkMode == "Sync")
                                test = 'netty-optimized';
                            else
                                test = "netty-asynco"
                            break;
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