
installDirectory=$(cd "$(dirname "$0")"; pwd)

pushd "${installDirectory}"

. ./mysql_dump_config.sh

settleStatus=UNKNOWN
isDeleteTodayData="N"

fileDumpFlag=0
echo "connect to $ip:$port/$db&user=$user&password=$password"

cdate=`date +%Y%m%d`

log(){
 ctime=`date +%Y%m%d\ %H:%M:%S`
 echo "$ctime $1"
}

settleFilterType=""
settleStateGroup=""

validateSettleStateFilterType(){
 WHITE_PATTERN="^\\|(0|[1-9][0-9]*|-[1-9][0-9]*)(,(0|[1-9][0-9]*|-[1-9][0-9]*))*$"
 BLACK_PATTERN="^(0|[1-9][0-9]*|-[1-9][0-9]*)(,(0|[1-9][0-9]*|-[1-9][0-9]*))*\\|$"
 if [[ $settlement_state_filter_list =~ $WHITE_PATTERN ]];
 then
  settleFilterType="WHITELIST"
  log "白名单 -> $settlement_state_filter_list"
 elif [[ $settlement_state_filter_list =~ $BLACK_PATTERN ]];
 then
  settleFilterType="BLACKLIST"
  log "黑名单 -> $settlement_state_filter_list"
 else
  log "结算状态过滤配置错误[$settlement_state_filter_list]"
  exit 1
 fi
 settleStateGroup=$(echo "$settlement_state_filter_list" | sed 's/|//g')
 echo $settleStateGroup
}

validateDbSettleState(){
 setSystemCharacterSeparator ","
 log "开始校验 -> $settleFilterType gropu : $settleStateGroup"
 if [[ $settleFilterType = "WHITELIST" ]];then
  log "白名单筛选"
  for state in $settleStateGroup;
  do
   log "$state"
   if [[ $state = $settleStatus ]];then
    log "$settleStatus 在 $settleStateGroup 白名单中,后续将执行删除操作."
    isDeleteTodayData="Y"
    break
   fi
  done
 elif [[ $settleFilterType = "BLACKLIST" ]];then
  log "黑名单类型"
  isDeleteTodayData="Y"
  for state in $settleStateGroup;do
   if [[ $state = $settleStatus ]];then
    log "$settleStatus 在黑名单中,将无法继续执行删除操作"
    isDeleteTodayData="N"
    break
   fi
  done
 else
  resetSystemCharacterSeparator
  log "未知的状态过滤配置 -> $settleFilterType"
  exit 1
 fi
 resetSystemCharacterSeparator
}

sysDefaultIFS=$IFS
setSystemCharacterSeparator(){
  IFS=$1
  log "将系统字符分隔符设置成 $IFS"
}
resetSystemCharacterSeparator(){
  IFS=$sysDefaultIFS
  log "将系统字符分隔符重置为$IFS"
}

fileDumpCheck(){
for file in ${backup_dir}/*;
do
  log "目录下有 -> $file"
  if [[ "$file" = "$backup_dir/${table}_${cdate}.sql" ]]; then
        log "dump successful to $backup_dir/${table}_${cdate}.sql"
        fileDumpFlag=1
        break;
  fi
done
}

querySettleStatus(){
while read a
do
if [[ $a = "Status" ]]; then
  echo "skip tableName $a"
else
  settleStatus=$a
fi
done < <(echo "SELECT Status FROM ATGO_SETTLE WHERE Date = CURDATE()" | $MYSQL)
}

normalDump(){
log "结算已经完成,开始导出数据.."
validateDbSettleState
mysqldump -t -h$ip -P$port -u$user -p$password --single-transaction --where='"'$cdate'" = ReceivedDate' $db $table > $backup_dir/${table}_${cdate}.sql
if [[ $enable_import_to_otherDB -eq 0 ]];then
    sed -i 's/`'$table'`/`'${table}_${cdate}'`/' $backup_dir/${table}_${cdate}.sql
fi
fileDumpCheck
}

execDumpData(){
if [ "$settleStatus" = "UNKNOWN" ];then
    log "结算未完成... 执行超时等待$query_settlement_state_timeout 秒"
    sleep $query_settlement_state_timeout
    querySettleStatus
    if [ "$settleStatus" != "UNKNOWN" ];then
      normalDump
    else
     log "结算未完成... "
     if [[ $settlement_fail_keep_dump -eq 0 ]];then
          log "导出数据照常执行.."
          mysqldump -t -h$ip -P$port -u$user -p$password --single-transaction --where='"'$cdate'" = ReceivedDate' $db $table > $backup_dir/${table}_${cdate}.sql
        if [[ $enable_import_to_otherDB -eq 0 ]];then
            sed -i 's/`'$table'`/`'${table}_${cdate}'`/' $backup_dir/${table}_${cdate}.sql
        fi
     fi
   fi
 else
  normalDump
fi
}


execDeleteData(){
log "开始清除数据库数据 ... "
log "是否删除 $isDeleteTodayData"
if [[ $fileDumpFlag -eq 1 ]] && [[ $isDeleteTodayData = "Y" ]];
then
  $MYSQL -e "delete from $table where DATE_FORMAT(CURDATE(),'%Y%m%d') = ReceivedDate; alter table $table engine=InnoDB;"
  log "成功清除数据..."
else
  log "无法清除当日数据,结算状态$settleStatus,文件dump状态$fileDumpFlag ... 请重试!"
  log "结算状态$settleStatus 可能不满足黑白名单配置: $settlement_state_filter_list"
fi
}

dbSchema(){
 mysqldump -n -d -h$ip -P$port -u$user -p$password --single-transaction --compact $db $table > $backup_dir/${table}_SCHEMA.txt
}

generateNewTabale(){
 newTableSchema=$(sed 's/`'$table'`/`'${table}_${cdate}'`/;s/`'${table}_${cdate}'`/IF NOT EXISTS & /' $backup_dir/${table}_SCHEMA.txt)
 $TO_MYSQL -e "$newTableSchema"
}

execImportToDb(){
if [[ $enable_import_to_otherDB -eq 0 ]];then
    log "is enable import to other db"
    fileDumpCheck
    if [[ $fileDumpFlag -eq 1 ]];then
       dbSchema
       generateNewTabale
       log "load data file [$backup_dir/${table}_${cdate}.sql] to -> $to_ip:$to_port/$to_db,table[${table}_${cdate}]"
       $TO_MYSQL_IMPORT < $backup_dir/${table}_${cdate}.sql
    fi
fi
}

zipDumpSqlFile(){
 log "zip backup data"
 zip -mj ${backup_dir}/${table}_${cdate}.sql.zip $backup_dir/${table}_${cdate}.sql
}

log "begin ..."

validateSettleStateFilterType

querySettleStatus

log "结算状态 -> $settleStatus"

execDumpData

execDeleteData

execImportToDb

zipDumpSqlFile

log "执行结束 ..."
