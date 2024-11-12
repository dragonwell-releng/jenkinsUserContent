properties([
  parameters([
    string(
      defaultValue: "test",
      description: "BUILD_TYPE determines what type of build we are getting dependency jars for, e.g. BUILD_TYPE='system' will fetch dependency jars for system test builds, BUILD_TYPE='test' will fetch dependency jars for all test builds other than system",
      name: "BUILD_TYPE"
    ),
    string(
      defaultValue: "",
      name: "NODE_LABEL"
    )
  ])
])

def nodeLabel = "perl"
if (!params.NODE_LABEL.equals("")) {
  nodeLabel += " && ${params.NODE_LABEL}"
}

node(nodeLabel) {
  sh "rm -rf getDependencies.pl *"
  sh "curl -Os https://raw.githubusercontent.com/adoptium/TKG/master/scripts/getDependencies.pl"
  sh "perl ./getDependencies.pl -path . -task default"
  sh "curl -Lks -C - https://sourceforge.net/projects/dacapobench/files/latest/download -o dacapo.jar"
  sh "curl -Lks -C - https://github.com/renaissance-benchmarks/renaissance/releases/download/v0.14.0/renaissance-mit-0.14.0.jar -o renaissance.jar"
  archiveArtifacts artifacts: "*.jar, *.zip, *.txt, *.gz", fingerprint: true, allowEmptyArchive: true
}
