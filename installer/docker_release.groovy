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
    ),
    string(
      description: 'X64 jdk url in github/oss',
      name: 'JDK_URL'
    ),
    booleanParam(
      defaultValue: true,
      description: 'check',
      name: 'CHECK'
    )
  ])
])

if (!["standard", "extended"].contains(params.TYPE))
  error "Invalid type"

def containersRepo = "git@github.com:dragonwell-releng/dragonwell-containers.git"
def containersBranch = "main"
def typePrefix = params.TYPE ? params.TYPE.capitalize() : "Extended"
releaseMap = [:]

// check map
checkRuleMap = [
  "8": [
         "centos": [""],
         "ubuntu": [""],
         "anolis": [""]
       ],
  "11": [
         "centos": [""],
         "ubuntu": [""],
         "anolis": [""],
         "alpine": [""]
       ],
  "17": [
         "centos": [""],
         "ubuntu": [""],
         "anolis": [""],
         "alpine": [""]
       ]
]

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
  def updateFileName = absoluteFilePath.split("\\./")[1]
  def diff = sh(returnStdout: true, script: "git diff")
  if (diff) {
    sh "git add . && git commit -m 'Update ${updateFileName}' && git push"
  }
}

def getTagsByRuleMap(tag, releaseVersion, edition) {
  def tags = [:]
  for (e1 in checkRuleMap) {
    def version = e1.key
    if (version == releaseVersion) {
      for (e2 in e1.value) {
        def os = e2.key
        for (e3 in e2.value) {
          def arch = e3
          def suffixs = os != "anolis" ? [""] : releaseVersion != "17" ? ["-slim"] : [""]
          if (!arch) {
            for (suffix in suffixs) {
              if (edition == "extended" || releaseVersion == "17") {
                tags["${releaseVersion}-${os}${suffix}"] = false
                if (os == "anolis" && suffix && !suffix.contains("-slim"))
                  tags["${releaseVersion}${suffix}"] = false
              }
              tags["${releaseVersion}-${edition}-ga-${os}${suffix}"] = false
              tags["${tag}-${edition}-ga-${os}${suffix}"] = false
            }
          } else if (arch == "latest") {
            if (edition == "extended" || releaseVersion == "17")
              tags["latest"] = false
          }
        }
      }
    }
  }
  return tags
}

def getFalseElementInMap(map) {
  return map.findAll {e -> !e.value}
}

node('ossworker && dockerfile && x64') {
  // get jdkVersion, tagName, releaseAsset
  def repoBaseName = containersRepo.split(":")[-1].split("/")[-1].split("\\.")[0]
  URL releaseUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/releases")
  def releases = new JsonSlurper().parseText(releaseUrl.text.trim())
  def releaseCard = releases.findAll { it.get("prerelease") == true && it.get("name").contains(typePrefix)}
  if (releaseCard.size() > 0) {
    def release = releaseCard[0]
    def releaseName = release.get("name")
    def tagName = release.get("tag_name")
    def assets = release.get("assets")
    def test_dir = "test_dir"
    sh "rm -rf ${test_dir} && mkdir -p ${test_dir}"
    dir(test_dir) {
      sh "wget ${params.JDK_URL} -O jdk.tar.gz && tar zxvf jdk.tar.gz && rm -rf jdk.tar.gz"
      def jdk_dir = sh(returnStdout: true, script: "ls").trim()
      sh "${jdk_dir}/bin/java -version 2> ../tmpt"
    }
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

  // record asset in map
    for (asset in assets) {
      def assetName = asset.get("name")
      if (assetName.contains(".json") || assetName.contains(".sig"))
        continue
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
      if (!exist) {
        sh "mkdir -p repoBaseName"
        dir(repoBaseName) {
          git branch: containersBranch, url: containersRepo
        }
      }
      dir(repoBaseName) {
        sh "git checkout ${containersBranch} && git reset --hard HEAD~10 && git clean -df && git pull origin ${containersBranch}"
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
          def releaseOS = splited[-2]
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
      def newVersions = oldVersions
      if (!oldVersions.split("\n").contains(tagName)) {
        newVersions += "\n" + tagName
        //for (oldVersion in oldVersions.split("\n")) {
        //  if (oldVersion.contains("-${params.TYPE}-${params.RELEASE}")) {
        //    newVersions += tagName + "\n"
        //  } else {
        //    newVersions += oldVersion + "\n"
        //  }
        //}
      }
      writeFile file:"version", text: newVersions
      def diff = sh(returnStdout: true, script: "git diff")
      if (diff) {
        sh "git add . && git commit -m 'Update dragonwell${params.RELEASE}-${params.TYPE} version' && git push"
      }
    }

    // check
    def tags = getTagsByRuleMap(finalTag, params.RELEASE, params.TYPE)
    def imageRegistry = "registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell"
    println """* tags: ${tags}"""
    dir(env.WORKSPACE) {
      sh "rm -rf get_acr_tags.py"
      sh "wget https://raw.githubusercontent.com/dragonwell-releng/jenkinsUserContent/master/utils/get_acr_tags.py -O get_acr_tags.py"
      sh "cp /root/.docker/getImageTags.sh ."
      def req = ""
      for (i in 0..5) {
        req = sh(returnStdout: true, script: "bash getImageTags.sh 1")
        if (req) break
        sleep 2
      }
      def resp = new JsonSlurper().parseText(req)
      def totalCount = resp.get("body").get("TotalCount")
      println "totalCount: ${totalCount}"
      def recordMap = tags
      sh "date"
      def today = sh(returnStdout: true, script: "date +%s")
      today = today.split("\n")[0].toInteger()
      def baseDate = today - 864000 // 10 days ago
      timeout(time: 1, unit: 'HOURS') {
        dir(workdir) {
          if (recordMap && !params.CHECK) {
            def newTag = "release${params.RELEASE}-${params.TYPE}-${finalTag}"
            def tryCreateTag = sh(returnStatus: true, script: "git tag ${newTag}")
            if (tryCreateTag) {
              sh "git tag -d ${newTag}"
              def tryDelRemoteTag = sh(returnStatus: true, script: "git push origin :${newTag}")
              sh "git tag -a ${newTag} -m ''"
            }
            sh "git push origin ${newTag}"
          }
        }
        while (recordMap) {
          def pageNos = totalCount > 100 ? [1, 2] : [1]
          for (pageNo in pageNos) {
            for (i in 0..5) {
              req = sh(returnStdout: true, script: "bash getImageTags.sh ${pageNo}")
              if (req) break
              sleep 2
        }
            // println req
            resp = new JsonSlurper().parseText(req)
            for (image in resp.get("body").get("Images")) {
              def imageTag = image.Tag
              def updateDate = image.get("ImageUpdate") / 1000
              if (imageTag.contains(finalTag)) {
                println "Image ${imageTag}, today: ${today}, 10 days ago: ${baseDate}, update date: ${updateDate}"
              }
              if (recordMap.containsKey(imageTag) && updateDate >= baseDate) {
                recordMap[imageTag] = true
              }
            }
          }
          recordMap = getFalseElementInMap(recordMap)
          //break // debug
          if (recordMap) {
            sh "date"
            println """* rest tags: ${recordMap}
sleep 1000s..."""
            sleep 1000 // it'll be removed if we can know when existed tag is updated
          }
        }
      }
    }
    
  }
}
