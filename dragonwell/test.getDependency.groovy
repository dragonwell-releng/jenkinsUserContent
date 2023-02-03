properties([
  parameters([
    string(
      defaultValue: "test",
      description: "BUILD_TYPE determines what type of build we are getting dependency jars for, e.g. BUILD_TYPE='system' will fetch dependency jars for system test builds, BUILD_TYPE='test' will fetch dependency jars for all test builds other than system",
      name: "BUILD_TYPE"
    )
  ])
])

node("perl") {
  sh "rm -rf getDependencies.pl *.jar, *.zip, *.txt, *.gz"
  sh "wget --no-check-certificate https://raw.githubusercontent.com/adoptium/TKG/master/scripts/getDependencies.pl"
  sh "perl ./getDependencies.pl -path . -task default"
  sh "curl -Lks -C - https://sourceforge.net/projects/dacapobench/files/latest/download -o dacapo.jar"
  archiveArtifacts artifacts: "*.jar, *.zip, *.txt, *.gz", fingerprint: true, allowEmptyArchive: true
}
