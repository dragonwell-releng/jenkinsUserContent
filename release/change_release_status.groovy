import net.sf.json.groovy.JsonSlurper
import groovy.json.*

pipeline {
    agent none
    parameters {
        choice(name: 'RELEASE', choices: '17\n11\n8\n', description: 'Use which Multiplexing')
        booleanParam(defaultValue: false,
                description: 'Change status to pre-release',
                name: 'prerelease')
        booleanParam(defaultValue: false,
                description: 'Change status to release',
                name: 'release')
    }
    stages {
        stage('Check status') {
            agent {
                label 'artifact.checker'
            }
            steps {
                script {
                    URL apiUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/releases")
                    def latest_release = new JsonSlurper().parseText(apiUrl.text.trim())[0]
                    println latest_release
                    def api_url = latest_release.get("url")
                    println api_url
                    def prerelease = false
                    if (params.prerelease == true && params.release == false) {
                      prerelease = true
                    }
                    if (params.prerelease == true || params.release == true) {
                      sh "curl -X PATCH -H \"Accept: application/vnd.github.v3+json\" -H 'Authorization: token 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' ${api_url} -d '{\"draft\":false,\"prerelease\":${prerelease}}'"
                    }
                }
            }
        }
    }
}
                    
