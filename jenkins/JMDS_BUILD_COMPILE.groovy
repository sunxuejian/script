package com.kafang.jenkins.MDS
/**
 * @author xuejian.sun* @date 2019/11/25 13:38
 *
 *  JAVA 行情模块编译
 *
 * 构建流程:
 * 1. 删除旧的workspace
 * 2. svn获取新的代码
 * 3. 编译好代码.放在指定目录.
 * 4. 用户选择是否deploy到远程,超时时间为20秒, 超时后将不做deploy,
 * 4.1. deploy: 调用 MarketDeploy Job 进行上传.
 *
 */
// 需要构建的行情模块
def modules = [
        "market-broker", "market-canceled"
        , "market-dump", "market-replay"
        , "market-level1", "market-level12fix"
        , "market-hbCreator", "market-monitor"
]

//获取svn代码
svn_checkout = {
    project, svnUrl, releaseMethod, revision, credentialsId ->
        stage("checkout $project") {
            checkout([$class              : 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '',
                      excludedRegions     : '', excludedRevprop: '', excludedUsers: '', filterChangelog: false,
                      ignoreDirPropChanges: false, includedRegions: '',
                      locations           : [[cancelProcessOnExternalsFail: true, credentialsId: "${credentialsId}", depthOption: 'infinity',
                                              ignoreExternalsOption       : false, local: "$project", remote: "$svnUrl$project/$releaseMethod$revision"]],
                      quietOperation      : true, workspaceUpdater: [$class: 'UpdateUpdater']])
        }
}

mvn_compile = {
    sh """
        cd $it
        mvn package -DskipTests
        cd -
    """
}

deploy = {
    module, rd ->
        sh """
            mkdir $rd || true
            rm -rf $rd/$module-release.zip
            mv $module/target/$module-release.zip $rd/
           """
}

node {
    def codeFolder = env.code_folder
    def revision = env.revision
    def svnRootFolder = env.svn_root_folder
    def releaseFolder = env.relase_folder
    def credentialsId = env.Authorization
    stage("load env&clean workspace") {
        echo "svn url: $svnRootFolder \r\n" +
                "release method: $codeFolder \r\n" +
                "revision: $revision"
        cleanWs()
    }
    stage("svn checkout") {
        def checkoutJobs = [:]
        for (int i = 0; i < modules.size(); i++) {
            def module = i
            checkoutJobs[i] = {
                svn_checkout modules[module], svnRootFolder, codeFolder, revision, credentialsId
            }
        }
        parallel checkoutJobs
    }
    stage("maven compile") {
        def mvnCompileJob = [:]
        for (int i = 0; i < modules.size(); i++) {
            def module = i
            mvnCompileJob[i] = {
                mvn_compile modules[module]
            }
        }
        parallel mvnCompileJob
    }
    stage("upload to release folder") {
        for (module in modules) {
            deploy "$module", "$releaseFolder"
        }
        sh "ls -l $releaseFolder"
        cleanWs()
    }
    def marketSource = ""
    stage "审批"
    try {
        timeout(activity: true, time: 30, unit: 'SECONDS') {
            marketSource = input message: '将最新代码部署到你的服务器?', ok: '是的',
                    parameters: [extendedChoice(defaultValue: 'level2,index', description: '选择需要回放行情源',
                            descriptionPropertyValue: '', multiSelectDelimiter: ',',
                            name: 'market_source', quoteValue: false, saveJSONParameterToFile: false, type: 'PT_CHECKBOX',
                            value: 'level2,index,order,trans,future,queue', visibleItemCount: 6)]
        }
    } catch (Exception e) {
        echo "abort or false ,$e"
        return
    }
    echo "use keep deploy\r\n" +
            "source: $marketSource"
}
