import groovy.json.*

properties([
  parameters([
    string(
      description: 'JDK download url',
      name: 'JDK_URL'
    ),
    string(
      description: 'JDK signature document download url',
      name: 'GPG_URL'
    )
  ])
])

if (!params.JDK_URL || !params.GPG_URL)
  error "JDK_URL or GPG_url is invalid!"

node('gpgsign') {
  timestamps {
    timeout(time: 1, unit: 'HOURS') {
      try {
        dir(env.WORKSPACE) {
          cleanWs notFailBuild: true, deleteDirs: true
          def jdk = params.JDK_URL.split('/')[-1].trim()
          def sign = params.GPG_URL.split('/')[-1].trim()
          sh "curl -Lks -C - --retry 3 ${params.JDK_URL} -o ${jdk}"
          sh "sha256sum ${jdk}"
          sh "curl -Lks -C - --retry 3 ${params.GPG_URL} -o ${sign}"
          sh "sha256sum ${sign}"
          sh "gpg --verify ${sign} 2>&1 | grep 'Good signature' > temp.txt"
          def verify = readFile('temp.txt')
          if (!verify.contains('Good signature'))
            error 'Bad signature!'
          println 'Good signature!'
        }
      } catch (Exception e) {
        throw new Exception(e)
      } finally {
        cleanWs notFailBuild: true, deleteDirs: true
      }
    }
  }
}
