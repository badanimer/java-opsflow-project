#!/bin/sh
# Makes deploy/.env for the local deployment, as new-env.ps1 does, for macOS and Linux: random passwords for
# MySQL root, the two accounts, and RabbitMQ, and a random token signing key. Nothing is printed, and nobody
# types a secret. Git ignores .env.
# It refuses to replace an .env that exists, because the MySQL volume keeps the passwords it was made with.
set -eu

cd "$(dirname "$0")"
if [ -e .env ]; then
    echo ".env exists already and is kept. To start again: docker compose down -v, delete .env, run this again."
    exit 0
fi

# Letters and digits only, so the value needs no quoting in SQL, YAML, or a shell; the same letters as
# new-env.ps1, without the ones that are easy to misread.
secret() {
    LC_ALL=C tr -dc 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789' < /dev/urandom | head -c 32
}

umask 077
{
    echo "# Made by new-env.sh. Secrets: never commit, copy, or print this file."
    echo "MYSQL_ROOT_PASSWORD=$(secret)"
    echo "OPSFLOW_DB_PASSWORD=$(secret)"
    echo "OPSFLOW_MIGRATION_PASSWORD=$(secret)"
    echo "OPSFLOW_RABBITMQ_PASSWORD=$(secret)"
    echo "OPSFLOW_TOKEN_SIGNING_KEY=$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
} > .env
echo ".env is made, with new random secrets."
