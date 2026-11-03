<#
.SYNOPSIS
    Verifies the OpsFlow schema, the per-table privileges, and the order and inventory
    concurrency rules against a real MySQL server, in a temporary database.

.DESCRIPTION
    Database design v1.2. Nothing here touches the real `opsflow` database.

    What it does:
      1. Asks for the MySQL root password in a masked dialog. The password is used only to
         create a temporary database and two temporary accounts, and to drop the accounts at
         the end. It is never printed, logged, or stored.
      2. Applies db/migration/V1__initial_schema.sql and db/verification/concurrency-fixtures.sql.
      3. Applies db/admin/restrict-app-privileges.sql, adapted to the temporary names.
      4. Runs structure, privilege, and concurrency checks. The concurrency checks run
         several mysql sessions at the same time, released together by a lock barrier, under
         the restricted application account.
      5. Drops the temporary database and accounts.

    Re-run this script whenever the schema, the privileges, or the flows in
    docs/database/database-design.md change.

.PARAMETER Keep
    Keep the temporary database and accounts afterwards, and store their random passwords in
    the user-level variables OPSFLOW_VERIFY_ADMIN_PW and OPSFLOW_VERIFY_APP_PW. Allows -Quick.

.PARAMETER Quick
    Reuse a database kept by -Keep. No root password is needed. Recreates the schema first.

.PARAMETER Rounds
    Repetitions of each race. More rounds make a rare interleaving more likely to show up.
#>
[CmdletBinding()]
param(
    [switch]$Keep,
    [switch]$Quick,
    [int]$Rounds = 12
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$mysql = $null
$cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
if ($cmd) { $mysql = $cmd.Source } else { $mysql = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' }
if (-not (Test-Path $mysql)) { throw "mysql.exe not found: $mysql" }

$repo      = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$migration = Join-Path $repo 'db\migration\V1__initial_schema.sql'
$fixtures  = Join-Path $PSScriptRoot 'concurrency-fixtures.sql'
$restrict  = Join-Path $repo 'db\admin\restrict-app-privileges.sql'
$DB    = 'opsflow_verify'
$ADMIN = 'verify_admin'
$APP   = 'verify_app'
$tmp = Join-Path $env:TEMP ('opsflow-verify-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null
$script:seq = 0
$script:retries = 0
$script:pass = 0
$script:fail = 0
$rootPw = $null
$adminPw = $null
$appPw = $null

# ---------------------------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------------------------

function New-RandomPassword([int]$Length = 32) {
    $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'.ToCharArray()
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $buf = New-Object byte[] 4
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $Length; $i++) {
        $rng.GetBytes($buf)
        [void]$sb.Append($chars[[BitConverter]::ToUInt32($buf, 0) % $chars.Length])
    }
    $sb.ToString()
}

function Q([string]$s) { "'" + ($s.Replace('\', '\\').Replace("'", "''")) + "'" }

function Read-RootPassword {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    [System.Windows.Forms.Application]::EnableVisualStyles()
    $form = New-Object System.Windows.Forms.Form
    $form.Text = 'OpsFlow database verification'
    $form.StartPosition = 'CenterScreen'
    $form.TopMost = $true
    $form.FormBorderStyle = 'FixedDialog'
    $form.MaximizeBox = $false; $form.MinimizeBox = $false
    $form.ClientSize = New-Object System.Drawing.Size(420, 150)
    $l = New-Object System.Windows.Forms.Label
    $l.Text = 'MySQL root password (used only to create a temporary database):'
    $l.Location = '15,15'; $l.AutoSize = $true
    $t = New-Object System.Windows.Forms.TextBox
    $t.UseSystemPasswordChar = $true; $t.Location = '15,45'; $t.Width = 390
    $ok = New-Object System.Windows.Forms.Button
    $ok.Text = 'Run'; $ok.Location = '255,100'; $ok.Width = 70
    $ok.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $cancel = New-Object System.Windows.Forms.Button
    $cancel.Text = 'Cancel'; $cancel.Location = '335,100'; $cancel.Width = 70
    $cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
    $form.AcceptButton = $ok; $form.CancelButton = $cancel
    $form.Controls.AddRange(@($l, $t, $ok, $cancel))
    $form.Add_Shown({ $form.Activate(); $t.Focus() })
    $r = $form.ShowDialog()
    $pw = $t.Text
    $form.Dispose()
    if (($r -eq [System.Windows.Forms.DialogResult]::OK) -and $pw) { $pw } else { $null }
}

# Starts one mysql session. The password travels in the child's environment only.
function Start-Sess {
    param([string]$User, [string]$Pass, [string]$Sql, [string]$Database = $DB, [string]$File, [switch]$Force)
    $script:seq++
    $id = $script:seq
    $in = $File
    if (-not $in) {
        $in = Join-Path $tmp "s$id.sql"
        [System.IO.File]::WriteAllText($in, "SET time_zone = '+00:00';`n" + $Sql, [System.Text.UTF8Encoding]::new($false))
    }
    $out = Join-Path $tmp "s$id.out"
    $err = Join-Path $tmp "s$id.err"
    $a = @('-u', $User, '--batch', '--skip-column-names', '--protocol=TCP', '-h', '127.0.0.1')
    if ($Force) { $a += '--force' }
    if ($Database) { $a += $Database }
    $env:MYSQL_PWD = $Pass
    try {
        $p = Start-Process -FilePath $mysql -ArgumentList $a -RedirectStandardInput $in -RedirectStandardOutput $out -RedirectStandardError $err -NoNewWindow -PassThru
        $null = $p.Handle
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    [pscustomobject]@{ Proc = $p; Out = $out; Err = $err }
}

function Wait-Sess($s) {
    $s.Proc.WaitForExit()
    $o = ''; $e = ''
    if (Test-Path $s.Out) { $o = [string](Get-Content -Raw -LiteralPath $s.Out) }
    if (Test-Path $s.Err) { $e = [string](Get-Content -Raw -LiteralPath $s.Err) }
    [pscustomobject]@{ Code = $s.Proc.ExitCode; Out = $o; Err = $e }
}

function Run-Sql {
    param([string]$User, [string]$Pass, [string]$Sql, [string]$Database = $DB, [switch]$Force)
    Wait-Sess (Start-Sess -User $User -Pass $Pass -Sql $Sql -Database $Database -Force:$Force)
}

function Run-File {
    param([string]$User, [string]$Pass, [string]$File, [string]$Database = $DB)
    Wait-Sess (Start-Sess -User $User -Pass $Pass -File $File -Database $Database)
}

# Only the error number and SQLSTATE are ever shown, never the statement text.
function Short-Err([string]$e) {
    $m = [regex]::Match($e, 'ERROR \d+ \(\w+\)')
    if ($m.Success) { $m.Value } else { '' }
}

function Result($r) {
    $m = [regex]::Match([string]$r.Out, 'RESULT=([^\r\n]*)')
    if ($m.Success) { $m.Groups[1].Value } else { 'NO_RESULT ' + (Short-Err $r.Err) }
}

function Scalar([string]$sql) {
    $r = Run-Sql $ADMIN $adminPw $sql
    $m = [regex]::Match([string]$r.Out, 'RESULT=([^\r\n]*)')
    if ($m.Success) { $m.Groups[1].Value } else { 'NO_RESULT ' + (Short-Err $r.Err) }
}

# Runs the given sessions at the same moment. A session that fails with a deadlock or a lock
# wait timeout is run again, up to two more times, the way the application would.
function Run-Parallel {
    param([object[]]$Specs, [switch]$NoRetry)
    $hold = [Math]::Round(2.0 + 0.4 * $Specs.Count, 1)
    $ctl = Start-Sess -User $ADMIN -Pass $adminPw -Sql "SELECT GET_LOCK('go', 0);`nSELECT SLEEP($hold);`nDO RELEASE_LOCK('go');"
    Start-Sleep -Milliseconds 1200
    $barrier = "SELECT GET_LOCK('go', 60);`nDO RELEASE_LOCK('go');`n"
    $started = @()
    foreach ($sp in $Specs) {
        $started += , (Start-Sess -User $sp.User -Pass $sp.Pass -Sql ($barrier + $sp.Sql) -Force:([bool]$sp.Force))
    }
    $results = @()
    foreach ($s in $started) { $results += , (Wait-Sess $s) }
    $null = Wait-Sess $ctl
    if (-not $NoRetry) {
        for ($i = 0; $i -lt $Specs.Count; $i++) {
            $n = 0
            while (((Result $results[$i]) -like 'RETRYABLE*') -and ($n -lt 2)) {
                $n++; $script:retries++
                $results[$i] = Run-Sql $Specs[$i].User $Specs[$i].Pass $Specs[$i].Sql
            }
        }
    }
    , $results
}

function Check([string]$name, [bool]$ok, [string]$detail = '') {
    $tail = ''
    if ($detail) { $tail = "  ($detail)" }
    if ($ok) { $script:pass++; Write-Output "PASS  $name$tail" } else { $script:fail++; Write-Output "FAIL  $name$tail" }
}

function Allowed($r) { $r.Code -eq 0 }
function Denied($r)  { ($r.Code -ne 0) -and (($r.Out + $r.Err) -match 'ERROR (1044|1142|1143|1227|1370)') }
function Dup($r)     { ($r.Code -ne 0) -and ($r.Err -match 'ERROR 1062') }

function Section([string]$title) { Write-Output ''; Write-Output "=== $title ===" }

function As-App([string]$sql)   { Run-Sql $APP $appPw $sql }
function As-Admin([string]$sql) { Run-Sql $ADMIN $adminPw $sql }

# ---------------------------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------------------------

try {
    if ($Quick) {
        $adminPw = [Environment]::GetEnvironmentVariable('OPSFLOW_VERIFY_ADMIN_PW', 'User')
        $appPw   = [Environment]::GetEnvironmentVariable('OPSFLOW_VERIFY_APP_PW', 'User')
        if ((-not $adminPw) -or (-not $appPw)) { throw 'No kept verification database. Run once with -Keep first.' }
        Write-Output 'Quick mode: reusing the kept temporary accounts.'
    } else {
        Write-Output 'Waiting for the masked root password dialog...'
        $rootPw = Read-RootPassword
        if (-not $rootPw) { Write-Output 'Cancelled. Nothing changed.'; return }
        $adminPw = New-RandomPassword
        $appPw   = New-RandomPassword
        $setup = @"
DROP DATABASE IF EXISTS $DB;
CREATE DATABASE $DB DEFAULT CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS '$ADMIN'@'localhost' IDENTIFIED BY $(Q $adminPw);
ALTER USER '$ADMIN'@'localhost' IDENTIFIED BY $(Q $adminPw);
CREATE USER IF NOT EXISTS '$APP'@'localhost' IDENTIFIED BY $(Q $appPw);
ALTER USER '$APP'@'localhost' IDENTIFIED BY $(Q $appPw);
GRANT ALL PRIVILEGES ON $DB.* TO '$ADMIN'@'localhost' WITH GRANT OPTION;
"@
        $r = Run-Sql 'root' $rootPw $setup -Database ''
        $setup = $null
        if ($r.Code -ne 0) { throw ('Could not create the temporary accounts ' + (Short-Err $r.Err)) }
        if ($Keep) {
            [Environment]::SetEnvironmentVariable('OPSFLOW_VERIFY_ADMIN_PW', $adminPw, 'User')
            [Environment]::SetEnvironmentVariable('OPSFLOW_VERIFY_APP_PW', $appPw, 'User')
        }
        Write-Output 'Temporary database and accounts created.'
    }

    # Fresh schema in both modes. The application account starts with the interim database-wide
    # grants that exist on the real server before the first migration.
    $r = Run-Sql $ADMIN $adminPw "DROP DATABASE IF EXISTS $DB; CREATE DATABASE $DB DEFAULT CHARACTER SET utf8mb4;" -Database ''
    if ($r.Code -ne 0) { throw ('Could not recreate the database ' + (Short-Err $r.Err)) }
    $r = Run-File $ADMIN $adminPw $migration
    Check 'migration V1 applies cleanly' (Allowed $r) (Short-Err $r.Err)
    if ($r.Code -ne 0) { throw 'Migration failed' }
    $r = Run-File $ADMIN $adminPw $fixtures
    Check 'verification procedures load' (Allowed $r) (Short-Err $r.Err)
    if ($r.Code -ne 0) { throw 'Fixtures failed' }
    $r = As-Admin "GRANT SELECT, INSERT, UPDATE ON $DB.* TO '$APP'@'localhost'; GRANT EXECUTE ON $DB.* TO '$APP'@'localhost';"
    if ($r.Code -ne 0) { throw ('Could not grant interim privileges ' + (Short-Err $r.Err)) }

    # -----------------------------------------------------------------------------------------
    Section 'Structure'
    # -----------------------------------------------------------------------------------------
    Check '16 tables exist' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.tables WHERE table_schema='$DB' AND table_type='BASE TABLE';") -eq '16')
    Check 'order_items has no updated_at (frozen)' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='order_items' AND column_name='updated_at';") -eq '0')
    Check 'inventory has no locked_quantity column and orders has no status, reservation, or cancellation columns (an order is final)' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.columns WHERE table_schema='$DB' AND ((table_name='inventory' AND column_name='locked_quantity') OR (table_name='orders' AND column_name IN ('status','reserved_until','finished_at','status_reason','cancel_reason','cancelled_by','cancelled_at')));") -eq '0')
    Check 'inventory has no version column' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='inventory' AND column_name='version';") -eq '0')
    $coll = Scalar "SELECT CONCAT('RESULT=', GROUP_CONCAT(CONCAT(table_name, '.', column_name, '=', collation_name) ORDER BY table_name, column_name)) FROM information_schema.columns WHERE table_schema='$DB' AND column_name IN ('order_no','ticket_no','idempotency_key');"
    Check 'order_no (orders, order_attempts, and tickets), ticket_no, idempotency_key use utf8mb4_0900_bin' ((($coll -split ',').Count -eq 5) -and (-not ($coll -match 'ai_ci'))) $coll

    $null = As-Admin "CALL seed(5);"
    $r = As-Admin "INSERT INTO orders(order_no,user_id) VALUES ('ORD-aaa',1),('ORD-AAA',1);"
    Check 'order_no is case-sensitive (ORD-aaa and ORD-AAA both accepted)' (Allowed $r)
    $r = As-Admin "INSERT INTO orders(order_no,user_id) VALUES ('ORD-aaa',1);"
    Check 'identical order_no is still rejected' (Dup $r)
    $r1 = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,product_name,order_id,quantity,result,stock_deducted) SELECT 'ATT-1',1,1,'Widget',id,2,'COMPLETED',2 FROM orders WHERE order_no='ORD-aaa';"
    $r2 = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,product_name,order_id,quantity,result,stock_deducted) VALUES ('ATT-2',1,1,'Widget',NULL,0,'INVALID_QUANTITY',0);"
    Check 'an attempt can be recorded as completed (with its order and the stock deducted) or as refused (no order, nothing deducted)' ((Allowed $r1) -and (Allowed $r2))
    $r1 = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,product_name,order_id,quantity,result,stock_deducted) VALUES ('ATT-3',1,2,'Widget',NULL,2,'COMPLETED',2);"
    $r2 = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,product_name,order_id,quantity,result,stock_deducted) SELECT 'ATT-4',1,2,'Widget',id,2,'COMPLETED',1 FROM orders WHERE order_no='ORD-aaa';"
    $r3 = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,product_name,order_id,quantity,result,stock_deducted) SELECT 'ATT-5',1,2,'Widget',id,1,'OUT_OF_STOCK',0 FROM orders WHERE order_no='ORD-aaa';"
    $r4 = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,product_name,order_id,quantity,result,stock_deducted) VALUES ('ATT-6',1,2,'Widget',NULL,1,'OUT_OF_STOCK',1);"
    Check 'an attempt whose outcome contradicts its result is rejected (a completed one without its order or with another deduction, a refused one with an order or a deduction)' ((($r1.Code -ne 0) -and ($r1.Err -match 'ERROR 3819')) -and (($r2.Code -ne 0) -and ($r2.Err -match 'ERROR 3819')) -and (($r3.Code -ne 0) -and ($r3.Err -match 'ERROR 3819')) -and (($r4.Code -ne 0) -and ($r4.Err -match 'ERROR 3819')))
    $r = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,product_name,order_id,quantity,result,stock_deducted) VALUES ('ATT-7',1,2,'Widget',NULL,1,'SHRUG',0);"
    Check 'an attempt with an unknown result is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,product_name,order_id,quantity,result,stock_deducted) VALUES ('ATT-2',1,1,'Widget',NULL,5,'BUSY',0);"
    Check 'one product line of one order number is recorded once' (Dup $r)
    $r = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,product_name,order_id,quantity,result,stock_deducted) VALUES ('ATT-8',1,999999,'Widget',NULL,1,'BUSY',0);"
    Check 'an attempt needs an existing product' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "INSERT INTO order_attempts(order_no,user_id,product_id,order_id,quantity,result,stock_deducted) VALUES ('ATT-9',1,1,NULL,1,'BUSY',0);"
    Check 'an attempt needs the name of the product' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1364'))
    $r = As-Admin "INSERT INTO orders(order_no,user_id,idempotency_key) VALUES ('ORD-k1',1,'Abc'),('ORD-k2',1,'abc');"
    Check 'idempotency keys Abc and abc are different for one user' (Allowed $r)
    $r = As-Admin "INSERT INTO orders(order_no,user_id,idempotency_key) VALUES ('ORD-k3',1,'abc');"
    Check 'the same key for the same user is rejected' (Dup $r)
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('TKTAAAA1',1,'OTHER','MANUAL','t','d'),('TKTAAAA2',1,'OTHER','MANUAL','t','d');"
    Check 'two different ticket numbers of 8 characters are accepted' (Allowed $r)
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('TKTSHORT',1,'OTHER','MANUAL','t','d'),('SHORT',1,'OTHER','MANUAL','t','d');"
    Check 'a ticket number that is not 8 characters is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('tktlower',1,'OTHER','MANUAL','t','d');"
    Check 'a ticket number with lower-case letters is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO users(username,password_hash,email) VALUES ('Alice','x','alice@example.com'),('alice','x','alice2@example.com');"
    Check 'usernames keep the case-insensitive rule (Alice vs alice rejected)' (Dup $r)
    Check 'users.failed_login_count defaults to 0' ((Scalar "SELECT CONCAT('RESULT=', COLUMN_DEFAULT) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='users' AND column_name='failed_login_count';") -eq '0')
    Check 'users.is_system defaults to FALSE' ((Scalar "SELECT CONCAT('RESULT=', COLUMN_DEFAULT) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='users' AND column_name='is_system';") -eq '0')
    Check 'users has username_changed_at and email_changed_at, both nullable and empty by default (reserved for a later change flow)' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='users' AND column_name IN ('username_changed_at','email_changed_at') AND is_nullable='YES' AND column_default IS NULL;") -eq '2')
    Check 'inventory_transactions has the index for the stock log of one item by day' ((Scalar "SELECT CONCAT('RESULT=', COUNT(DISTINCT index_name)) FROM information_schema.statistics WHERE table_schema='$DB' AND table_name='inventory_transactions' AND index_name='idx_inventory_tx_inventory_created';") -eq '1')
    Check 'the four log tables have a time index for the log of all products and all items' ((Scalar "SELECT CONCAT('RESULT=', COUNT(DISTINCT index_name)) FROM information_schema.statistics WHERE table_schema='$DB' AND index_name IN ('idx_product_events_created','idx_order_attempts_created','idx_item_events_created','idx_inventory_tx_created');") -eq '4')
    Check 'tickets has no priority column' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='tickets' AND column_name='priority';") -eq '0')
    Check 'tickets has the indexes for the open count, the order lookup, the archive job, and the system-first lists' ((Scalar "SELECT CONCAT('RESULT=', COUNT(DISTINCT index_name)) FROM information_schema.statistics WHERE table_schema='$DB' AND table_name='tickets' AND index_name IN ('idx_tickets_created_status','idx_tickets_order_status','idx_tickets_status_resolved','idx_tickets_status_source_created','idx_tickets_status_source_closed','idx_tickets_status_source_cancelled');") -eq '6')
    Check 'operation_logs has its category list index, and orders has no index for an order list (there is none)' ((Scalar "SELECT CONCAT('RESULT=', COUNT(DISTINCT index_name)) FROM information_schema.statistics WHERE table_schema='$DB' AND index_name IN ('idx_orders_created','idx_operation_logs_category_created');") -eq '1')
    $r1 = As-Admin "INSERT INTO operation_logs(category,action,target_type,target_id) VALUES ('STOCK','STOCK_RESTOCK','ITEM',1);"
    $r2 = As-Admin "INSERT INTO operation_logs(category,action,target_type,target_id) VALUES ('ORDER','X','ORDER',1);"
    $r3 = As-Admin "INSERT INTO operation_logs(action,target_type,target_id) VALUES ('X','ORDER',1);"
    Check 'operation_logs needs a known category: orders have none, and a row without a category is refused' ((Allowed $r1) -and ($r2.Code -ne 0) -and ($r2.Err -match 'ERROR 3819') -and ($r3.Code -ne 0)) "$($r2.Err) / $($r3.Err)"
    $null = As-Admin "DELETE FROM operation_logs;"
    Check 'the warehouse and product indexes for the catalog and the product page exist, and the unused date index is gone' ((Scalar "SELECT CONCAT('RESULT=', COUNT(DISTINCT index_name)) FROM information_schema.statistics WHERE table_schema='$DB' AND index_name IN ('uk_inventory_name_active','idx_products_status_name','idx_inventory_name','idx_inventory_created');") -eq '2')
    $null = As-Admin "INSERT INTO inventory(id,sku,name,created_by) VALUES (20,'TESTSK20','Item 20',1),(21,'TESTSK21','Item 21',1);"
    $r = As-Admin "INSERT INTO inventory(sku,name,created_by) VALUES ('TESTSK20','same sku again',1);"
    Check 'two warehouse items cannot share a SKU' (Dup $r)
    $r = As-Admin "INSERT INTO inventory(sku,name,created_by) VALUES ('TESTSK22','no creator',999999);"
    Check 'a warehouse item needs a real user as its creator' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "INSERT INTO inventory(sku,name,created_by) VALUES ('SHORT','short sku',1);"
    Check 'a SKU that is not 8 characters is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO inventory(sku,name,created_by) VALUES ('lowercas','lower case sku',1);"
    Check 'a SKU with lower-case letters is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO inventory(sku,name,created_by) VALUES ('TESTSK23','item 20',1);"
    Check 'two warehouse items cannot share a name, whatever the case' (Dup $r)
    $r = As-Admin "UPDATE inventory SET deleted_at=NOW(3), deleted_by=1, available_quantity=0 WHERE id=20;"
    $r2 = As-Admin "INSERT INTO inventory(sku,name,created_by) VALUES ('TESTSK24','Item 20',1);"
    Check 'the name of a deleted item can be used again' ((Allowed $r) -and (Allowed $r2)) ((Short-Err $r.Err) + ' ' + (Short-Err $r2.Err))
    $r = As-Admin "UPDATE inventory SET deleted_at=NOW(3) WHERE id=21;"
    Check 'a deleted item must carry both the time and the user' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $null = As-Admin "UPDATE inventory SET available_quantity=3 WHERE id=21;"
    $r = As-Admin "UPDATE inventory SET deleted_at=NOW(3), deleted_by=1 WHERE id=21;"
    Check 'an item that still shows stock can be marked deleted, because the stock no longer matters' (Allowed $r) (Short-Err $r.Err)
    $null = As-Admin "UPDATE inventory SET deleted_at=NULL, deleted_by=NULL, available_quantity=0 WHERE id=21;"
    $r = As-Admin "INSERT INTO inventory_item_events(inventory_id,action,sku,name,quantity,operator_id) VALUES (21,'CREATED','TESTSK21','Item 21',0,1),(21,'DELETED','TESTSK21','Item 21',3,1);"
    Check 'an item event records the item, the SKU, the name, the quantity, and the user' ((Allowed $r) -and ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM inventory_item_events WHERE inventory_id=21;") -eq '2')) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO inventory_item_events(inventory_id,action,sku,name,quantity,operator_id) VALUES (21,'MOVED','TESTSK21','Item 21',0,1);"
    Check 'an item event with an unknown action is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO inventory_item_events(inventory_id,action,sku,name,quantity,operator_id) VALUES (21,'CREATED','TESTSK21','Item 21',-1,1);"
    Check 'an item event with a negative quantity is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "DELETE FROM inventory WHERE id=21;"
    Check 'a warehouse item with events cannot be deleted' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1451'))
    $r = As-Admin "INSERT INTO products(inventory_id,name,price,status,listed_at,listed_by) VALUES (20,'Item 20',4.50,'LISTED',NOW(3),1);"
    Check 'an item can be listed as a product' (Allowed $r) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO products(inventory_id,name,price,status,listed_at,listed_by) VALUES (20,'Item 20 again',4.50,'LISTED',NOW(3),1);"
    Check 'an item has at most one product' (Dup $r)
    $r = As-Admin "INSERT INTO products(inventory_id,name,price,status,listed_at,listed_by) VALUES (999999,'x',1.00,'LISTED',NOW(3),1);"
    Check 'a product needs an existing warehouse item' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "INSERT INTO products(inventory_id,name,price,status,listed_at,listed_by) VALUES (21,'x',-1.00,'LISTED',NOW(3),1);"
    Check 'a negative price is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO products(inventory_id,name,price,status,listed_at,listed_by) VALUES (21,'x',1.00,'ACTIVE',NOW(3),1);"
    Check 'a product status other than LISTED or UNLISTED is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO products(inventory_id,name,price,status,listed_at,listed_by,delisted_at,delisted_by) VALUES (21,'x',1.00,'LISTED',NOW(3),1,NOW(3),1);"
    Check 'a LISTED product cannot carry delisting fields' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO products(inventory_id,name,price,status,listed_at,listed_by) VALUES (21,'x',1.00,'UNLISTED',NOW(3),1);"
    Check 'an UNLISTED product must carry its delisting fields' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "UPDATE products SET status='UNLISTED', delisted_at=NOW(3), delisted_by=1 WHERE inventory_id=20;"
    Check 'a product can be delisted with a time and a user' (Allowed $r) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO product_listing_events(product_id,inventory_id,action,name,price,operator_id) SELECT id,inventory_id,'DELISTED',name,price,1 FROM products WHERE inventory_id=20;"
    Check 'a listing event records the product, the item, the name, the price, and the user' ((Allowed $r) -and ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM product_listing_events WHERE action='DELISTED' AND inventory_id=20 AND price=4.50;") -eq '1')) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO product_listing_events(product_id,inventory_id,action,name,price,operator_id) SELECT id,inventory_id,'HIDDEN',name,price,1 FROM products WHERE inventory_id=20;"
    Check 'a listing event with an unknown action is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "DELETE FROM products WHERE inventory_id=20;"
    Check 'a product with listing events cannot be deleted' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1451'))
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('TKTSRC01',1,'OTHER','MAYBE','t','d');"
    Check 'a ticket source other than SYSTEM or MANUAL is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('TKTSYS01',1,'OTHER','SYSTEM','t','d');"
    Check 'a ticket with source SYSTEM is accepted' (Allowed $r) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('TKTNOSRC',1,'OTHER',NULL,'t','d');"
    Check 'a ticket without a source is rejected' ($r.Code -ne 0)
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('TKTOE001',1,'ORDER_EXCEPTION','MANUAL','t','d');"
    Check 'an ORDER_EXCEPTION ticket without an order number is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $null = As-Admin "INSERT INTO orders(id,order_no,user_id) VALUES (200,'ORD-oe',1);"
    $r = As-Admin "INSERT INTO tickets(ticket_no,order_no,created_by,type,source,title,description) VALUES ('TKTOE002','ATT-2',1,'ORDER_EXCEPTION','MANUAL','t','d');"
    Check 'an ORDER_EXCEPTION ticket that names an order number is accepted (the number of a refused attempt too)' (Allowed $r) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('TKTOE003',1,'INVENTORY_EXCEPTION','MANUAL','t','d');"
    Check 'only an ORDER_EXCEPTION ticket needs an order number' (Allowed $r) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO email_verification_codes(email,purpose,code_hash,expires_at) VALUES ('x@example.com','OTHER','h',NOW(3));"
    Check 'email_verification_codes rejects an unknown purpose' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $null = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('TKTBATCH',1,'INVENTORY_EXCEPTION','MANUAL','batch','several products');"
    $r = As-Admin "INSERT INTO ticket_inventory(ticket_id,inventory_id) SELECT t.id,i.id FROM tickets t, inventory i WHERE t.ticket_no='TKTBATCH' AND i.id IN (1,2,3);"
    Check 'one ticket can cover several inventory records' ((Allowed $r) -and ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM ticket_inventory;") -eq '3')) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO ticket_inventory(ticket_id,inventory_id) SELECT t.id,1 FROM tickets t WHERE t.ticket_no='TKTBATCH';"
    Check 'the same inventory record cannot be linked to a ticket twice' (Dup $r)
    $r = As-Admin "INSERT INTO ticket_inventory(ticket_id,inventory_id) SELECT t.id,999999 FROM tickets t WHERE t.ticket_no='TKTBATCH';"
    Check 'a link to a missing inventory record is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "DELETE FROM tickets WHERE ticket_no='TKTBATCH';"
    Check 'a ticket with linked inventory records cannot be deleted' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1451'))
    $null = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description) VALUES ('TKTFIRST',1,'OTHER','MANUAL','first','first problem');"
    $r = As-Admin "UPDATE tickets SET status='CANCELLED', cancel_reason='opened by mistake', cancelled_by=1, cancelled_at=NOW(3) WHERE ticket_no='TKTFIRST';"
    Check 'a ticket can be CANCELLED with a reason, a canceller, and a time' ((Allowed $r) -and ((Scalar "SELECT CONCAT('RESULT=', status, '|', cancel_reason) FROM tickets WHERE ticket_no='TKTFIRST';") -eq 'CANCELLED|opened by mistake')) (Short-Err $r.Err)
    $r = As-Admin "UPDATE tickets SET status='REOPENED' WHERE ticket_no='TKTFIRST';"
    Check 'there is no REOPENED status' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $null = As-Admin "UPDATE tickets SET status='CLOSED', closed_by=1, closed_at=NOW(3) WHERE ticket_no='TKTFIRST';"
    Check 'a ticket can be CLOSED with a closer and a time' ((Scalar "SELECT CONCAT('RESULT=', status, '|', closed_by) FROM tickets WHERE ticket_no='TKTFIRST';") -eq 'CLOSED|1')
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description,follows_ticket_id) SELECT 'TKTSECND',1,'OTHER','MANUAL','second','continues the first',id FROM tickets WHERE ticket_no='TKTFIRST';"
    Check 'a ticket can follow an earlier ticket' ((Allowed $r) -and ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM tickets f JOIN tickets p ON p.id=f.follows_ticket_id WHERE f.ticket_no='TKTSECND' AND p.ticket_no='TKTFIRST';") -eq '1')) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,source,title,description,follows_ticket_id) VALUES ('TKTTHIRD',1,'OTHER','MANUAL','third','missing',999999);"
    Check 'following a ticket that does not exist is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "DELETE FROM tickets WHERE ticket_no='TKTFIRST';"
    Check 'a ticket that has a follow-up cannot be deleted' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1451'))
    $r = As-Admin "UPDATE tickets SET closed_by=999999 WHERE ticket_no='TKTSECND';"
    Check 'closed_by must be an existing user' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "UPDATE tickets SET cancelled_by=999999 WHERE ticket_no='TKTSECND';"
    Check 'cancelled_by must be an existing user' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $itx = "INSERT INTO inventory_transactions(inventory_id,operator_id,type,quantity,available_before,available_after,reason) "
    $r = As-Admin ($itx + "VALUES (1,1,'WRITE_OFF',2,10,8,'damaged in storage');")
    Check 'a WRITE_OFF with a reason and an operator is accepted' (Allowed $r) (Short-Err $r.Err)
    $r = As-Admin ($itx + "VALUES (1,1,'WRITE_OFF',2,10,12,'wrong direction');")
    Check 'a WRITE_OFF that raises stock is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    foreach ($ty in 'RESTOCK','WRITE_OFF','ADJUST_IN','ADJUST_OUT') {
        $after = if ($ty -in 'RESTOCK','ADJUST_IN') { 12 } else { 8 }
        $r = As-Admin ($itx + "VALUES (1,1,'$ty',2,10,$after,NULL);")
        Check "$ty without a reason is rejected" (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
        $r = As-Admin ($itx + "VALUES (1,1,'$ty',2,10,$after,'   ');")
        Check "$ty with a blank reason is rejected" (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
        $r = As-Admin ($itx + "VALUES (1,NULL,'$ty',2,10,$after,'no operator');")
        Check "$ty without an operator is rejected" (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1048'))
    }

    # Orders are final, and a sale is the only order transaction.
    $null = As-Admin "INSERT INTO orders(id,order_no,user_id) VALUES (201,'ORD-st2',1),(202,'ORD-st3',1),(203,'ORD-st4',1);"
    $r = As-Admin ($itx.Replace(',reason) ', ',order_id,reason) ') + "VALUES (1,1,'RETURN',2,10,12,202,'give back');")
    Check 'RETURN is not a transaction type (an order is never cancelled)' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin ($itx.Replace(',reason) ', ',order_id) ') + "VALUES (1,1,'SALE',2,10,8,NULL);")
    Check 'a SALE without an order is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin ($itx.Replace(',reason) ', ',order_id) ') + "VALUES (1,1,'SALE',2,10,12,202);")
    Check 'a SALE that raises stock is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $r = As-Admin ($itx.Replace(',reason) ', ',order_id) ') + "VALUES (1,1,'SALE',2,10,8,202);")
    Check 'a SALE with its order and no reason is accepted' (Allowed $r) (Short-Err $r.Err)
    $r = As-Admin ($itx.Replace(',reason) ', ',order_id) ') + "VALUES (1,1,'SALE',2,8,6,202);")
    Check 'the same order cannot be sold twice from one item' (Dup $r)
    $r = As-Admin ($itx.Replace(',reason) ', ',order_id,reason) ') + "VALUES (1,1,'RESTOCK',2,10,12,203,'with an order');")
    Check 'a manual stock type cannot name an order' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))

    # -----------------------------------------------------------------------------------------
    Section 'Per-table privileges (db/admin/restrict-app-privileges.sql)'
    # -----------------------------------------------------------------------------------------
    $script = [System.IO.File]::ReadAllText($restrict)
    $adapted = $script.Replace("'opsflow_app'", "'$APP'").Replace('opsflow.', "$DB.")
    $leftover = [regex]::Matches(($adapted -replace '(?m)^--.*$', ''), 'opsflow(?!_verify)').Count
    Check 'privilege script adapts to the temporary names without touching real accounts' ($leftover -eq 0) "leftover references: $leftover"
    $adaptedFile = Join-Path $tmp 'restrict-adapted.sql'
    [System.IO.File]::WriteAllText($adaptedFile, $adapted, [System.Text.UTF8Encoding]::new($false))
    $r = Run-File $ADMIN $adminPw $adaptedFile
    Check 'privilege script runs' (Allowed $r) (Short-Err $r.Err)

    $null = As-Admin ("CALL seed(5); INSERT INTO roles(id,code,name) VALUES (1,'R','Role'); INSERT INTO user_roles(user_id,role_id) VALUES (1,1); " +
        "INSERT INTO orders(id,order_no,user_id) VALUES (100,'ORD-p',1); " +
        "INSERT INTO order_items(order_id,product_id,product_sku,product_name,unit_price,quantity,subtotal) VALUES (100,1,'P1','Widget',10.00,1,10.00); " +
        "INSERT INTO inventory_transactions(inventory_id,type,quantity,available_before,available_after,reason,operator_id) VALUES (1,'RESTOCK',1,5,6,'initial',1); " +
        "INSERT INTO tickets(id,ticket_no,created_by,type,source,title,description) VALUES (100,'TKTPRIV1',1,'OTHER','MANUAL','t','d'); " +
        "INSERT INTO ticket_assignment_history(ticket_id,from_user_id,to_user_id,changed_by) VALUES (100,NULL,2,1); " +
        "INSERT INTO ticket_inventory(ticket_id,inventory_id) VALUES (100,1); " +
        "INSERT INTO product_listing_events(product_id,inventory_id,action,name,price,operator_id) VALUES (1,3,'LISTED','Widget',10.00,1); " +        "INSERT INTO operation_logs(category,action,target_type,target_id) VALUES ('TICKET','A','ORDER',100); " +
        "INSERT INTO email_verification_codes(email,purpose,code_hash,expires_at) VALUES ('a@example.com','REGISTER','h',NOW(3));")

    Check 'app: update mutable tables (users, roles, products, inventory, orders, tickets, email_verification_codes), including the soft delete of an item' (Allowed (As-App "UPDATE inventory SET available_quantity=0, deleted_at=NOW(3), deleted_by=1 WHERE id=2; UPDATE inventory SET deleted_at=NULL, deleted_by=NULL WHERE id=2; UPDATE email_verification_codes SET attempts=attempts+1 WHERE email='a@example.com'; UPDATE users SET failed_login_count=1, last_failed_login_at=NOW(3), locked_until=NOW(3), password_changed_at=NOW(3) WHERE id=1; UPDATE users SET status='ACTIVE' WHERE id=1; UPDATE roles SET name='R2' WHERE id=1; UPDATE products SET name='W2' WHERE id=1; UPDATE inventory SET sold_quantity=sold_quantity WHERE id=1; UPDATE orders SET remark='x' WHERE id=100; UPDATE tickets SET status='IN_PROGRESS' WHERE id=100;"))
    Check 'app: insert into every table it writes' (Allowed (As-App ("INSERT INTO users(username,password_hash,email) VALUES ('n1','x','n1@example.com'); INSERT INTO ticket_inventory(ticket_id,inventory_id) VALUES (100,2); INSERT INTO inventory(id,sku,name,created_by) VALUES (30,'NEWSKU30','New item',1); INSERT INTO products(inventory_id,name,price,status,listed_at,listed_by) VALUES (30,'New item',1.00,'LISTED',NOW(3),1); INSERT INTO product_listing_events(product_id,inventory_id,action,name,price,operator_id) VALUES (1,3,'DELISTED','Widget',10.00,1); INSERT INTO inventory_item_events(inventory_id,action,sku,name,quantity,operator_id) VALUES (30,'CREATED','NEWSKU30','New item',0,1); INSERT INTO order_attempts(order_no,user_id,product_id,product_name,quantity,result,stock_deducted) VALUES ('ATT-P',1,1,'Widget',0,'BUSY',0); " +
        "INSERT INTO ticket_assignment_history(ticket_id,from_user_id,to_user_id,changed_by) VALUES (100,2,3,1); INSERT INTO operation_logs(category,action,target_type,target_id) VALUES ('TICKET','B','ORDER',100); " +
        "INSERT INTO inventory_transactions(inventory_id,type,quantity,available_before,available_after,reason,operator_id) VALUES (1,'RESTOCK',1,6,7,'delivery',1); INSERT INTO user_roles(user_id,role_id) VALUES (2,1); INSERT INTO email_verification_codes(email,purpose,code_hash,expires_at) VALUES ('b@example.com','RESET_PASSWORD','h',NOW(3));")))
    Check 'app: SELECT ... FOR UPDATE on inventory' (Allowed (As-App "START TRANSACTION; SELECT id FROM inventory WHERE id=1 FOR UPDATE; COMMIT;"))
    $refusals = @(
        @('UPDATE inventory_transactions', "UPDATE inventory_transactions SET reason='x';"),
        @('DELETE inventory_transactions', "DELETE FROM inventory_transactions;"),
        @('UPDATE ticket_assignment_history', "UPDATE ticket_assignment_history SET reason='x';"),
        @('DELETE ticket_assignment_history', "DELETE FROM ticket_assignment_history;"),
        @('UPDATE ticket_inventory', "UPDATE ticket_inventory SET inventory_id=2;"),
        @('DELETE ticket_inventory', "DELETE FROM ticket_inventory;"),
        @('UPDATE product_listing_events', "UPDATE product_listing_events SET name='x';"),
        @('DELETE product_listing_events', "DELETE FROM product_listing_events;"),
        @('UPDATE inventory_item_events', "UPDATE inventory_item_events SET name='x';"),
        @('DELETE inventory_item_events', "DELETE FROM inventory_item_events;"),        @('UPDATE operation_logs', "UPDATE operation_logs SET remark='x';"),
        @('DELETE operation_logs', "DELETE FROM operation_logs;"),
        @('UPDATE order_attempts', "UPDATE order_attempts SET result='BUSY';"),
        @('DELETE order_attempts', "DELETE FROM order_attempts;"),
        @('UPDATE order_items (frozen)', "UPDATE order_items SET quantity=9;"),
        @('DELETE order_items', "DELETE FROM order_items;"),
        @('UPDATE user_roles', "UPDATE user_roles SET role_id=1;"),
        @('DELETE user_roles (until granted)', "DELETE FROM user_roles;"),
        @('DELETE orders', "DELETE FROM orders;"),
        @('DELETE users', "DELETE FROM users WHERE id=3;"),
        @('DELETE products', "DELETE FROM products;"),
        @('DELETE inventory', "DELETE FROM inventory;"),
        @('CREATE TABLE', "CREATE TABLE zzz (id INT);"),
        @('DROP TABLE', "DROP TABLE ticket_inventory;")
    )
    foreach ($t in $refusals) { Check ('app is refused: ' + $t[0]) (Denied (As-App $t[1])) }
    $g = Allowed (As-Admin "GRANT DELETE ON $DB.user_roles TO '$APP'@'localhost';")
    $d = Allowed (As-App "DELETE FROM user_roles WHERE user_id=2;")
    Check 'app can delete user_roles once DELETE is granted on that table only' ($g -and $d)
    $null = As-Admin "REVOKE DELETE ON $DB.user_roles FROM '$APP'@'localhost';"
    $null = As-Admin "INSERT INTO email_verification_codes(email,purpose,code_hash,expires_at) VALUES ('old@example.com','REGISTER','h',NOW(3) - INTERVAL 1 MINUTE), ('new@example.com','REGISTER','h',NOW(3) + INTERVAL 10 MINUTE);"
    $dc = Allowed (As-App "DELETE FROM email_verification_codes WHERE expires_at < NOW(3);")
    $left = Scalar "SELECT CONCAT('RESULT=', GROUP_CONCAT(email ORDER BY id)) FROM email_verification_codes WHERE email IN ('old@example.com','new@example.com');"
    Check 'app can delete email codes that have expired, and a valid code stays' ($dc -and ($left -eq 'new@example.com')) "$dc / $left"

    # -----------------------------------------------------------------------------------------
    Section "Concurrency ($Rounds rounds per race; sessions run as the restricted application account)"
    # -----------------------------------------------------------------------------------------
    $items2 = '[{"product_id":1,"qty":3},{"product_id":2,"qty":2}]'
    $item1 = '[{"product_id":1,"qty":1}]'
    $item2q = '[{"product_id":1,"qty":2}]'
    $item3q = '[{"product_id":1,"qty":3}]'
    $item4q = '[{"product_id":1,"qty":4}]'
    $pairItems = '[{"product_id":1,"qty":1},{"product_id":2,"qty":1}]'

    # S2. Repeated submission.
    $bad = 0
    for ($r = 1; $r -le $Rounds; $r++) {
        $null = As-Admin "CALL seed(10);"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(1,'ORD-s2-$r-a','K1','$item2q',@r,@o); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(1,'ORD-s2-$r-b','K1','$item2q',@r,@o); SELECT CONCAT('RESULT=',@r);" })
        $a = Result $res[0]; $b = Result $res[1]
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders WHERE idempotency_key='K1'), '|', (SELECT CONCAT(available_quantity,',',sold_quantity) FROM inventory WHERE id=3), '|', (SELECT COUNT(*) FROM inventory_transactions WHERE type='SALE'), '|', (SELECT COUNT(*) FROM order_attempts));"
        $pair = @($a, $b) | Sort-Object
        if (($st -ne '1|8,2|1|1') -or (($pair -join ',') -ne 'COMPLETED,DUPLICATE')) { $bad++ }
    }
    Check 'repeated submission: one order, stock deducted once' ($bad -eq 0) "$Rounds rounds, bad $bad"
    $null = As-Admin "CALL seed(10);"
    $res = Run-Parallel @(
        @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(1,'ORD-s2-x1','K2','$item1',@r,@o); SELECT CONCAT('RESULT=',@r);" },
        @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(2,'ORD-s2-x2','K2','$item1',@r,@o); SELECT CONCAT('RESULT=',@r);" })
    Check 'the same key from two different users creates two orders' (((Result $res[0]) -eq 'COMPLETED') -and ((Result $res[1]) -eq 'COMPLETED'))

    # S3. Multi-item order with one item short.
    $itemsShort = '[{"product_id":1,"qty":2},{"product_id":2,"qty":3},{"product_id":3,"qty":1}]'
    $null = As-Admin "CALL seed(10); UPDATE inventory SET available_quantity=1 WHERE id=2;"
    $res = Result (As-App "CALL create_order(1,'ORD-s3',NULL,'$itemsShort',@r,@o); SELECT CONCAT('RESULT=',@r);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders), '|', (SELECT COUNT(*) FROM order_items), '|', (SELECT COUNT(*) FROM inventory_transactions), '|', (SELECT GROUP_CONCAT(available_quantity ORDER BY id) FROM inventory), '|', (SELECT SUM(sold_quantity) FROM inventory));"
    Check 'one item short: whole order rolled back, no order, no stock change, no transaction' (($res -eq 'OUT_OF_STOCK') -and ($st -eq '0|0|0|10,1,10|0')) "$res / $st"
    $at = Scalar "SELECT CONCAT('RESULT=', COUNT(*), '|', GROUP_CONCAT(DISTINCT result), '|', SUM(stock_deducted), '|', COUNT(order_id)) FROM order_attempts WHERE order_no='ORD-s3';"
    Check 'the refused order is recorded as an attempt, one row for each product line, with the reason, no order, and nothing deducted' ($at -eq '3|OUT_OF_STOCK|0|0') $at
    $nm = Scalar "SELECT CONCAT('RESULT=', COUNT(*), '|', SUM(a.product_name = p.name)) FROM order_attempts a JOIN products p ON p.id = a.product_id WHERE a.order_no='ORD-s3';"
    Check 'a recorded attempt carries the name the product had at the time' ($nm -eq '3|3') $nm

    $bad = 0
    for ($r = 1; $r -le $Rounds; $r++) {
        $null = As-Admin "CALL seed(10); UPDATE inventory SET available_quantity=1 WHERE id=2;"
        $specs = @()
        for ($i = 1; $i -le 5; $i++) {
            $u = (($i - 1) % 3) + 1
            $specs += @{ User = $APP; Pass = $appPw; Sql = "CALL create_order($u,'ORD-s3-$r-$i',NULL,'$pairItems',@r,@o); SELECT CONCAT('RESULT=',@r);" }
        }
        $res = Run-Parallel $specs
        $created = @($res | Where-Object { (Result $_) -eq 'COMPLETED' }).Count
        $oos = @($res | Where-Object { (Result $_) -eq 'OUT_OF_STOCK' }).Count
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders), '|', (SELECT CONCAT(available_quantity,',',sold_quantity) FROM inventory WHERE id=2), '|', (SELECT CONCAT(available_quantity,',',sold_quantity) FROM inventory WHERE id=3), '|', (SELECT COUNT(*) FROM inventory_transactions));"
        if (($created -ne 1) -or ($oos -ne 4) -or ($st -ne '1|0,1|9,1|2')) { $bad++ }
    }
    Check 'five buyers race for the last unit: one wins, four see out of stock, nothing half-done' ($bad -eq 0) "$Rounds rounds, bad $bad"

    # S4. Many sales on one stock row: no oversell, and an unbroken snapshot chain.
    $chainSql = "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM (SELECT available_before ab, LAG(available_after) OVER (ORDER BY id) pa FROM inventory_transactions WHERE inventory_id=3) t WHERE pa IS NOT NULL AND ab <> pa));"
    $bad = 0
    $rounds4 = [Math]::Max(3, [int]($Rounds / 2))
    for ($r = 1; $r -le $rounds4; $r++) {
        $null = As-Admin "CALL seed(20);"
        $specs = @()
        for ($i = 1; $i -le 10; $i++) {
            $u = (($i - 1) % 3) + 1
            $specs += @{ User = $APP; Pass = $appPw; Sql = "CALL create_order($u,'ORD-s4-$r-$i',NULL,'$item3q',@r,@o); SELECT CONCAT('RESULT=',@r);" }
        }
        $res = Run-Parallel $specs
        $created = @($res | Where-Object { (Result $_) -eq 'COMPLETED' }).Count
        $st = Scalar "SELECT CONCAT('RESULT=', available_quantity, ',', sold_quantity, '|', (SELECT COUNT(*) FROM inventory_transactions WHERE inventory_id=3 AND type='SALE'), '|', (SELECT available_before FROM inventory_transactions WHERE inventory_id=3 ORDER BY id LIMIT 1), '|', (SELECT available_after FROM inventory_transactions WHERE inventory_id=3 ORDER BY id DESC LIMIT 1)) FROM inventory WHERE id=3;"
        $broken = Scalar $chainSql
        if (($created -ne 6) -or ($st -ne '2,18|6|20|2') -or ($broken -ne '0')) { $bad++ }
    }
    Check 'ten buyers, stock for six: no oversell, snapshot chain unbroken and ends at the real stock' ($bad -eq 0) "$rounds4 rounds, bad $bad"

    # Negative control: the same race with an unlocked read must break the chain.
    $null = As-Admin "CALL seed(20);"
    $ids = Scalar "INSERT INTO orders(order_no,user_id) VALUES ('C1',1),('C2',1),('C3',1),('C4',1),('C5',1),('C6',1); SELECT CONCAT('RESULT=', GROUP_CONCAT(id)) FROM orders;"
    $specs = @()
    foreach ($oid in ($ids -split ',')) { $specs += @{ User = $APP; Pass = $appPw; Sql = "CALL sale_unsafe($oid,3,3);" } }
    $null = Run-Parallel $specs -NoRetry
    $broken = [int](Scalar $chainSql)
    Check 'negative control: reading stock without a lock DOES break the snapshot chain' ($broken -gt 0) "broken links: $broken"

    $rounds5 = [Math]::Max(3, [int]($Rounds / 3))

    # S5c. Listing, delisting, and ordering under a product. Written for v1.7 and not run yet.
    $itemChanged = '[{"product_id":1,"qty":1,"price":9.99}]'
    $itemSame = '[{"product_id":1,"qty":1,"price":10.00,"name":"Widget"}]'
    $itemZero = '[{"product_id":1,"qty":0}]'
    $itemHuge = '[{"product_id":1,"qty":10000}]'

    $null = As-Admin "CALL seed(10); INSERT INTO tickets(ticket_no,created_by,type,source,title,description,status) VALUES ('TKTRES01',1,'OTHER','MANUAL','r','d','RESOLVED'),('TKTCLS01',1,'OTHER','MANUAL','c','d','CLOSED'),('TKTOPN01',1,'OTHER','MANUAL','o','d','OPEN'),('TKTCAN01',1,'OTHER','MANUAL','x','d','CANCELLED');"
    $f1 = Result (As-App "CALL create_ticket('FOLLOW01',1,'[3]','TKTRES01',@r); SELECT CONCAT('RESULT=',@r);")
    $f2 = Result (As-App "CALL create_ticket('FOLLOW02',1,'[]','TKTCLS01',@r); SELECT CONCAT('RESULT=',@r);")
    $f3 = Result (As-App "CALL create_ticket('FOLLOW03',1,'[]','TKTOPN01',@r); SELECT CONCAT('RESULT=',@r);")
    $f4 = Result (As-App "CALL create_ticket('FOLLOW04',1,'[]','TKTCAN01',@r); SELECT CONCAT('RESULT=',@r);")
    $f5 = Result (As-App "CALL create_ticket('FOLLOW05',1,'[3]','NOSUCH01',@r); SELECT CONCAT('RESULT=',@r);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT GROUP_CONCAT(CONCAT(f.ticket_no,'>',p.ticket_no) ORDER BY f.id) FROM tickets f JOIN tickets p ON p.id=f.follows_ticket_id), '|', (SELECT COUNT(*) FROM tickets WHERE ticket_no LIKE 'FOLLOW%'), '|', (SELECT COUNT(*) FROM ticket_inventory));"
    Check 'a ticket can be opened as a follow-up by the number of a RESOLVED or CLOSED ticket; an unknown number or an unfinished or cancelled ticket is refused with nothing written' (($f1 -eq 'CREATED') -and ($f2 -eq 'CREATED') -and ($f3 -eq 'FOLLOW_NOT_ALLOWED') -and ($f4 -eq 'FOLLOW_NOT_ALLOWED') -and ($f5 -eq 'FOLLOW_NOT_FOUND') -and ($st -eq 'FOLLOW01>TKTRES01,FOLLOW02>TKTCLS01|2|1')) "$f1 / $f2 / $f3 / $f4 / $f5 / $st"

    $bad = 0
    for ($r = 1; $r -le $rounds5; $r++) {
        $null = As-Admin "CALL seed(10); INSERT INTO inventory(id,sku,name,available_quantity,created_by) VALUES (10,'EXTRA001','Extra item',5,1);"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL list_product(10,7.00,NULL,1,@r); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL list_product(10,8.00,NULL,2,@r); SELECT CONCAT('RESULT=',@r);" })
        $pair = @((Result $res[0]), (Result $res[1])) | Sort-Object
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM products WHERE inventory_id=10), '|', (SELECT COUNT(*) FROM product_listing_events WHERE inventory_id=10 AND action='LISTED'), '|', (SELECT status FROM products WHERE inventory_id=10));"
        if ((($pair -join ',') -ne 'ALREADY_LISTED,LISTED') -or ($st -ne '1|1|LISTED')) { $bad++ }
    }
    Check 'two managers list the same item at once: one product, one listing event' ($bad -eq 0) "$rounds5 rounds, bad $bad"

    $null = As-Admin "CALL seed(10); INSERT INTO inventory(id,sku,name,available_quantity,created_by) VALUES (10,'EXTRA001','Extra item',5,1);"
    $a = Result (As-App "CALL list_product(10,7.00,NULL,1,@r); SELECT CONCAT('RESULT=',@r);")
    $prodId = Scalar "SELECT CONCAT('RESULT=', id) FROM products WHERE inventory_id=10;"
    $b = Result (As-App "CALL delist_product($prodId,1,3,@r,@n); SELECT CONCAT('RESULT=',@r);")
    $c = Result (As-App "CALL list_product(10,9.50,'Renamed item',2,@r); SELECT CONCAT('RESULT=',@r);")
    $d = Result (As-App "CALL list_product(10,9.50,NULL,2,@r); SELECT CONCAT('RESULT=',@r);")
    $e = Result (As-App "CALL list_product(9999,1.00,NULL,2,@r); SELECT CONCAT('RESULT=',@r);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM products WHERE inventory_id=10), '|', (SELECT id FROM products WHERE inventory_id=10), '|', (SELECT CONCAT(status,',',price,',',name) FROM products WHERE inventory_id=10), '|', (SELECT GROUP_CONCAT(action ORDER BY id) FROM product_listing_events WHERE inventory_id=10));"
    Check 'listing again after a delisting keeps the product id, takes the new price and name, and records every step' (($a -eq 'LISTED') -and ($b -eq 'DELISTED') -and ($c -eq 'LISTED') -and ($d -eq 'ALREADY_LISTED') -and ($e -eq 'NOT_FOUND') -and ($st -eq "1|$prodId|LISTED,9.50,Renamed item|LISTED,DELISTED,LISTED")) "$a / $b / $c / $d / $e / $st"

    $null = As-Admin "CALL seed(10); INSERT INTO inventory(id,sku,name,available_quantity,created_by) VALUES (10,'EXTRA001','Extra item',0,1);"
    $n1 = Result (As-App "CALL list_product(10,7.00,NULL,1,@r); SELECT CONCAT('RESULT=',@r);")
    $n0 = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM products WHERE inventory_id=10), '|', (SELECT COUNT(*) FROM product_listing_events WHERE inventory_id=10));"
    $null = As-Admin "UPDATE inventory SET available_quantity=5 WHERE id=10;"
    $n2 = Result (As-App "CALL list_product(10,7.00,NULL,1,@r); SELECT CONCAT('RESULT=',@r);")
    $prodN = Scalar "SELECT CONCAT('RESULT=', id) FROM products WHERE inventory_id=10;"
    $null = As-App "CALL delist_product($prodN,1,3,@r,@n);"
    $null = As-Admin "UPDATE inventory SET available_quantity=0 WHERE id=10;"
    $n3 = Result (As-App "CALL list_product(10,7.00,NULL,1,@r); SELECT CONCAT('RESULT=',@r);")
    $n4 = Scalar "SELECT CONCAT('RESULT=', (SELECT status FROM products WHERE inventory_id=10), '|', (SELECT GROUP_CONCAT(action ORDER BY id) FROM product_listing_events WHERE inventory_id=10));"
    Check 'an item with no stock cannot be listed, the first time or again after a delisting, and nothing is written; with stock it can' (($n1 -eq 'NO_STOCK') -and ($n0 -eq '0|0') -and ($n2 -eq 'LISTED') -and ($n3 -eq 'NO_STOCK') -and ($n4 -eq 'UNLISTED|LISTED,DELISTED')) "$n1 / $n0 / $n2 / $n3 / $n4"

    $null = As-Admin "CALL seed(10);"
    $o1 = Result (As-App "CALL create_order(1,'ORD-pc',NULL,'$itemChanged',@r,@o); SELECT CONCAT('RESULT=',@r);")
    $o2 = Result (As-App "CALL create_order(1,'ORD-ps',NULL,'$itemSame',@r,@o); SELECT CONCAT('RESULT=',@r);")
    $o3 = Result (As-App "CALL create_order(1,'ORD-q0',NULL,'$itemZero',@r,@o); SELECT CONCAT('RESULT=',@r);")
    $o4 = Result (As-App "CALL create_order(1,'ORD-qh',NULL,'$itemHuge',@r,@o); SELECT CONCAT('RESULT=',@r);")
    $null = As-App "CALL delist_product(1,1,3,@r,@n);"
    $o5 = Result (As-App "CALL create_order(1,'ORD-dl',NULL,'$item1',@r,@o); SELECT CONCAT('RESULT=',@r);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders), '|', (SELECT CONCAT(available_quantity,',',sold_quantity) FROM inventory WHERE id=3));"
    $at = Scalar "SELECT CONCAT('RESULT=', GROUP_CONCAT(CONCAT(order_no,':',result,':',stock_deducted,':',IF(order_id IS NULL,'-','o')) ORDER BY id)) FROM order_attempts;"
    Check 'every attempt is recorded with its order number, result, and stock deducted: the completed one has its order, the refused ones have none' ($at -eq 'ORD-pc:PRODUCT_CHANGED:0:-,ORD-ps:COMPLETED:1:o,ORD-q0:INVALID_QUANTITY:0:-,ORD-qh:INVALID_QUANTITY:0:-,ORD-dl:PRODUCT_DELISTED:0:-') $at
    Check 'an order is refused when the price changed, the product is delisted, or the quantity is not valid, and it writes nothing' (($o1 -eq 'PRODUCT_CHANGED') -and ($o2 -eq 'COMPLETED') -and ($o3 -eq 'INVALID_QUANTITY') -and ($o4 -eq 'INVALID_QUANTITY') -and ($o5 -eq 'PRODUCT_DELISTED') -and ($st -eq '1|9,1')) "$o1 / $o2 / $o3 / $o4 / $o5 / $st"

    $bad = 0
    for ($r = 1; $r -le $rounds5; $r++) {
        $null = As-Admin "CALL seed(20);"
        $specs = @()
        for ($i = 1; $i -le 5; $i++) {
            $u = (($i - 1) % 3) + 1
            $specs += @{ User = $APP; Pass = $appPw; Sql = "CALL create_order($u,'ORD-pd-$r-$i',NULL,'$item1',@r,@o); SELECT CONCAT('RESULT=',@r);" }
        }
        $specs += @{ User = $APP; Pass = $appPw; Sql = "CALL delist_product(1,1,3,@r,@n); SELECT CONCAT('RESULT=',@r);" }
        $res = Run-Parallel $specs
        $made = @($res[0..4] | Where-Object { (Result $_) -eq 'COMPLETED' }).Count
        $refused = @($res[0..4] | Where-Object { (Result $_) -eq 'PRODUCT_DELISTED' }).Count
        $dl = Result $res[5]
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders), '|', (SELECT sold_quantity FROM inventory WHERE id=3), '|', (SELECT status FROM products WHERE id=1), '|', (SELECT COUNT(*) FROM orders o JOIN products p ON p.id=1 WHERE o.created_at > p.delisted_at), '|', (SELECT COUNT(*) FROM order_attempts), '|', (SELECT COUNT(*) FROM order_attempts WHERE result='COMPLETED'));"
        if (($dl -ne 'DELISTED') -or (($made + $refused) -ne 5) -or ($st -ne "$made|$made|UNLISTED|0|5|$made")) { $bad++ }
    }
    Check 'five orders racing a delisting: each is completed or refused, and none is created after the delisting' ($bad -eq 0) "$rounds5 rounds, bad $bad"

    $ticketSql = "INSERT INTO tickets(id,ticket_no,created_by,type,source,title,description,status,assignee_id) VALUES (1,'TKTDEL01',1,'INVENTORY_EXCEPTION','MANUAL','open','d','OPEN',NULL),(2,'TKTDEL02',1,'INVENTORY_EXCEPTION','MANUAL','in progress','d','IN_PROGRESS',2),(3,'TKTDEL03',1,'INVENTORY_EXCEPTION','MANUAL','resolved','d','RESOLVED',2),(4,'TKTDEL04',1,'INVENTORY_EXCEPTION','MANUAL','other product','d','OPEN',NULL); INSERT INTO ticket_inventory(ticket_id,inventory_id) VALUES (1,3),(2,3),(3,3),(4,2);"
    $null = As-Admin "CALL seed(10); UPDATE users SET is_system=1 WHERE id=3; $ticketSql"
    $x = Result (As-App "CALL delist_product(1,1,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT GROUP_CONCAT(CONCAT(id,':',status) ORDER BY id) FROM tickets), '|', (SELECT COUNT(*) FROM tickets WHERE cancel_reason='PRODUCT_DELISTED:1' AND cancelled_by=3 AND cancelled_at IS NOT NULL));"
    Check 'delisting cancels the open and in-progress tickets of the product and leaves the others alone' (($x -eq 'DELISTED|2') -and ($st -eq '1:CANCELLED,2:CANCELLED,3:RESOLVED,4:OPEN|2')) "$x / $st"

    $bad = 0
    for ($r = 1; $r -le $rounds5; $r++) {
        $null = As-Admin "CALL seed(10); UPDATE users SET is_system=1 WHERE id=3; $ticketSql"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL delist_product(1,1,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL delist_product(1,2,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);" })
        $pair = @((Result $res[0]), (Result $res[1])) | Sort-Object
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM product_listing_events WHERE action='DELISTED'), '|', (SELECT COUNT(*) FROM tickets WHERE status='CANCELLED'));"
        if ((($pair -join ',') -ne 'ALREADY_DELISTED|0,DELISTED|2') -or ($st -ne '1|2')) { $bad++ }
    }
    Check 'two delistings at once: one wins, one delisting event, each ticket cancelled once' ($bad -eq 0) "$rounds5 rounds, bad $bad"

    # S5d. Adding and deleting warehouse items, and opening a ticket on items. Written for v1.7 and not run yet.
    $null = As-Admin "CALL seed(10);"
    $c1 = Result (As-App "CALL create_item('NEWITEM1','Brand new item',25,1,@r); SELECT CONCAT('RESULT=',@r);")
    $c2 = Result (As-App "CALL create_item('NEWITEM2','Empty item',0,1,@r); SELECT CONCAT('RESULT=',@r);")
    $c3 = Result (As-App "CALL create_item('NEWITEM3','brand NEW item',5,1,@r); SELECT CONCAT('RESULT=',@r);")
    $c4 = Result (As-App "CALL create_item('NEWITEM1','Another name',5,1,@r); SELECT CONCAT('RESULT=',@r);")
    $c5 = Result (As-App "CALL create_item('NEWITEM5','Negative item',-1,1,@r); SELECT CONCAT('RESULT=',@r);")
    $c6 = Result (As-App "CALL create_item('NEWITEM6','Huge item',1000000,1,@r); SELECT CONCAT('RESULT=',@r);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM inventory WHERE sku LIKE 'NEWITEM%'), '|', (SELECT available_quantity FROM inventory WHERE sku='NEWITEM1'), '|', (SELECT COUNT(*) FROM inventory_transactions t JOIN inventory i ON i.id=t.inventory_id WHERE i.sku LIKE 'NEWITEM%' AND t.type='RESTOCK' AND t.reason='initial stock' AND t.available_before=0 AND t.available_after=25), '|', (SELECT GROUP_CONCAT(CONCAT(action,':',quantity) ORDER BY id) FROM inventory_item_events WHERE sku LIKE 'NEWITEM%'));"
    Check 'adding an item: the starting stock is recorded as a restock, a repeated name or SKU and a bad quantity are refused, and each addition has an event' (($c1 -eq 'CREATED') -and ($c2 -eq 'CREATED') -and ($c3 -eq 'NAME_EXISTS') -and ($c4 -eq 'SKU_EXISTS') -and ($c5 -eq 'INVALID_QUANTITY') -and ($c6 -eq 'INVALID_QUANTITY') -and ($st -eq '2|25|1|CREATED:25,CREATED:0')) "$c1 / $c2 / $c3 / $c4 / $c5 / $c6 / $st"

    $bad = 0
    for ($r = 1; $r -le $rounds5; $r++) {
        $null = As-Admin "CALL seed(10);"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_item('RACEAAA1','Same name',4,1,@r); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_item('RACEBBB1','Same name',6,2,@r); SELECT CONCAT('RESULT=',@r);" })
        $pair = @((Result $res[0]), (Result $res[1])) | Sort-Object
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM inventory WHERE name='Same name'), '|', (SELECT COUNT(*) FROM inventory_item_events WHERE name='Same name'), '|', (SELECT COUNT(*) FROM inventory_transactions t JOIN inventory i ON i.id=t.inventory_id WHERE i.name='Same name'));"
        if ((($pair -join ',') -ne 'CREATED,NAME_EXISTS') -or ($st -ne '1|1|1')) { $bad++ }
    }
    Check 'two managers add an item with the same name at once: one item, one event, one starting restock' ($bad -eq 0) "$rounds5 rounds, bad $bad"

    $null = As-Admin "CALL seed(10); UPDATE users SET is_system=1 WHERE id=3; $ticketSql"
    $d1 = Result (As-App "CALL delete_item(3,1,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);")
    $d2 = Result (As-App "CALL delete_item(3,1,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);")
    $d3 = Result (As-App "CALL delete_item(9999,1,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);")
    $d4 = Result (As-App "CALL list_product(3,5.00,NULL,1,@r); SELECT CONCAT('RESULT=',@r);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT CONCAT(available_quantity,',',IF(deleted_at IS NULL,'live','deleted')) FROM inventory WHERE id=3), '|', (SELECT status FROM products WHERE inventory_id=3), '|', (SELECT GROUP_CONCAT(CONCAT(id,':',status) ORDER BY id) FROM tickets), '|', (SELECT COUNT(*) FROM tickets WHERE cancel_reason='ITEM_DELETED:3'), '|', (SELECT GROUP_CONCAT(action ORDER BY id) FROM product_listing_events WHERE inventory_id=3), '|', (SELECT GROUP_CONCAT(CONCAT(action,':',quantity) ORDER BY id) FROM inventory_item_events WHERE inventory_id=3), '|', (SELECT COUNT(*) FROM inventory_transactions WHERE inventory_id=3));"
    $reuse = Result (As-App "CALL create_item('REUSE001','Widget item',0,1,@r); SELECT CONCAT('RESULT=',@r);")
    Check 'deleting an item delists its product, cancels the open tickets linked to it, leaves its stock alone, records the event, and frees its name' (($d1 -eq 'DELETED|2') -and ($d2 -eq 'ALREADY_DELETED|0') -and ($d3 -eq 'NOT_FOUND|0') -and ($d4 -eq 'NOT_FOUND') -and ($reuse -eq 'CREATED') -and ($st -eq '10,deleted|UNLISTED|1:CANCELLED,2:CANCELLED,3:RESOLVED,4:OPEN|2|DELISTED|DELETED:10|0')) "$d1 / $d2 / $d3 / $d4 / $reuse / $st"

    $bad = 0
    for ($r = 1; $r -le $rounds5; $r++) {
        $null = As-Admin "CALL seed(10); UPDATE users SET is_system=1 WHERE id=3; $ticketSql"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL delete_item(3,1,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL delete_item(3,2,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);" })
        $pair = @((Result $res[0]), (Result $res[1])) | Sort-Object
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM inventory_item_events WHERE action='DELETED'), '|', (SELECT COUNT(*) FROM inventory_transactions), '|', (SELECT COUNT(*) FROM tickets WHERE status='CANCELLED'), '|', (SELECT COUNT(*) FROM product_listing_events WHERE action='DELISTED'));"
        if ((($pair -join ',') -ne 'ALREADY_DELETED|0,DELETED|2') -or ($st -ne '1|0|2|1')) { $bad++ }
    }
    Check 'two deletions of the same item at once: one wins, one event, each ticket cancelled once' ($bad -eq 0) "$rounds5 rounds, bad $bad"

    $bad = 0
    for ($r = 1; $r -le $rounds5; $r++) {
        $null = As-Admin "CALL seed(10);"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(1,'ORD-dr-$r',NULL,'$item1',@r,@o); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL delete_item(3,1,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);" })
        $pair = (Result $res[0]) + ',' + (Result $res[1])
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders), '|', (SELECT CONCAT(available_quantity,',',sold_quantity,',',IF(deleted_at IS NULL,'live','deleted')) FROM inventory WHERE id=3), '|', (SELECT status FROM products WHERE id=1));"
        $okA = ($pair -eq 'COMPLETED,DELETED|0') -and ($st -eq '1|9,1,deleted|UNLISTED')
        $okB = ($pair -eq 'PRODUCT_DELISTED,DELETED|0') -and ($st -eq '0|10,0,deleted|UNLISTED')
        if (-not ($okA -or $okB)) { $bad++ }
    }
    Check 'an order racing the deletion of its item: either the order is completed and then the item is deleted, or the item is deleted and the order is refused' ($bad -eq 0) "$rounds5 rounds, bad $bad"

    $bad = 0
    for ($r = 1; $r -le $rounds5; $r++) {
        $null = As-Admin "CALL seed(10); UPDATE users SET is_system=1 WHERE id=3;"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_ticket('TKTR$('{0:0000}' -f $r)',1,'[3]',NULL,@r); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL delete_item(3,2,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);" })
        $pair = (Result $res[0]) + ',' + (Result $res[1])
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM tickets), '|', (SELECT COUNT(*) FROM ticket_inventory), '|', (SELECT COUNT(*) FROM tickets WHERE status='OPEN'), '|', (SELECT COUNT(*) FROM tickets WHERE cancel_reason='ITEM_DELETED:3'));"
        $okA = ($pair -eq 'CREATED,DELETED|1') -and ($st -eq '1|1|0|1')
        $okB = ($pair -eq 'ITEM_GONE,DELETED|0') -and ($st -eq '0|0|0|0')
        if (-not ($okA -or $okB)) { $bad++ }
    }
    Check 'a ticket opened on an item racing its deletion: either the ticket is opened and then cancelled, or it is refused, and no open ticket stays linked to a deleted item' ($bad -eq 0) "$rounds5 rounds, bad $bad"

    $null = As-Admin "CALL seed(10); UPDATE inventory SET available_quantity=0, deleted_at=NOW(3), deleted_by=1 WHERE id=1;"
    $t1 = Result (As-App "CALL create_ticket('TKTN0001',1,'[]',NULL,@r); SELECT CONCAT('RESULT=',@r);")
    $t2 = Result (As-App "CALL create_ticket('TKTN0002',1,'[3,2]',NULL,@r); SELECT CONCAT('RESULT=',@r);")
    $t3 = Result (As-App "CALL create_ticket('TKTGONE1',1,'[2,1]',NULL,@r); SELECT CONCAT('RESULT=',@r);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT GROUP_CONCAT(ticket_no ORDER BY id) FROM tickets), '|', (SELECT COUNT(*) FROM ticket_inventory));"
    Check 'a ticket can be opened on no items or on several, and one deleted item refuses the whole ticket with nothing written' (($t1 -eq 'CREATED') -and ($t2 -eq 'CREATED') -and ($t3 -eq 'ITEM_GONE') -and ($st -eq 'TKTN0001,TKTN0002|2')) "$t1 / $t2 / $t3 / $st"

    $bad = 0
    for ($r = 1; $r -le $rounds5; $r++) {
        $null = As-Admin "CALL seed(10); INSERT INTO inventory(id,sku,name,available_quantity,created_by) VALUES (10,'EXTRA001','Extra item',5,1);"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL list_product(10,7.00,NULL,1,@r); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL delete_item(10,2,3,@r,@n); SELECT CONCAT('RESULT=',@r,'|',@n);" })
        $pair = (Result $res[0]) + ',' + (Result $res[1])
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT IF(deleted_at IS NULL,'live','deleted') FROM inventory WHERE id=10), '|', (SELECT COALESCE(MAX(status),'none') FROM products WHERE inventory_id=10));"
        $okA = ($pair -eq 'LISTED,DELETED|0') -and ($st -eq 'deleted|UNLISTED')
        $okB = ($pair -eq 'NOT_FOUND,DELETED|0') -and ($st -eq 'deleted|none')
        if (-not ($okA -or $okB)) { $bad++ }
    }
    Check 'listing an item racing its deletion: the item is never left deleted with a LISTED product' ($bad -eq 0) "$rounds5 rounds, bad $bad"
    # S6. Deadlocks and lock waits.
    $null = As-Admin "CALL seed(10);"
    $sa = "START TRANSACTION;`nINSERT INTO operation_logs(category,action,target_type,target_id) VALUES ('TICKET','A','T',1);`nSELECT id FROM inventory WHERE id=1 FOR UPDATE;`nDO SLEEP(1.5);`nSELECT id FROM inventory WHERE id=2 FOR UPDATE;`nCOMMIT;`nSELECT 'RESULT=COMMITTED';"
    $sb = "START TRANSACTION;`nINSERT INTO operation_logs(category,action,target_type,target_id) VALUES ('TICKET','B','T',1);`nSELECT id FROM inventory WHERE id=2 FOR UPDATE;`nDO SLEEP(1.5);`nSELECT id FROM inventory WHERE id=1 FOR UPDATE;`nCOMMIT;`nSELECT 'RESULT=COMMITTED';"
    $res = Run-Parallel @(@{ User = $APP; Pass = $appPw; Sql = $sa }, @{ User = $APP; Pass = $appPw; Sql = $sb }) -NoRetry
    $dead = @($res | Where-Object { $_.Err -match 'ERROR 1213' }).Count
    $done = @($res | Where-Object { (Result $_) -eq 'COMMITTED' }).Count
    $rows = Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM operation_logs;"
    Check 'locking rows in opposite orders: MySQL aborts exactly one session with a deadlock' (($dead -eq 1) -and ($done -eq 1)) "deadlocked $dead, committed $done"
    Check 'the aborted session left nothing behind (its earlier insert was rolled back too)' ($rows -eq '1') "log rows: $rows"
    if ($res[0].Err -match 'ERROR 1213') { $loser = $sa } else { $loser = $sb }
    $r = Run-Sql $APP $appPw $loser
    $rows = Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM operation_logs;"
    Check 'the aborted session succeeds when it is retried' ((Allowed $r) -and ($rows -eq '2')) "log rows: $rows"

    $bad = 0; $script:retries = 0
    $fwd = '[{"product_id":1,"qty":1},{"product_id":2,"qty":1},{"product_id":3,"qty":1}]'
    $rev = '[{"product_id":3,"qty":1},{"product_id":2,"qty":1},{"product_id":1,"qty":1}]'
    for ($r = 1; $r -le $Rounds; $r++) {
        $null = As-Admin "CALL seed(1000);"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(1,'ORD-s6-$r-a',NULL,'$fwd',@r,@o); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(2,'ORD-s6-$r-b',NULL,'$rev',@r,@o); SELECT CONCAT('RESULT=',@r);" })
        if (((Result $res[0]) -ne 'COMPLETED') -or ((Result $res[1]) -ne 'COMPLETED')) { $bad++ }
    }
    Check 'orders listing items in opposite order never deadlock (locks are taken by ascending inventory id)' (($bad -eq 0) -and ($script:retries -eq 0)) "$Rounds rounds, retries needed $($script:retries), failed $bad"

    # A lock wait timeout rolls back only the statement that timed out.
    $null = As-Admin "CALL seed(10);"
    $holder = "START TRANSACTION;`nSELECT id FROM inventory WHERE id=1 FOR UPDATE;`nDO SLEEP(7);`nCOMMIT;"
    $waiter = "SET SESSION innodb_lock_wait_timeout = 2;`nSTART TRANSACTION;`nINSERT INTO operation_logs(category,action,target_type,target_id) VALUES ('TICKET','W','T',1);`nUPDATE inventory SET sold_quantity = sold_quantity WHERE id=1;`nSELECT CONCAT('RESULT=after_timeout:', (SELECT COUNT(*) FROM operation_logs WHERE action='W'));`nROLLBACK;`nSELECT CONCAT('RESULT=after_rollback:', (SELECT COUNT(*) FROM operation_logs WHERE action='W'));"
    $h = Start-Sess -User $APP -Pass $appPw -Sql $holder
    Start-Sleep -Milliseconds 1500
    $w = Run-Sql $APP $appPw $waiter -Force
    $null = Wait-Sess $h
    $timedOut = $w.Err -match 'ERROR 1205'
    $afterTimeout = [regex]::Match($w.Out, 'after_timeout:(\d+)').Groups[1].Value
    $afterRollback = [regex]::Match($w.Out, 'after_rollback:(\d+)').Groups[1].Value
    Check 'a lock wait timeout (error 1205) happens' $timedOut
    Check 'after a lock wait timeout the earlier statements of the transaction are still pending (only the timed-out statement rolled back)' ($afterTimeout -eq '1') "rows visible after timeout: $afterTimeout"
    Check 'the application must roll back explicitly, which removes them' ($afterRollback -eq '0') "rows after rollback: $afterRollback"
}
finally {
    # -----------------------------------------------------------------------------------------
    # Teardown
    # -----------------------------------------------------------------------------------------
    if ((-not $Keep) -and (-not $Quick) -and $adminPw) {
        $null = Run-Sql $ADMIN $adminPw "DROP DATABASE IF EXISTS $DB;" -Database ''
        if ($rootPw) {
            $dropped = Run-Sql 'root' $rootPw "DROP USER IF EXISTS '$ADMIN'@'localhost', '$APP'@'localhost';" -Database ''
            Write-Output ''
            if ($dropped.Code -eq 0) { Write-Output 'Cleanup: temporary database and accounts dropped.' } else { Write-Output ('Cleanup: could not drop the accounts ' + (Short-Err $dropped.Err)) }
        }
    }
    if (-not $Keep) {
        [Environment]::SetEnvironmentVariable('OPSFLOW_VERIFY_ADMIN_PW', $null, 'User')
        [Environment]::SetEnvironmentVariable('OPSFLOW_VERIFY_APP_PW', $null, 'User')
    }
    $rootPw = $null; $adminPw = $null; $appPw = $null
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Output ''
Write-Output "RESULT: $($script:pass) passed, $($script:fail) failed"
if ($script:fail -gt 0) { exit 1 }
