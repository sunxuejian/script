#!/bin/bash

# 这是一个java服务启停脚本.

# get this server pid
pid=""
# 查找对应服务的进程 test_server: 启动进程中的唯一关键字,需要自己定义
funcGrepJavaPid(){
pid=`ps -aux|grep java|grep test_server|grep -v "grep"|awk '{print $2}'`
}


# check current state
checkState(){
funcGrepJavaPid
if [ "$1" = "stop" ];
then
    if [ "$pid" = "" ];
    then
       echo "no test_server pid alive !"
       exit 1
    fi
elif [ "$1" = "start" ];
then
    if [ "$pid" != "" ];
    then
        echo "Startup failed, the service is already running!"
        exit 1
    fi
elif [ "$1" = "isStart" ]
then
    if [ "$pid" != "" ];
    then
        echo "Startup Successful -> $pid!"
        exit 0;
    fi
fi
}

# stop server
stop(){
funcGrepJavaPid
echo "Stop test_server -> $pid"
kill ${pid}
stopCheck=0
condition=1
while [ ${condition} -eq 1 ]
do
  funcGrepJavaPid
  if [ "$pid" = "" ];
  then
     condition=0
     echo "Stop Successful!"
  else
     if [[ $stopCheck -eq 0 ]];
     then sleep 3
     else kill -9 $pid
     fi
  fi
  stopCheck+=1
done
}

# start server
start(){

installDirectory=$(cd "$(dirname "$0")";cd ..; pwd)

JAVA_OPT="-server -d64 -Xms1024M -Xmx5g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDateStamps -XX:+PrintGCDetails -Xloggc:gc.log -DATGO_ENV=provided"

pushd "${installDirectory}"

export LD_LIBRARY_PATH=$installDirectory/so:$LD_LIBRARY_PATH

echo "Start market-tdf ..."

nohup java $JAVA_OPT -Dserver_name=test_server -cp ./conf:lib/* org.springframework.boot.loader.JarLauncher &> /dev/null 2>&1 &

}

restart(){
stop
start
}

case "$1" in

    'stop')
        checkState $1
        stop
        ;;

    'start')
        checkState $1
        start
        checkState "isStart"
        ;;

    'restart')
        checkState $1
        restart
        checkState "isStart"
        ;;

    *)
        echo "Usage: $0 { start | stop | restart }"
        exit 1
        ;;
esac
