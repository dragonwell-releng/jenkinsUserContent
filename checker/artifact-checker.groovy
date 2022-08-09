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
    booleanParam(
      defaultValue: true,
      description: 'use dragonwell release rule',
      name: 'DRAGONWELL'
    )
  ])
])

platforms = []
if (params.RELEASE == "17") {
    platforms = ["aarch64_linux", "x64_alpine-linux", "x64_linux", "x64_windows"]
} else if (params.RELEASE == "11") {
    platforms = ["aarch64_linux", "x64_alpine-linux", "x64_linux", "x64_windows"]
} else {
    platforms = ["aarch64_linux", "x64_linux", "x64_windows"] //  default dragonwell8
}

def typePrefix = params.TYPE ? params.TYPE.capitalize() : "Extended"

def getNewVersion(version, openjdkVersion) {
  def patchNum = ""
  def pacthSep = ""
  if (params.RELEASE == "17") {
    patchNum = openjdkVersion.split("\\+")[-1]
    pacthSep = "+"
  }
  if (params.RELEASE == "17") {
    version = version.split("\\+")[0]
  }
  def versionArr = version.split("\\.")
  def end = versionArr.size() - 1
  def expectVersion = ""
  def jdkVersionSuffix = "jdk-"
  def num = 0
  for (i in 0..end) {
    if ((params.RELEASE == "8" && i != 0) || 
        (params.RELEASE == "11" && (i == 2 || i == 3)) ||
        (params.RELEASE == "17" && (i == 2 || i == 4))) {
      num = versionArr[i].toInteger() + 1
    } else {
      num = versionArr[i].toInteger()
    }
    def suffix = i == end ? "${num}" : "${num}."
    expectVersion += suffix
    if (params.RELEASE == "11" || params.RELEASE == "17") {
      if (i < 2)
        jdkVersionSuffix += "${num}."
      else if (i == 2)
        jdkVersionSuffix += "${num}"
    }
  }
  if (params.RELEASE == "8") {
    jdkVersionSuffix = openjdkVersion.split("-")[0]
  }
  expectVersion = "${expectVersion}${pacthSep}${patchNum}"
  def expectTagVersion = "${expectVersion}_${jdkVersionSuffix}"
  return [expectVersion, expectTagVersion]
}

def validateCheckSum(pkgName, cmpFile) {
    def shaVal = sh returnStdout: true, script: "sha256sum ${pkgName} | cut -d ' ' -f 1"
    def checkRes = sh returnStdout: true, script: "cat ${cmpFile} | grep ${shaVal}"
    if (checkRes == false) {
        println "${cmpFile} sha256 is wrong"
        return [false, shaVal]
    }
    return [true, shaVal]
}

def checkPackages(packages) {
  def usedPlatforms = []
  if (packages.size() != platforms.size()) {
    return [false, "Package size is wrong!"]
  }
  for (pkg in packages) {
    for (platform in platforms) {
      if (pkg.contains(platform)) {
        usedPlatforms.add(platform)
        break
      }
    }
  }
  if (usedPlatforms.size() < platforms.size()) {
    for (platform in platforms) {
      if (!usedPlatforms.contains(platform))
        println "Miss platform ${platform}"
    }
    return [false, "Miss some platform packages!"]
  }
  def res = true
  def msg = "Check packages pass!"
  for (pkg in packages) {
    def (ret, val) = validateCheckSum(pkg, "${pkg}.sha256.txt")
    if (!ret) {
      res = false
      msg = "Check sha256 failed"
    }
  }
  return [res, msg]
}

node("artifact.checker") {
  sh "rm -rf *.tar.gz *.txt"
  URL releaseUrl = new URL("https://api.github.com/repos/alibaba/dragonwell${params.RELEASE}/releases")
  URL openjdkUrl = new URL("https://api.adoptium.net/v3/assets/latest/${params.RELEASE}/hotspot?vendor=eclipse")
  def releases = new JsonSlurper().parseText(releaseUrl.text.trim())
  def preReleaseCard = releases.findAll { it.get("prerelease") == true && it.get("name").contains(typePrefix)}
  def lastReleaseCard = releases.findAll { it.get("prerelease") == false && it.get("name").contains(typePrefix)}
  if (lastReleaseCard.size() == 0) {
    lastReleaseCard = releases.findAll { it.get("prerelease") == false }
  }
  def openjdkCard = new JsonSlurper().parseText(openjdkUrl.text.trim())
  def openjdkRelease = openjdkCard[0].get("release_name")
  if (preReleaseCard.size() >= 1) {
    def releaseName = preReleaseCard[0].get("name")
    def tagName = preReleaseCard[0].get("tag_name")
    def assets = preReleaseCard[0].get("assets")
    def lastReleaseName = lastReleaseCard[0].get("name")
    println """* openjdk release name: ${openjdkRelease}
* last release name: ${lastReleaseName}
* release name: ${releaseName}
* tag name: ${tagName}"""
    def lastReleaseVersion = lastReleaseName.split("_")[-1]
    def newVersions = getNewVersion(lastReleaseVersion, openjdkRelease)
    println """* new version: ${newVersions[0]}
* new tag version: ${newVersions[1]}"""
    if (!releaseName.contains(newVersions[0])) {
      error "Invalid version number!"
    }
    if (!tagName.contains(newVersions[1])) {
      error "Invalid tag String!"
    }
    if (assets.size() % 2 != 0)
      error "Wrong upload files number, maybe forget to upload *.sha256.txt"
    def packageList = []
    for (asset in assets) {
      def assetName = asset.get("name")
      if (assetName.contains(".sha256.txt")) {
        def packageName = assetName.split(".sha256")[0]
        if (!packageList.contains(packageName)) {
          packageList.add(packageName)
        }
      }
      def assetUrl = asset.get("browser_download_url")
      sh "wget -q ${assetUrl}"
    }
    def (res, msg) = checkPackages(packageList)
    if (!res)
      error msg
    else
      println msg
  } else {
    error "Cannot find pre-release or name doesn't contain ${typePrefix}!"
  }
}
