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
    )
  ])
])
        
node("artifact.checker") {
  URL apiUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/releases")
  def card = new JsonSlurper().parse(apiUrl)
  GITHUBTAG = card[0].get("tag_name")
  if (params.RELEASE == "17") {
      // docker 不允许用+
      GITHUBTAG = GITHUBTAG.replace("+", ".")
  }
}

jobs = [:]
jobs["x64_linux"] = node("linux&&x64&&dockerChecker") {
  sh "docker pull registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:${GITHUBTAG}_x86_64_slim"
  version = sh(script: "docker run  registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:${GITHUBTAG}_x86_64_slim  /opt/alibaba/dragonwell${params.RELEASE}/bin/java -version", returnStdout: true).split()
  print version
}
jobs["aarch64_linux"] = node("linux&&aarch64&&dockerChecker") {
  sh "docker pull registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:${GITHUBTAG}_aarch64_slim"
  version = sh(script: "docker run  registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:${GITHUBTAG}_aarch64_slim  /opt/alibaba/dragonwell${params.RELEASE}/bin/java -version", returnStdout: true).split()
  print version
}
jobs["x64_alpine-linux"] = node("linux&&x64&&dockerChecker") {
  if (params.RELEASE != "8") {
    sh "docker pull registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:${GITHUBTAG}_alpine_x86_64"
    def version = sh(script: "docker run  registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:${GITHUBTAG}_alpine_x86_64 /opt/alibaba/dragonwell${params.RELEASE}/bin/java -version", returnStdout: true).split()
    print version
  }
}

parallel jobs
