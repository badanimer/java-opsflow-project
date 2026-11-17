#!/bin/sh
# Runs once, when the MySQL volume of the deployment is new: the two accounts of ENVIRONMENT_SETUP.md, with the
# passwords of .env. The application account starts with the database-wide rights that
# db/admin/restrict-app-privileges.sql then narrows, as on the development machine.
set -e
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<SQL
CREATE USER 'opsflow_migrator'@'%' IDENTIFIED BY '${OPSFLOW_MIGRATION_PASSWORD}';
CREATE USER 'opsflow_app'@'%' IDENTIFIED BY '${OPSFLOW_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON opsflow.* TO 'opsflow_migrator'@'%';
GRANT SELECT, INSERT, UPDATE ON opsflow.* TO 'opsflow_app'@'%';
SQL
