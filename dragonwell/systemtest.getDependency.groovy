properties([
  parameters([
    string(
      defaultValue: "https://github.com/adoptium/aqa-tests.git",
      description: "https://github.com/adoptium/aqa-tests.git",
      name: "ADOPTOPENJDK_REPO"
    ),
    string(
      defaultValue: "master",
      description: "master",
      name: "ADOPTOPENJDK_BRANCH"
    ),
    string(
      defaultValue: "systemtest",
      description: "BUILD_TYPE determines what type of build we are getting dependency jars for, e.g. BUILD_TYPE='system' will fetch dependency jars for system test builds, BUILD_TYPE='test' will fetch dependency jars for all test builds other than system",
      name: "BUILD_TYPE"
    ),
    string(
      defaultValue: "",
      description: "",
      name: "LABEL"
    )
  ])
])

node("perl && dep") {
  def javaHome = "${env.WORKSPACE}/j2sdk-image"
  git branch: "master", url: "https://github.com/adoptium/aqa-tests.git"
  //sh "curl -OLJks 'https://api.adoptopenjdk.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/adoptopenjdk'"
  def systemtestDir = "${env.WORKSPACE}/aqa-systemtest"
  def stfDir = "${env.WORKSPACE}/STF"
  def res = fileExists systemtestDir
  if (!res)
    sh "git clone https://github.com/adoptium/aqa-systemtest"
   res = fileExists stfDir
  if (!res)
    sh "git clone https://github.com/adoptium/STF"
  dir(systemtestDir) {
    sh "git reset --hard && git pull"
  }
  dir(stfDir) {
    sh "git reset --hard && git pull"
  }
  sh "ant -f ./aqa-systemtest/openjdk.build/build.xml -Djava.home=${javaHome}/jre -Dprereqs_root=${env.WORKSPACE}/systemtest_prereqs configure"
  sh "ant -f ./aqa-systemtest/openjdk.test.mauve/build.xml -Djava.home=${javaHome}/jre -Dprereqs_root=${env.WORKSPACE}/systemtest_prereqs configure"
  archiveArtifacts artifacts: "systemtest_prereqs/apache-ant/lib/ant-launcher.jar", fingerprint: true, allowEmptyArchive: true
  archiveArtifacts artifacts: "systemtest_prereqs/asm/asm.jar", fingerprint: true, allowEmptyArchive: true
  archiveArtifacts artifacts: "systemtest_prereqs/cvsclient/org-netbeans-lib-cvsclient.jar", fingerprint: true, allowEmptyArchive: true
  archiveArtifacts artifacts: "systemtest_prereqs/junit/hamcrest-core.jar", fingerprint: true, allowEmptyArchive: true
  archiveArtifacts artifacts: "systemtest_prereqs/junit/junit.jar", fingerprint: true, allowEmptyArchive: true
  archiveArtifacts artifacts: "systemtest_prereqs/log4j/log4j-api.jar", fingerprint: true, allowEmptyArchive: true
  archiveArtifacts artifacts: "systemtest_prereqs/log4j/log4j-core.jar", fingerprint: true, allowEmptyArchive: true
  archiveArtifacts artifacts: "systemtest_prereqs/mauve/mauve.jar", fingerprint: true, allowEmptyArchive: true
  archiveArtifacts artifacts: "systemtest_prereqs/tools/tools.jar", fingerprint: true, allowEmptyArchive: true
}
