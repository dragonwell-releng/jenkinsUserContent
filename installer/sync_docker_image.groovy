import groovy.json.*

node('ossworker && dockerfile && x64') {
  dir(env.WORKSPACE) {
    sh "cp ~/image-syncer .; cp ~/docker-ls ."
    if (!params.ONLY_SYNC_TO_ANOLIS) {
      timeout(time: 8, unit: 'HOURS') {
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
        def tags = ''
        def maxPageNo = 1
        if (totalCount > 100) {
          maxPageNo = totalCount % 100 == 0 ? totalCount.intdiv(100) : totalCount.intdiv(100) + 1
        }
        for (pageNo in 1..maxPageNo) {
          req = sh(returnStdout: true, script: "bash getImageTags.sh ${pageNo}")
          resp = new JsonSlurper().parseText(req)
          for (image in resp.get('body').get('Images')) {
            def imageTag = image.Tag
            tags = tags ? "${tags},${imageTag}" : "${imageTag}"
          }
        }
        sh """
rm -rf images.json
echo '{
  "auth":' >> images.json
cat /root/.docker/anolis_sync_auth.json >> images.json
echo ',
  "images": {
    "dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:${tags}":"registry.hub.docker.com/alibabadragonwell/dragonwell"
  }
}' >> images.json
./image-syncer --config=./images.json --retries=3
"""
        // https://github.com/AliyunContainerService/image-syncer/blob/master/FAQs.md#how-can-i-deal-with-the-failed-sync-tasks%E5%90%8C%E6%AD%A5%E5%A4%B1%E8%B4%A5%E7%9A%84%E4%BB%BB%E5%8A%A1%E5%A6%82%E4%BD%95%E5%A4%84%E7%90%86
        //sh "./image-syncer --config=/root/.docker/sync_config.json --retries=3"
      }
    }
    //timeout(time: 8, unit: 'HOURS') {
    //  // Sync to OpenAnolis
    //  sh "wget https://raw.githubusercontent.com/dragonwell-releng/jenkinsUserContent/master/installer/DragonwellImageSyncer.java -O DragonwellImageSyncer.java && javac DragonwellImageSyncer.java && java DragonwellImageSyncer"
    //}
  }
}
