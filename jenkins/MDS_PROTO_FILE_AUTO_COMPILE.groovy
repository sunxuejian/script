package com.kafang.jenkins.MDS

/**
 * @author xuejian.sun* @date 2020/1/7 15:20
 */

def svnCredentialsId = "25263817-38e0-47f2-8874-4dbe4d386948"
def svnUrl = "http://192.168.5.208/svn/develop/project_mds/protobuf/market@HEAD"

def cppFileOutPath = "./cppparse/MdPbParse/PBStruct"

node {
    stage('svn checkout proto/market HEAD') {
        echo "remove workspace ..."
        cleanWs()
        timestamps {
            checkout([$class              : 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '',
                      excludedRegions     : '', excludedRevprop: '', excludedUsers: '', filterChangelog: false,
                      ignoreDirPropChanges: false, includedRegions: '',
                      locations           : [[cancelProcessOnExternalsFail: true, credentialsId: "$svnCredentialsId", depthOption: 'infinity',
                                              ignoreExternalsOption       : false, local: '.', remote: "$svnUrl"]],
                      quietOperation      : true, workspaceUpdater: [$class: 'UpdateUpdater']])
        }
        sh "chmod 755 -R bin/*"
    }

    stage('generate proto file') {
        // run shell script
        sh "chmod 770 ./rscript/run.csv2message.sh; ./rscript/run.csv2message.sh"
    }

    stage("proto file compiler - java") {
        timestamps {
            sh "bin/java-compiler.sh"
        }
    }

    stage("proto file compiler - cpp") {
        timestamps {
            if (cppFileOutPath.isEmpty()) {
                sh "bin/linux/cpp-compiler.sh"
            } else {
                sh "bin/linux/cpp-compiler.sh $cppFileOutPath"
            }
        }
    }

    stage("proto file compiler - python") {
        timestamps {
            sh "bin/linux/python-compiler.sh"
        }
    }

    stage("commit proto class") {
        timestamps {
            withCredentials([usernamePassword(credentialsId: "$svnCredentialsId", passwordVariable: 'password', usernameVariable: 'userName')]) {
                sh """ 
                    svn cleanup
                    svn status
                    svn add $cppFileOutPath/** --force
                    svn add python/** --force
                    svn add message/** --force
                    svn add java/** --force
                    svn commit --username ${userName} --password ${password} -m "proto auto compile and commit!"
                  """
            }
        }
    }
}