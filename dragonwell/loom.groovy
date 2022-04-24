pipeline {
    options {
        durabilityHint('PERFORMANCE_OPTIMIZED')
        buildDiscarder(logRotator(numToKeepStr: '999999', artifactDaysToKeepStr: '3650'))
    }
    parameters {
        choice(name: 'ThreadMode', choices: 'Virtual\nSystem\nOptimized\nVirtualBiz\nPreferOio',
                description: 'Use virtual or system')
        choice(name: 'FrameworkMode', choices: 'Sync\nAsync',
                description: 'Submit to a biz executor or not')
        choice(name: 'Test', choices: 'netty\nspring',
                description: 'which framework we use?')
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
                    currentBuild.displayName = "${params.Test} ${params.ThreadMode} ${params.FrameworkMode}"

                    if (params.ThreadMode == "Async" && params.Test == "spring") {
                        return
                    }


                    def test = ""
                    switch (params.ThreadMode) {
                        case "Virtual":
                            if (params.FrameworkMode == "Sync")
                                test = "${params.Test}-virtual"
                            else
                                test = "${params.Test}-asyncv"
                            break;
                        case "System":
                            if (params.FrameworkMode == "Sync") {
                                if (params.Test == "spring")
                                    test = "${params.Test}"
                                else
                                    test = "${params.Test}"
                            }
                            else
                                test = "${params.Test}-asyncp"
                            break;
                        case "Optimized":
                            if (params.FrameworkMode == "Sync")
                                test = "${params.Test}-optimized"
                            else
                                test = "${params.Test}-asynco"
                            break;
                        case "VirtualBiz":
                            test = "spring-virtualbiz"
                            break;
                        case "PreferOio":
                            test = "netty-preferoio"
                            break;
                    }
                    checkout([$class                           : 'GitSCM',
                              branches                         : [[name: "*/master"]],
                              doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [],
                              userRemoteConfigs                : [[url: 'https://github.com/joeyleeeeeee97/FrameworkBenchmarks.git']]])
                    sh "git clean -ffdx"
                    sh "./tfb --network-mode='host'  --database-host='172.31.141.248' --client-host='172.31.141.248' --server-host='172.31.141.247' --concurrency-levels 4 8 16 32 64 128  --pipeline-concurrency-levels 32 64 128 256  --duration 30 --test ${test}"
                    def results = findFiles(glob: 'results/**/results.json')
                    sh "python3 prettytable_result_parser.py --files ${results[0].path} --data latencyAvg latencyMax"
                    archiveArtifacts artifacts: 'results/**/results.json', followSymlinks: false
                }
            }
        }
    }
}