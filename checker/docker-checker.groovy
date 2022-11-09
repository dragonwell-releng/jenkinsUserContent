import net.sf.json.groovy.JsonSlurper
import groovy.json.*

GITHUBTAG = ""

properties([
  parameters([
    choice(
      choices: [
        "17",
        "11",
        "8"
      ].join("\n"),
      description: 'Use which Multiplexing',
      name: 'RELEASE'
    ),
    string(
      description: 'Release type, such as extended, standard',
      name: 'TYPE'
    ),
  ])
])

def typePrefix = params.TYPE ? params.TYPE.capitalize() : "Extended"
  
node("artifact.checker") {
  URL apiUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/releases")
  def card = new JsonSlurper().parseText(apiUrl.text.trim()).findAll { it.get("prerelease") == true && it.get("name").contains(typePrefix) }
  if (card.size() >= 1) {
    GITHUBTAG = card[0].get("tag_name")
    println GITHUBTAG
    if (params.RELEASE == "17") {
      // docker 不允许用+
      GITHUBTAG = GITHUBTAG.replace("+", ".")
    }
    EDITION = GITHUBTAG.split("-")[1]
    FULL_VERSION = GITHUBTAG.split("_jdk")[0].split("-")[-1]
    VERSION = FULL_VERSION.split("\\.")[0]
    OPT = GITHUBTAG.split("-")[-1]
  }
}


jobs = [:]
DOCKER_REPO = "dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell"
jobs["x64_linux"] = {
  node("linux&&x64&&dockerChecker") {
    def tag = "${VERSION}-${EDITION}-${OPT}-anolis-slim"
    sh "docker pull ${DOCKER_REPO}:${tag}"
    def res = sh(script: "docker run ${DOCKER_REPO}:${tag}  /opt/java/openjdk/bin/java -version", returnStatus: true)
    if (res)
      error "test failed"
  }
}
jobs["aarch64_linux"] = {
  node("linux&&aarch64&&dockerChecker") {
    def tag = "${VERSION}-${EDITION}-${OPT}-anolis-slim"
    sh "docker pull ${DOCKER_REPO}:${tag}"
    def res = sh(script: "docker run ${DOCKER_REPO}:${tag}  /opt/java/openjdk/bin/java -version", returnStatus: true)
    if (res)
      error "test failed"
  }
}

if (params.RELEASE != "8") {
  jobs["x64_alpine_linux"] = {
    node("linux&&x64&&dockerChecker") {
      def tag = "${VERSION}-${EDITION}-${OPT}-alpine"
      sh "docker pull ${DOCKER_REPO}:${tag}"
      def res = sh(script: "docker run ${DOCKER_REPO}:${tag} /opt/java/openjdk/bin/java -version", returnStatus: true)
      if (res)
        error "test failed"
    }
  }
}

parallel jobs
