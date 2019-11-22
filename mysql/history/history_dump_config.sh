source ~/AtgoSysEnv.sh
ip=$mysql_ipAddress
port=$mysql_port
user=$mysql_username
password=$mysql_password
db=$mysql_databaseName
table=${1:-BAK_MESSAGE}
userDir=`pwd ~`
fileDumpFlag=0
mkdir -p $userDir/history_dump
backup_dir=$userDir/history_dump
MYSQL="mysql -h$ip -P$port -u$user -p$password $db"

# 导向的库
to_ip=$ip
to_port=$port
to_user=$user
to_password=$password
to_db=$db
TO_MYSQL="mysql -h$to_ip -P$to_port -u$to_user -p$to_password $to_db"
TO_MYSQL_IMPORT="mysql -h$to_ip -P$to_port -u$to_user -p$to_password -f -D $to_db"
