import groovy.json.*

node('ossworker && dockerfile && x64') {
  dir(env.WORKSPACE) {
    sh "cp ~/image-syncer ."
    timeout(time: 8, unit: 'HOURS') {
      sh "./image-syncer --config=/root/.docker/sync_config.json"
    }
  }
}
