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
      description: 'Pipeline build Number',
      name: 'VERSION'
    ),
    string(
      description: 'Github version',
      name: 'GITHUBVERSION'
    ),
    string(
      description: 'Github tag',
      name: 'GITHUBTAG'
    ),
    string(
      description: 'Build number',
      name: 'BUILDNUMBER'
    ),
    booleanParam(
      defaultValue: true,
      description: 'copy release oss',
      name: 'OSS'
    ),
    booleanParam(
      defaultValue: true,
      description: 'release on github',
      name: 'GITHUB'
    ),
    booleanParam(
      defaultValue: true,
      description: 'release on docker',
      name: 'DOCKER'
    ),
    booleanParam(
      defaultValue: true,
      description: 'clean workspace',
      name: 'CLEAN'
    ),
    booleanParam(
      defaultValue: true,
      description: 'generate wiki content',
      name: 'GENERATE_WIKI'
    ),
    booleanParam(
      defaultValue: false,
      description: 'update wiki',
      name: 'UPDATE_WIKI'
    )
  ])
])

OSS_TOOL = "/home/testuser/ossutil64"
TOKEN = "ghp_X7IHDUgMLjz8vOjXIURFPHT0vW8IyW2V7yA" + "D"
RELEASE_MAP = [:]
CHECKSUM_MAP = [:]

def typePrefix = params.TYPE ? params.TYPE.capitalize() : "Extended"
def targetBranch = params.TYPE.toLowerCase() == "standard" ? "standard" : "master"
def slash = "-"
if (params.RELEASE == "8")
    slash = ""

def tagName4Docker = params.GITHUBTAG
def versionName4OSS = params.VERSION
if (params.RELEASE == "17" || params.RELEASE == "11") {
    tagName4Docker = tagName4Docker.replace("+", ".") // + is not allowed is docker image
    versionName4OSS = versionName4OSS.replace("+", "%2B")
}


DOCKER_IMAGES_TEMPLATE1 = "| registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:VERSION_x86_64 | x86_64 | centos | No |"
DOCKER_IMAGES_TEMPLATE2 = "| registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:VERSION_aarch64 | aarch64 | centos | No |"
DOCKER_IMAGES_TEMPLATE3 = "| registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:VERSION_x86_64_slim | x86_64 | centos | Yes |"
DOCKER_IMAGES_TEMPLATE4 = "| registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:VERSION_aarch64_slim | aarch64 | centos | Yes |"
DOCKER_IMAGES_TEMPLATE5 = "| registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:VERSION_alpine_x86_64 | x86_64 | alpine | No |"

if (params.RELEASE == "8") {
    MIRROS_DOWNLOAD_TEMPLATE = """
# ${params.VERSION}-${typePrefix}

| File name | China mainland | United States |
|---|---|---|
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_aarch64_linux.tar.gz | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_aarch64_linux.tar.gz) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_aarch64_linux.tar.gz) |
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_x64_alpine-linux.tar.gz | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_alpine-linux.tar.gz) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/releases/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_alpine-linux.tar.gz) |
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_x64-linux.tar.gz | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_linux.tar.gz) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/releases/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_linux.tar.gz) |
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_x64_windows.zip | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_windows.zip) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/releases/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_windows.zip) |
"""
} else if (params.RELEASE == "11") {
    def newTitle = params.VERSION.substring(0, params.VERSION.lastIndexOf("."))
    if (params.GITHUBVERSION.contains("-GA")) {
      newTitle = "${newTitle}-${typePrefix}-GA"
    }
    MIRROS_DOWNLOAD_TEMPLATE = """
# ${newTitle}

| File name | China mainland | United States |
|---|---|---|
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_aarch64_linux.tar.gz | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_aarch64_linux.tar.gz) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_aarch64_linux.tar.gz) |
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_x64_alpine-linux.tar.gz | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_alpine-linux.tar.gz) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/releases/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_alpine-linux.tar.gz) |
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_x64-linux.tar.gz | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_linux.tar.gz) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/releases/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_linux.tar.gz) |
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_x64_windows.zip | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_windows.zip) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/releases/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_windows.zip) |
"""
} else {
    MIRROS_DOWNLOAD_TEMPLATE = """
# ${params.GITHUBVERSION}-${typePrefix}

| File name | China mainland | United States |
|---|---|---|
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_aarch64_linux.tar.gz | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_aarch64_linux.tar.gz) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_aarch64_linux.tar.gz) |
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_x64_alpine-linux.tar.gz | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_alpine-linux.tar.gz) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/releases/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_alpine-linux.tar.gz) |
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_x64-linux.tar.gz | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_linux.tar.gz) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/releases/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_linux.tar.gz) |
| Alibaba_Dragonwell_${typePrefix}_jdk-${params.VERSION}_x64_windows.zip | [download](https://dragonwell.oss-cn-shanghai.aliyuncs.com/${versionName4OSS}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_windows.zip) | [download](https://github.com/alibaba/dragonwell${params.RELEASE}/releases/download/${params.GITHUBVERSION}/Alibaba_Dragonwell_${typePrefix}_${versionName4OSS}_x64_windows.zip) |
"""
}


if (params.RELEASE == "8") {
    PARENT_JOB_NAME = ""
    JDK_NAME = ""
    PLATFORMS = ["x64_linux", "x64_windows", "aarch64_linux"]
    REPO = "dragonwell8"
    HEAD = "OpenJDK8U-jdk_"
    BUILDER = "http://ci.dragonwell-jdk.io/userContent/utils/build.sh"
} else if (params.RELEASE == "11") {
    PARENT_JOB_NAME = ""
    JDK_NAME = ""
    PLATFORMS = ["x64_linux", "x64_windows", "x64_alpine-linux", "aarch64_linux"]
    REPO = "dragonwell11"
    HEAD = "OpenJDK11U-jdk_"
    BUILDER = "http://ci.dragonwell-jdk.io/userContent/utils/build11.sh"
} else {
    PARENT_JOB_NAME = ""
    JDK_NAME = ""
    PLATFORMS = ["x64_linux", "x64_windows", "x64_alpine-linux", "aarch64_linux"]
    REPO = "dragonwell17"
    HEAD = "OpenJDK17U-jdk"
    BUILDER = "http://ci.dragonwell-jdk.io/userContent/utils/build17.sh"
}

DOCKER_IMAGE_MAP = [:]

// for Chinese and English version
def updateReleaseNotes() {

}

stage('publishOssGithub') {
  if (params.OSS || params.DOCKER || params.GITHUB) {
    node("ossworker") {
      if (params.CLEAN) {
        sh "rm -rf /home/testuser/jenkins/workspace/dragonwell-oss-installer/workspace/target/"
        copyArtifacts(
                projectName: "build-scripts/openjdk${params.RELEASE}-pipeline",
                filter: "**/${HEAD}*dragonwell*tar.gz*",
                selector: specific("${params.BUILDNUMBER}"),
                fingerprintArtifacts: true,
                target: "workspace/target/",
                flatten: true)
        copyArtifacts(
                projectName: "build-scripts/openjdk${params.RELEASE}-pipeline",
                filter: "**/${HEAD}*dragonwell*zip*",
                selector: specific("${params.BUILDNUMBER}"),
                fingerprintArtifacts: true,
                target: "workspace/target/",
                flatten: true)
      }
      dir("workspace/target/") {
        files = sh(script: 'ls', returnStdout: true).split()
        for (String platform : PLATFORMS) {
          def tailPattern = (platform != "x64_windows") ? "tar.gz" : "zip"
          def tarPattern = ".+${platform}.+${tailPattern}"
          def checkPattern = ".+${platform}.+sha256.txt"
          for (String file : files) {
            final p = file =~ /${tarPattern}/
            final checksum = file =~ /${checkPattern}/
            if (p.matches()) {
              def releaseFile = "Alibaba_Dragonwell_${typePrefix}_${params.VERSION}_${platform}.${tailPattern}"
              sh "mv ${file} ${releaseFile}"
              if (params.OSS)
                sh "${OSS_TOOL} cp -f ${releaseFile} oss://dragonwell/${params.VERSION}/${releaseFile}"
              print "https://dragonwell.oss-cn-shanghai.aliyuncs.com/${params.VERSION}/${releaseFile}"
              RELEASE_MAP["${releaseFile}"] = "https://dragonwell.oss-cn-shanghai.aliyuncs.com/${params.VERSION}/${releaseFile}"
              DOCKER_IMAGE_MAP["${platform}"] = "https://dragonwell.oss-cn-shanghai.aliyuncs.com/${params.VERSION}/${releaseFile}"
            } else if (checksum.matches()) {
              def releaseFile = "Alibaba_Dragonwell_${typePrefix}_${params.VERSION}_${platform}.${tailPattern}.sha256.txt"
              sh "mv ${file} ${releaseFile}"
              if (params.OSS)
                sh "${OSS_TOOL} cp -f ${releaseFile} oss://dragonwell/${params.VERSION}/${releaseFile}"
              print "https://dragonwell.oss-cn-shanghai.aliyuncs.com/${params.VERSION}/${releaseFile}"
              CHECKSUM_MAP["${releaseFile}"] = "https://dragonwell.oss-cn-shanghai.aliyuncs.com/${params.VERSION}/${releaseFile}"
            }
          }
        } 
        if (params.GITHUB) {
          def versionSuffix = params.RELEASE == "11" ? params.VERSION.substring(0, params.VERSION.lastIndexOf(".")) : params.VERSION
          def releaseJson = new JsonBuilder([
                   "tag_name"        : "${params.GITHUBTAG}",
                   "target_commitish": "${targetBranch}",
                   "name"            : "Alibaba_Dragonwell_${typePrefix}_${versionSuffix}",
                   "body"            : "",
                   "draft"           : false,
                   "prerelease"      : true
          ])
          writeFile file: 'release.json', text: releaseJson.toPrettyString()
          def checkReleaseByTag = sh(script: "curl -H \"Authorization:token ${TOKEN}\" https://api.github.com/repos/alibaba/${REPO}/releases/tags/${params.GITHUBTAG}", returnStdout: true)
          writeFile file: 'result.json', text: "${checkReleaseByTag}"
          def id = 0
          if (checkReleaseByTag.contains("id")) {
            println "exist release Alibaba_Dragonwell_${typePrefix}_${versionSuffix} with tag ${params.GITHUBTAG}"
            id = sh(script: "cat result.json | grep id | head -n 1 | awk -F\"[:,]\"  '{ print \$2 }' | awk '{print \$1}'", returnStdout: true).trim()
          } else {
            def release = sh(script: "curl -XPOST -H \"Authorization:token ${TOKEN}\" --data @release.json https://api.github.com/repos/alibaba/${REPO}/releases", returnStdout: true)
            writeFile file: 'result.json', text: "${release}"
            id = sh(script: "cat result.json | grep id | head -n 1 | awk -F\"[:,]\"  '{ print \$2 }' | awk '{print \$1}'", returnStdout: true).trim()
          }
          println "ID: $id"
          for (file in RELEASE_MAP) {
            def assetName = file.key
            def urlAssetName = assetName.replace("+", "%2B")
            sh "curl -Ss -XPOST -H \"Authorization:token ${TOKEN}\" -H \"Content-Type:application/zip\" --data-binary @${assetName} https://uploads.github.com/repos/alibaba/${REPO}/releases/${id}/assets?name=${urlAssetName}"
          }
          for (file in CHECKSUM_MAP) {
            def assetName = file.key
            def urlAssetName = assetName.replace("+", "%2B")
            sh "curl -Ss -XPOST -H \"Authorization:token ${TOKEN}\" -H \"Content-Type:text/plain\" --data-binary @${assetName} https://uploads.github.com/repos/alibaba/${REPO}/releases/${id}/assets?name=${urlAssetName}"
          }
        }
      }
    }
  }
}
stage('publishDocker-x64') {
    if (params.DOCKER) {
      node("docker:x64") {
            sh "docker login"
            def url = DOCKER_IMAGE_MAP["x64_linux"]
            def urlAlpine = DOCKER_IMAGE_MAP["x64_alpine-linux"]
            if (params.RELEASE == "17") {
                url = url.replace("+", "%2B")
                urlAlpine = urlAlpine.replace("+", "%2B")
            }
            sh "wget ${BUILDER} -O build.sh"
            sh "sh build.sh ${url} ${tagName4Docker} ${urlAlpine}"
        }
    }
}
stage('publishDocker-aarch64') {
    if (params.DOCKER) {
        node("docker:aarch64") {
            sh "docker login"
            def url = DOCKER_IMAGE_MAP["aarch64_linux"]
            if (params.RELEASE == "17") {
                url = url.replace("+", "%2B")
            }
            def urlAlpine = ""
            sh "wget ${BUILDER} -O build.sh"
            sh "sh build.sh ${url} ${tagName4Docker} ${urlAlpine}"
        }
    }
}

stage('wiki-update') {
    if (params.GENERATE_WIKI || params.UPDATE_WIKI) {
        node("ossworker") {
            sh "rm -rf workspace/target/ || true"
            dir("/repo/dragonwell${params.RELEASE}") {
                sh "git fetch origin"
                sh "git reset --hard origin/master"
            }
            dir("/root/wiki/dragonwell${params.RELEASE}.wiki") {
                print "更新ReleaseNotes"
                sh "git fetch origin && git reset --hard origin/master"
                sh(script: "docker run  registry.cn-hangzhou.aliyuncs.com/dragonwell/dragonwell:${tagName4Docker}_x86_64_slim java -version 2> tmpt")
                def fullVersionOutput = sh(script: "cat tmpt", returnStdout: true).trim()
                print "fullversion is ${fullVersionOutput}"
                if (!fullVersionOutput.contains("${typePrefix} Edition")) {
                  error "Invalid version string"
                }
                def releasenots = sh(script: "cat Alibaba-Dragonwell${slash}${params.RELEASE}-${typePrefix}-Release-Notes.md", returnStdout: true).trim()
                if (!releasenots.contains("${params.VERSION}")) {
                    print "更新 ${params.VERSION} 到 Alibaba-Dragonwell${slash}${params.RELEASE}-${typePrefix}-Release-Notes.md"
                    URL apiUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/tags")
                    def page = 10
                    if (params.RELEASE == "17") {
                      apiUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/tags?per_page=100&page=${page}")
                    }
                    def card = new JsonSlurper().parse(apiUrl)
                    if (params.RELEASE == "17") {
                      while (page > 0) {
                        if (card.size() > 1)
                          break
                        page -= 1
                        apiUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/tags?per_page=100&page=${page}")
                        card = new JsonSlurper().parse(apiUrl)
                      }
                      println "page ${page}"
                    }
                    def fromTag = ""
                    def toTag = ""
                    if (card.size() > 1) {
                        def end = card.size() - 1
                        def lastRelease = ""
                        def curRelease = ""
                        for (i in 0..end) {
                          println card[i].get("name")
                          if (card[i].get("name").contains(typePrefix.toLowerCase())) {
                            println "matched ${card[i].get("name")}"
                            if (curRelease == "")
                              curRelease = card[i].get("name")
                            else
                              lastRelease = card[i].get("name")
                          }
                          if (curRelease && lastRelease) {
                            break
                          }
                        }
                        if (!lastRelease) {
                          for (i in 0..end) {
                            def releaseName = card[i].get("name")
                            if (!(releaseName.contains("extended") || releaseName.contains("standard")) && releaseName.contains("dragonwell")) {
                              lastRelease = card[i].get("name")
                              break
                            }
                          }
                        }
                        fromTag = lastRelease ? "--fromtag ${lastRelease}" : ""
                        toTag = curRelease ? "--totag ${curRelease}" : ""
                    }
                    sh "wget https://raw.githubusercontent.com/dragonwell-releng/jenkinsUserContent/master/utils/driller.py -O driller.py"
                    def gitLogReport = sh(script: "python3 driller.py --repo /repo/dragonwell${params.RELEASE} ${fromTag} ${toTag} --release ${params.RELEASE}", returnStdout: true)
                    def newReleasenotes = ""
                    if (params.RELEASE == "8") {
                      newReleasenotes = """
# Alibaba Dragonwell ${params.VERSION}-GA
 ```
${fullVersionOutput}
 ```
${gitLogReport}
""" + releasenots
                            } else if (params.RELEASE == "11") {
                              def suffix = params.VERSION.substring(0, params.VERSION.lastIndexOf("."))
                              //def patchNum = params.VERSION.substring(params.VERSION.lastIndexOf(".") + 1)
                              def newVersion = "${suffix}"
                              newReleasenotes = """
# ${newVersion}
 ```
${fullVersionOutput}
 ```
${gitLogReport}
""" + releasenots
                            } else {
                              newReleasenotes = """
# ${params.VERSION}
 ```
${fullVersionOutput}
 ```
${gitLogReport}
""" + releasenots
                            }
                            println newReleasenotes
                            if (params.UPDATE_WIKI) {
                              def rdFileName = "Alibaba-Dragonwell${slash}${params.RELEASE}-${typePrefix}-Release-Notes.md"
                              if (params.RELEASE == "17")
                                rdFileName = "Alibaba-Dragonwell${slash}${params.RELEASE}-${typePrefix}-Edition-Release-Notes.md"
                              writeFile file: rdFileName, text: newReleasenotes
                              sh "git add ${rdFileName}"
                              sh "git commit -m \" update ${rdFileName} \""
                              sh "git push origin HEAD:master"
                              if (fileExists("阿里巴巴Dragonwell${params.RELEASE}-${typePrefix}发布说明.md")) {
                                  print "更新 ${params.VERSION}-${typePrefix} 到 发布说明中文版"
                                  releasenots = sh(script: "cat 阿里巴巴Dragonwell${params.RELEASE}-${typePrefix}发布说明.md", returnStdout: true).trim()
                                  writeFile file: "阿里巴巴Dragonwell${params.RELEASE}-${typePrefix}发布说明.md", text: newReleasenotes
                                  sh "git add 阿里巴巴Dragonwell${params.RELEASE}-${typePrefix}发布说明.md"
                                  sh "git commit -m \" update 阿里巴巴Dragonwell${params.RELEASE}-${typePrefix}发布说明.md \""
                                  sh "git push origin HEAD:master"
                              } else {
                                  print "did not find 阿里巴巴Dragonwell${params.RELEASE}-${typePrefix}发布说明.md"
                              }
                            }
                        }

                        print "更新docker镜像"

                        def dockerimages
                        if (params.RELEASE == "8") {
                            dockerimages = sh(script: "cat Use-Dragonwell${slash}${params.RELEASE}-docker-image.md", returnStdout: true).trim()
                        } else {
                            dockerimages = sh(script: "cat Use-Dragonwell${slash}${params.RELEASE}-docker-images.md", returnStdout: true).trim()
                        }

                        if (!dockerimages.contains("${tagName4Docker}")) {
                            print "更新 ${tagName4Docker} 到 Use-Dragonwell${slash}${params.RELEASE}-docker-images.md"
                            ArrayList l = new ArrayList(Arrays.asList(dockerimages.split("\n")))
                            for (int i = 0; i < l.size(); i++) {
                                if (l.get(i).contains("---")) {
                                    l.add(i + 1, DOCKER_IMAGES_TEMPLATE1.replace("VERSION", tagName4Docker));
                                    l.add(i + 1, DOCKER_IMAGES_TEMPLATE2.replace("VERSION", tagName4Docker));
                                    l.add(i + 1, DOCKER_IMAGES_TEMPLATE3.replace("VERSION", tagName4Docker));
                                    l.add(i + 1, DOCKER_IMAGES_TEMPLATE4.replace("VERSION", tagName4Docker));
                                    if (params.RELEASE != "8") {
                                        l.add(i + 1, DOCKER_IMAGES_TEMPLATE5.replace("VERSION", tagName4Docker));
                                    }
                                    break;
                                }
                            }
                            println l.join("\n")
                            if (params.UPDATE_WIKI) {
                              writeFile file: "Use-Dragonwell${slash}${params.RELEASE}-docker-images.md", text: l.join("\n")
                              sh "git add Use-Dragonwell${slash}${params.RELEASE}-docker-images.md"
                              sh "git commit -m \" update Use-Dragonwell${slash}${params.RELEASE}-docker-images.md \""
                              sh "git push origin HEAD:master"
                            }
                        }
                        print "更新OSS下载链接"
                        def fineName
                        if (params.RELEASE == "8") {
                            fineName = "\'下载镜像(Mirrors-for-download).md\'"
                        } else {
                            fineName = "\'Mirrors-for-download-(下载镜像).md\'"
                        }

                        def osslinks = sh(script: "cat $fineName", returnStdout: true).trim()
                        //print "oss links ${osslinks}"
                        println MIRROS_DOWNLOAD_TEMPLATE + osslinks
                        if (!osslinks.contains("${params.VERSION}-${typePrefix}") && params.UPDATE_WIKI) {
                            print "更新 ${params.VERSION} 到下载镜像"
                            if (params.RELEASE == "8") {
                                writeFile file: '下载镜像(Mirrors-for-download).md', text: (MIRROS_DOWNLOAD_TEMPLATE + osslinks)
                                sh "git add '下载镜像(Mirrors-for-download).md'"
                            } else {
                                writeFile file: 'Mirrors-for-download-(下载镜像).md', text: (MIRROS_DOWNLOAD_TEMPLATE + osslinks)
                                sh "git add 'Mirrors-for-download-(下载镜像).md'"
                            }
                            sh "git commit -m \" update Mirrors for download\""
                            sh "git push origin HEAD:master"
                        }
                    }
                }
            }
}
