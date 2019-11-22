source ~/AtgoSysEnv.sh
ip=$mysql_ipAddress
port=$mysql_port
user=$mysql_username
password=$mysql_password
db=$mysql_databaseName
table=${1:-BAK_MESSAGE}
userDir=`pwd ~`
mkdir -p $userDir/data
backup_dir=$userDir/data
MYSQL="mysql -h$ip -P$port -u$user -p$password $db"
# 结算失败是否继续导出数据 1: 否, 0:继续
settlement_fail_keep_dump=0
# 是否在数据导出后导入到其他的数据库 1: 否,0:是
enable_import_to_otherDB=1
# PATTERN: 1,2,3| or 3,2,1|
settlement_state_filter_list="0,-2,-3|"
# 秒
query_settlement_state_timeout=15

# 导向的库
to_ip=$ip
to_port=$port
to_user=$user
to_password=$password
to_db=atgodb_history
TO_MYSQL="mysql -h$to_ip -P$to_port -u$to_user -p$to_password $to_db"
TO_MYSQL_IMPORT="mysql -h$to_ip -P$to_port -u$to_user -p$to_password -f -D $to_db"
