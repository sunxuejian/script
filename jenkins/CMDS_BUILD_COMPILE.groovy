package com.kafang.jenkins.MDS
/**
 * @author xuejian.sun* @date 2019/11/25 13:38
 *
 * C++ 行情模块构建编译
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
        "QtcInfo", "BarGen"
]

//获取svn代码
svn_checkout = {
    project, svnUrl, releaseMethod, revision, credentialsId ->
        stage("checkout $project") {
            checkout([$class              : 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '',
                      excludedRegions     : '', excludedRevprop: '', excludedUsers: '', filterChangelog: false,
                      ignoreDirPropChanges: false, includedRegions: '',
                      locations           : [[cancelProcessOnExternalsFail: true, credentialsId: "${credentialsId}", depthOption: 'infinity',
                                              ignoreExternalsOption       : false, local: "$project", remote: "$svnUrl/$releaseMethod$project$revision"]],
                      quietOperation      : true, workspaceUpdater: [$class: 'UpdateUpdater']])
        }
}

cmake_compile = {
    withEnv(['PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/bin:/usr/lib64']) {
        sh """
            cd $it
            sh linuxBuild.sh
            cd -
           """
    }
}

deploy = {
    module, rd ->
        sh """
            mkdir $rd || true
            rm -rf $rd$module-release.zip
            rm -rf $rd$module-debug.zip
            cd Output/$module/linux/x86_x64/
            mkdir $module
            mv release/* $module
            zip -rm $module-release.zip $module
            mv $module-release.zip $rd
            mkdir $module
            mv debug/* $module
            zip -rm $module-debug.zip $module
            mv $module-debug.zip $rd
            cd -
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
    stage("svn checkout library") {
        svn_checkout "libs", svnRootFolder, codeFolder, revision, credentialsId
    }
    stage("svn checkout CMDS") {
        def checkoutJobs = [:]
        for (int i = 0; i < modules.size(); i++) {
            def module = i
            checkoutJobs[i] = {
                svn_checkout modules[module], svnRootFolder, codeFolder, revision, credentialsId
            }
        }
        parallel checkoutJobs
    }
    stage("cmake compile") {
        def cmakeCompileJob = [:]
        for (int i = 0; i < modules.size(); i++) {
            def module = i
            cmakeCompileJob[i] = {
                cmake_compile modules[module]
            }
        }
        parallel cmakeCompileJob
    }
    stage("upload to release folder") {
        for (module in modules) {
            deploy "$module", "$releaseFolder"
        }
        sh "ls -l $releaseFolder"
    }
    stage "审批"
    try {
        timeout(activity: true, time: 30, unit: 'SECONDS') {
            input message: '将最新代码部署到你的服务器?', ok: '是的'
        }
    } catch (Exception e) {
        echo "abort or false ,$e"
        return
    }
}
