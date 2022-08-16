import net.sf.json.groovy.JsonSlurper
import groovy.json.*

def token = "ghp_X7IHDUgMLjz8vOjXIURFPHT0vW8IyW2V7yA" + "D"

properties([
  parameters([
    choice(name: 'RELEASE', choices: '17\n11\n8\n', description: 'Use which Multiplexing'),
    booleanParam(defaultValue: false,
                description: 'Change status to pre-release',
                name: 'prerelease'),
    booleanParam(defaultValue: false,
                description: 'Change status to release',
                name: 'release'),
    string(name: 'TYPE', default: '', description: 'standard/extended')
  ])
])
def typePrefix = params.TYPE ? params.TYPE.capitalize() : "Extended"
node('artifact.checker') {
  URL releaseUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/releases")
  def releases = new JsonSlurper().parseText(releaseUrl.text.trim())
  def latest_releases = releases.findAll {it.get("name").contains(typePrefix)}
  if (latest_releases.size() >= 1) {
    def latest_release = latest_releases[0]
    println latest_release
    def api_url = latest_release.get("url")
    println api_url
    def prerelease = false
    if (params.prerelease == true && params.release == false) {
      prerelease = true
    }
    if (params.prerelease == true || params.release == true) {
      sh "curl -X PATCH -H \"Accept: application/vnd.github.v3+json\" -H \"Authorization: token ${token}\" ${api_url} -d '{\"draft\":false,\"prerelease\":${prerelease}}'"
    }
  }
}
                    
