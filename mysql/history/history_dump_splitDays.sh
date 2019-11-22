installDirectory=$(cd "$(dirname "$0")"; pwd)

pushd "${installDirectory}"

. ./history_dump_config.sh

isDeleteData=1

log(){
 curTime=`date +%Y%m%d\ %H:%M:%S`
 echo $curTime $1
}

dataCount(){
  $MYSQL -e "SELECT ReceivedDate,COUNT(ReceivedDate) 总条数 FROM BAK_MESSAGE GROUP BY ReceivedDate;" > $backup_dir/data_count.txt
}

dbSchema(){
 mysqldump -n -d -h$ip -P$port -u$user -p$password --single-transaction --compact $db $table > $backup_dir/${table}_SCHEMA.txt
}

clearupTable(){
 read  -n1 -t 5 -p "after dump delete old data [Y/N]?:" anw
 case $anw in
   Y | y)
    echo -e "\nafter dump start delete $table data yes!"
    isDeleteData=0;;
   N | n)
    echo -e "\nskip cleanup $table!";;
   *)
    echo -e "\nskip clearup $table!";;
esac
}

truncate(){
 if [[ $isDeleteData -eq 0 ]];then
  log "开始清理${table}中所有数据!"
  $MYSQL -e  "truncate table $table;"
  log "清理结束."
 fi
}

#导入数据到指定表 $1: sql文件地址 $2: 表名
mysqlImport(){
 if [[ $1 != "" ]];then
   log "load data file [$1] to -> $to_ip:$to_port/$to_db,table[$2]"
   $TO_MYSQL_IMPORT < $1 
 fi
}

# 如果表不存在则生成表 $1: 表名
generateNewTabale(){
 # 将默认表名替换成传入的表名
 newTableSchema=$(sed 's/`'$table'`/`'$1'`/;s/`'$1'`/IF NOT EXISTS & /' $backup_dir/${table}_SCHEMA.txt)
 $TO_MYSQL -e "$newTableSchema"
}

historyDump(){
   while read recDate
   do
    if [[ $recDate = "rd" ]]; then
     # skip 1 line
     log "skip column"
    else
     log "dump date -> $recDate"
     # 导出数据到配置目录
     mysqldump -t -h$ip -P$port -u$user -p$password --single-transaction --compact --where=''${recDate}' = ReceivedDate' $db $table > $backup_dir/${table}_${recDate}.sql
     # 修改导出数据中的表名,与文件名同步,方便直导出到另一张表
     sed -i 's/`'$table'`/`'${table}_${recDate}'`/' $backup_dir/${table}_${recDate}.sql
    fi
   done < <(echo "SELECT DISTINCT(ReceivedDate) rd FROM ${table}" | $MYSQL)
}

loadData(){
 for file in $backup_dir/*;
 do
  if [[ "$file" =~ ^${backup_dir}/BAK_MESSAGE_[0-9]{8}.sql ]];then
   fileSplit=${file##*/}
   generateNewTabale ${fileSplit%.*}
   mysqlImport $file ${fileSplit%.*}
  fi
 done
}

log "begin ..."

log "connect to $ip:$port/$db&user=$user&password=$password"

clearupTable

log "导出${table}数据统计文件"

dataCount

log "导出${table}表结构"

dbSchema

log "历史数据导出"

historyDump

log "成功导出历史数据 -> $backup_dir"

echo "-----------------------------------------------------------------------"

echo "-----------------------------------------------------------------------"

log "------------------- 将备份数据导入到${to_db}中 -------------------------"

loadData

log "------------------- 导入结束 --------------------------------------------"

truncate

log "执行结束 ..."

popd
