import groovy.json.*

node('ossworker && dockerfile && x64') {
  dir(env.WORKSPACE) {
    sh "cp ~/image-syncer .; cp ~/docker-ls ."
    if (!env.ONLY_SYNC_TO_ANOLIS) {
      timeout(time: 8, unit: 'HOURS') {
        // https://github.com/AliyunContainerService/image-syncer/blob/master/FAQs.md#how-can-i-deal-with-the-failed-sync-tasks%E5%90%8C%E6%AD%A5%E5%A4%B1%E8%B4%A5%E7%9A%84%E4%BB%BB%E5%8A%A1%E5%A6%82%E4%BD%95%E5%A4%84%E7%90%86
        sh "./image-syncer --config=/root/.docker/sync_config.json --retries=3"
      }
    }
    timeout(time: 8, unit: 'HOURS') {
      // Test OpenAnolis images
      // Need "apt -y install locales; locale-gen zh_CN.utf8" first
      def res = sh "cp ~/DragonwellImageTester.java . && javac DragonwellImageTester.java && export LANG=zh_CN.utf8 && java DragonwellImageTester dragonwell"
      if (res)
        error "pipeline failed"
    }
    timeout(time: 8, unit: 'HOURS') {
      // Sync to OpenAnolis
      sh "wget https://raw.githubusercontent.com/dragonwell-releng/jenkinsUserContent/master/installer/DragonwellImageSyncer.java -O DragonwellImageSyncer.java && javac DragonwellImageSyncer.java && java DragonwellImageSyncer"
    }
  }
}
