package com.kafang.jenkins.MDS

/**
 * @author xuejian.sun* @date 2019/12/25 14:25
 */

/**
 * 环境变量
 */
def packageType = env.pakType
def userChoiceModule = env.modules
def replay_speed = env.speed
def rd = env.release_dir
def mt = env.mockTime
def mfd = env.mfd
def ms = env.market_source
def remoteIp = env.remote
def remoteCredentialId = env.Authorization

//发布包路径
def pakDir = "/shared/release/market/"

getServer = {
    ip, id ->
        def remote = [:]
        remote.name = "server-${ip}"
        remote.host = ip
        remote.allowAnyHosts = true
        withCredentials([usernamePassword(credentialsId: "$id", passwordVariable: 'password', usernameVariable: 'userName')]) {
            remote.user = "${userName}"
            remote.password = "${password}"
        }
        return remote
}

node {
    stage('load env') {
        echo "choice modules: $userChoiceModule \r\n" +
                "pakType: $packageType \r\n" +
                "credentialId: $remoteCredentialId \r\n" +
                "replay speed: $replay_speed \r\n" +
                "release dir: $rd \r\n" +
                "init mock time: $mt \r\n" +
                "market data folder: $mfd \r\n" +
                "market source: $ms"
        cleanWs()
    }
    String[] deployModules = userChoiceModule.replaceAll("\"", "").tokenize(",")
    stage("pull release package") {
        String unzipFileList = "";
        if (deployModules.length > 1) {
            unzipFileList += "{"
        }
        for (int i = 0; i < deployModules.length; i++) {
            unzipFileList += deployModules[i] + "-${packageType}.zip"
            if ((i + 1) != deployModules.length) {
                unzipFileList += ","
            }
        }
        if (deployModules.length > 1) {
            unzipFileList += "}"
        }
        echo "pull: $unzipFileList"
        // 将需要deploy的包压缩在一起,放在当前workspace
        sh """ mkdir package
               cd package
               cp $pakDir$unzipFileList .
               zip -qumj market-release.zip $unzipFileList   
               cd -           
           """
    }
    stage("deploy -> $rd") {
        String[] source = ms.replaceAll("\"", "").tokenize(",")
        def removedFile = ""
        def isDeployMarketReplay = false
        for (module in deployModules) {
            removedFile += module + " "
            if (module == "market-replay") {
                isDeployMarketReplay = true
            }
        }
        if (isDeployMarketReplay) {
            sh """ unzip -q package/market-release.zip market-replay-release.zip -d package/javaTemporary
                   cd package/javaTemporary
                   unzip -q "*.zip"
                   sed -i 's/market.replay.mock.time.frequency=1/market.replay.mock.time.frequency=${replay_speed}/g' market-replay/conf/application-provided.properties
                   sed -i 's/market.replay.mock.time=20191120_090000/market.replay.mock.time=${mt}/g' market-replay/conf/application-provided.properties
                   sed -i 's#atgo.market.storage.file-dir=/home/atgouser/market/data#atgo.market.storage.file-dir=${mfd}#g' market-replay/conf/application-provided.properties 
               """
            for (item in source) {
                sh """ cd package/javaTemporary
                       sed -i 's/market.replay.${item}.enabled=false/market.replay.${item}.enable=true/g' market-replay/conf/application-provided.properties
                       cd -
                   """
            }
            sh """ cd package/javaTemporary
                   zip -rq market-replay-release.zip market-replay/
                   rm -rf market-replay
                   zip -qum ../market-release.zip market-replay-release.zip
                   cd -
               """
        }

        //delete old pak, release new pak
        def sshServer = getServer "${remoteIp}", "${remoteCredentialId}"
        sshPut remote: sshServer, from: "${env.WORKSPACE}/package/market-release.zip", into: "$rd"
        sshCommand remote: sshServer,
                command: """ cd $rd
                             rm -rf $removedFile
                             unzip -qo market-release.zip
                             unzip -qo "*.zip"
                             rm -rf *.zip
                         """
    }
}