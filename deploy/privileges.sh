#!/bin/sh
# Applies db/admin/restrict-app-privileges.sql to the application account of the deployment, after the migrations.
# The script names the account host 'localhost'; in the containers the account is '%'. The database-wide rights
# are given again first, because the script starts by revoking them and MySQL refuses to revoke a right that is
# not held: so the same steps run on every start, and a migration that added a table gets its grant too.
set -e
{
  echo "GRANT SELECT, INSERT, UPDATE ON opsflow.* TO 'opsflow_app'@'%';"
  sed "s/'localhost'/'%'/g" /deploy/restrict-app-privileges.sql
} | MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h mysql -uroot
echo "The rights of opsflow_app are set."
