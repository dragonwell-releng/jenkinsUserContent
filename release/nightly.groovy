BUILDER = "http://ci.dragonwell-jdk.io/userContent/utils/nightly11.sh"
HEAD = "OpenJDK${params.RELEASE}U-jdk_x64_linux"

pipeline {
    agent {
        label "ossworker"
    }
    parameters {
        choice(name: 'RELEASE', choices: '17\n11\n8\n', description: 'Use which Multiplexing')
    }
    options {
        ansiColor('xterm')
    }
    stages {
        stage('nightlyDocker') {
            steps {
                script {
                    copyArtifacts(
                            projectName: "build-scripts/openjdk${params.RELEASE}-pipeline",
                            filter: "**/${HEAD}*dragonwell*tar.gz",
                            selector: lastSuccessful(),
                            fingerprintArtifacts: true,
                            target: "workspace/target/",
                            flatten: true)

                    def releases = findFiles excludes: '', glob: "workspace/target/${HEAD}*dragonwell*tar.gz"

                    sh "wget ${BUILDER} -O build.sh"
                    sh "sh build.sh ${releases[0].path} ${params.RELEASE}"
                }
            }
        }
    }
}