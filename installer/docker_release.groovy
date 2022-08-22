import groovy.json.*

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
    )
  ])
])

if (!["standard", "extended"].contains(params.TYPE))
  error "Invalid type"

def containersRepo = "git@github.com:dragonwell-releng/dragonwell-containers.git"
def containersBranch = "main"
def typePrefix = params.TYPE ? params.TYPE.capitalize() : "Extended"
releaseMap = [:]

def recordByArch(platform, name, downloadUrl) {
  def childMap = releaseMap.get(platform) ? releaseMap.get(platform) : [:]
  def templateMap = ["sha256": "", "url": ""]
  def key = name.contains("sha256.txt") ? "sha256" : "url"
  def value = downloadUrl
  if (key == "sha256") {
    dir(env.WORKSPACE) {
      sh "rm -rf sha256.txt && wget ${downloadUrl} -O sha256.txt"
      def content = readFile "sha256.txt"
      value = content.split(" ")[0].trim()
    }
  }
  def arch = ""
  if (name.contains("aarch64"))
    arch = "aarch64"
  else if (name.contains("x64"))
    arch = "x64"
  else if (name.contains("x86"))
    arch = "x86"
  if (arch) {
    def tmp = childMap.get(arch) ? childMap.get(arch) : templateMap
    tmp[key] = value
    childMap[arch] = tmp
  }
  releaseMap[platform] = childMap
}

def replaceStrInDockerfile(absoluteFilePath, arch, os, version) {
  def content = readFile absoluteFilePath
  def baseName = absoluteFilePath.split("/")[-1]
  def newContent = ""
  def flags = ["x86": false, "x64": false, "aarch64": false, "type": false, "match_type": false]
  for (line in content.split("\n")) {
    if (line.contains("ENV JAVA_VERSION")) {
      newContent += "ENV JAVA_VERSION ${version}\n"
    } else if (line.contains("EDITION") && line.contains("=")) {
      flags["type"] = true
      if (params.TYPE == "extended")
        flags["match_type"] = true
      newContent += "${line}\n"
    } else if (flags["type"] && !flags["match_type"] && params.TYPE == "standard" && line.contains("else")) {
      flags["match_type"] = true
      newContent += "${line}\n"
    } else if (line.contains(")") && line.contains("|")) {
      if (line.contains("aarch64") || line.contains("arm64"))
        flags["aarch64"] = true
      else if (line.contains("amd64") || line.contains("x86-64"))
        flags["x64"] = true
      else if (line.contains("x86") && !line.contains("x86-64"))
        flags["x86"] = true
      newContent += "${line}\n"
    } else if (line.contains("ESUM=") && flags["match_type"]) {
      def record = false
      for (e in flags) {
        if (e.key != "type" && e.key != "match_type" && e.value) {
          def esum = line.split("'")[1]
          newContent += line.replace(esum, releaseMap[os].get(e.key).get("sha256")) + "\n"
          record = true
          break
        }
      }
      if (!record)
        newContent += "${line}\n"
    } else if (line.contains("BINARY_URL=") && flags["match_type"]) {
      def record = false
      for (e in flags) {
        if (e.key != "type" && e.key != "match_type" && e.value) {
          def url = line.split("'")[1]
          newContent += line.replace(url, releaseMap[os].get(e.key).get("url")) + "\n"
          record = true
          e.value = false
          break
        }
      }
      if (!record)
        newContent += "${line}\n"
    } else {
      if (line.contains("fi \\")) {
        flags["match_type"] = false
        flags["type"] = false
      }
      newContent += "${line}\n"
    }
  }
  writeFile file:absoluteFilePath, text: newContent, encoding: "UTF-8"
  sh "git diff"
  //sh "git add . && git commit -m 'Update ${platform} ${os} ${baseName}' && git push"
}

node('ossworker' && 'dockerfile') {
  def repoBaseName = containersRepo.split(":")[-1].split("/")[-1].split("\\.")[0]
  URL releaseUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/releases")
  def releases = new JsonSlurper().parseText(releaseUrl.text.trim())
  def releaseCard = releases.findAll { it.get("prerelease") == false && it.get("name").contains(typePrefix)}
  if (releaseCard.size() > 0) {
    def release = releaseCard[0]
    def releaseName = release.get("name")
    def tagName = release.get("tag_name")
    def assets = release.get("assets")
    sh(script: "docker run  registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:${tagName.replace("+", ".")}_x86_64_slim java -version 2> tmpt")
    def fullVersionOutput = sh(script: "cat tmpt", returnStdout: true).trim()
    def jdkVersion = fullVersionOutput.split("\n")[1].split("build ")[1].split("\\)")[0].trim()
    def finalTag = jdkVersion
    if (params.RELEASE == "8") {
      jdkVersion = (tagName.split("_")[-1].split("-")[0] + "-" + fullVersionOutput.split("-")[-1].split("\\)")[0]).split(",")[0].trim()
      finalTag = tagName.split("_")[0].split("-")[-1].trim()
    } else if (params.RELEASE == "11") {
      jdkVersion = "jdk-" + jdkVersion
      finalTag = finalTag.replace("+", ".")
    } else if (params.RELEASE == "17") {
      jdkVersion = "jdk-" + tagName.split("_jdk")[0].split("-")[-1].trim()
      finalTag = tagName.split("_jdk")[0].split("-")[-1].replace("+", ".").trim()
    }
    println """* release name: ${releaseName}
* tag name: ${tagName}
* jdk version: ${jdkVersion}
* full version: ${fullVersionOutput}
* final tag: ${finalTag}"""
    for (asset in assets) {
      def assetName = asset.get("name")
      def assetDownloadUrl = asset.get("browser_download_url")
      if (assetName.contains("windows")) {
        recordByArch("windows", assetName, assetDownloadUrl)
      } else if (assetName.contains("linux") && !assetName.contains("alpine")) {
        recordByArch("linux", assetName, assetDownloadUrl)
      } else if (assetName.contains("alpine")) {
        recordByArch("alpine", assetName, assetDownloadUrl)
      }
    }
    println """* releaseMap: ${releaseMap}"""
    dir(env.WORKSPACE) {
      def exist = fileExists repoBaseName
      if (!exist)
      git branch: containersBranch, url: containersRepo
      dir(repoBaseName) {
        sh "git checkout ${containersBranch} && git reset --hard && git clean -df && git pull origin ${containersBranch}"
      }
    }
    def workdir = "${env.WORKSPACE}/${repoBaseName}"
    dir(workdir) {
      def foundDockerfiles = sh(returnStdout: true, script: "find -iname Dockerfile*")
      if (foundDockerfiles) {
        def dockerfiles = foundDockerfiles.split("\n")
        for (dockerfile in dockerfiles) {
          def splited = dockerfile.split("/")
          def releaseVersion = splited[1]
          def releaseOS = splited[2]
          def fileBaseName = splited[-1]
          if (fileBaseName.contains("Dockerfile.releases") && releaseVersion == params.RELEASE) {
            def fileSuffix = fileBaseName.split("\\.")[-1]
            def arch = ""
            def os = releaseOS == "windows" ? releaseOS : releaseOS == "alpine" ? releaseOS : "linux"
            if (fileSuffix == "full") {
              arch = "full"
            } else {
              if (fileBaseName.contains("aarch64"))
                arch = "aarch64"
              else if (fileBaseName.contains("x64") || fileBaseName.contains("x86_64"))
                arch = "x64"
              else if (fileBaseName.contains("x86"))
                arch = "x86"
            }
            if (releaseOS != "windows")
            replaceStrInDockerfile(dockerfile, arch, os, jdkVersion)
          }
        }
      }
      def oldVersions = readFile "version"
      def newVersions = ""
      for (oldVersion in oldVersions.split("\n")) {
        if (oldVersion.contains("-${params.TYPE}-${params.RELEASE}")) {
          newVersions += tagName + "\n"
        } else {
          newVersions += oldVersion + "\n"
        }
      }
      if (oldVersions.trim() == newVersions.trim()) {
        newVersions += tagName
      }
      writeFile file:"version", text: newVersions
      sh "git diff"
      //sh "git add . && git commit -m 'Update dragonwell${params.RELEASE}-${params.TYPE} version' && git push"
    }
  } 
}
