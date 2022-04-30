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
        stage('batchRun') {
            agent {
                label 'artifact.checker'
            }
            steps {
                build quietPeriod: 10, propagate: false, job: 'loom', parameters: [string(name: 'ThreadMode', value: 'System'), string(name: 'FrameworkMode', value: 'Sync'), string(name: 'Test', value: 'netty')]

                build quietPeriod: 10, propagate: false, job: 'loom', parameters: [string(name: 'ThreadMode', value: 'Virtual'), string(name: 'FrameworkMode', value: 'Sync'), string(name: 'Test', value: 'netty')]

                build quietPeriod: 10, propagate: false, job: 'loom', parameters: [string(name: 'ThreadMode', value: 'Optimized'), string(name: 'FrameworkMode', value: 'Sync'), string(name: 'Test', value: 'netty')]

                build quietPeriod: 10, propagate: false, job: 'loom', parameters: [string(name: 'ThreadMode', value: 'PreferOio'), string(name: 'FrameworkMode', value: 'Sync'), string(name: 'Test', value: 'netty')]


                build quietPeriod: 10, propagate: false, job: 'loom', parameters: [string(name: 'ThreadMode', value: 'System'), string(name: 'FrameworkMode', value: 'Async'), string(name: 'Test', value: 'netty')]

                build quietPeriod: 10, propagate: false, job: 'loom', parameters: [string(name: 'ThreadMode', value: 'Virtual'), string(name: 'FrameworkMode', value: 'Async'), string(name: 'Test', value: 'netty')]

                build quietPeriod: 10, propagate: false, job: 'loom', parameters: [string(name: 'ThreadMode', value: 'Optimized'), string(name: 'FrameworkMode', value: 'Async'), string(name: 'Test', value: 'netty')]



            }
        }
    }
}