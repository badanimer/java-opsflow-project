# Makes deploy/.env for the local deployment: random passwords for MySQL root, the two accounts, and RabbitMQ, and
# a random token signing key. Nothing is printed, and nobody types a secret. Git ignores .env.
# It refuses to replace an .env that exists, because the MySQL volume keeps the passwords it was made with.
$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot '.env'
if (Test-Path $path) {
    Write-Host ".env exists already and is kept. To start again: docker compose down -v, delete .env, run this again."
    exit 0
}

$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
function New-Secret([int]$length) {
    # Letters and digits only, so the value needs no quoting in SQL, YAML, or a shell.
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $length
    $random.GetBytes($bytes)
    -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}
$key = New-Object byte[] 32
$random.GetBytes($key)

@(
    "# Made by new-env.ps1. Secrets: never commit, copy, or print this file."
    "MYSQL_ROOT_PASSWORD=$(New-Secret 32)"
    "OPSFLOW_DB_PASSWORD=$(New-Secret 32)"
    "OPSFLOW_MIGRATION_PASSWORD=$(New-Secret 32)"
    "OPSFLOW_RABBITMQ_PASSWORD=$(New-Secret 32)"
    "OPSFLOW_TOKEN_SIGNING_KEY=$([Convert]::ToBase64String($key))"
) | Set-Content -Path $path -Encoding ascii
Write-Host ".env is made, with new random secrets."
