package com.kafang.jenkins.MDS

/**
 * @author xuejian.sun* @date 2019/12/25 15:26
 */

/**
 * 环境变量
 */
def rd = env.release_dir
def modules = (env.modules).trim().replaceAll("\"", "")
def launch = env.launch
def remoteIp = env.remote
def credentialId = env.Authorization

/**
 * rePub模块对应dump文件名的映射,
 * 用于重新构建时删除旧的dump文件
 */
def map = [:]
map."BarGen" = "Bar_"
map."QtcInfo" = "Qtc_"
map."market-canceled" = "Canceled_"

getServer = {
    ip, credentialsId ->
        def remote = [:]
        remote.name = "server-$ip"
        remote.host = ip
        remote.allowAnyHosts = true
        withCredentials([usernamePassword(credentialsId: "$credentialsId", passwordVariable: 'password', usernameVariable: 'userName')]) {
            remote.user = "${userName}"
            remote.password = "${password}"
        }
        return remote
}

node {
    stage("load env") {
        echo "操作方式: $launch \r\n" +
                "已选择的模块: $modules \r\n" +
                "远程地址: $remoteIp \r\n" +
                "项目发布目录: $rd \r\n" +
                "credentialsId: ${credentialId}"
    }

    stage("execute launch") {
        if (modules == null || modules == "") {
            echo "用户没选择任何模块, 执行结束. $credentialId"
        } else {
            String[] om = modules.tokenize(",")
            def remoteServer = getServer "$remoteIp", "$credentialId"
            for (module in om) {
                def rePubFile = map[module]
                echo rePubFile

                sshCommand remote: remoteServer,
                        command: """ sh $rd$module/bin/atgo.sh $launch 
                                     if [[ $module == "market-replay" &&  $launch != "stop" ]];
                                        then
                                            rm -rf ${rd}market-dump/market-stroage/Trans_`date +%Y%m%d`
                                            rm -rf ${rd}market-dump/market-stroage/Order_`date +%Y%m%d`
                                            rm -rf ${rd}market-dump/market-stroage/Queue_`date +%Y%m%d`
                                            rm -rf ${rd}market-dump/market-stroage/Index_`date +%Y%m%d`
                                            rm -rf ${rd}market-dump/market-stroage/QuoteE_`date +%Y%m%d`
                                            rm -rf ${rd}market-dump/market-stroage/QuoteF_`date +%Y%m%d`
                                     elif [[ $module != "market-replay" &&  $launch != "stop" ]]
                                        then                                           
                                            rm -rf ${rd}market-dump/market-stroage/${rePubFile}`date +%Y%m%d`
                                     else
                                        echo "exec $module $launch"
                                     fi 
                                 """
            }
        }
    }
}