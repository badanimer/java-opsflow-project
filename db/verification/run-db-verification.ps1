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
            while (((Result $results[$i]) -eq 'RETRYABLE') -and ($n -lt 2)) {
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
    Check '14 tables exist' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.tables WHERE table_schema='$DB' AND table_type='BASE TABLE';") -eq '14')
    Check 'ticket_comments has no updated_at (append-only)' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='ticket_comments' AND column_name='updated_at';") -eq '0')
    Check 'order_items has no updated_at (frozen)' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='order_items' AND column_name='updated_at';") -eq '0')
    Check 'inventory has no version column' ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='inventory' AND column_name='version';") -eq '0')
    $coll = Scalar "SELECT CONCAT('RESULT=', GROUP_CONCAT(CONCAT(table_name, '.', column_name, '=', collation_name) ORDER BY table_name, column_name)) FROM information_schema.columns WHERE table_schema='$DB' AND column_name IN ('order_no','ticket_no','idempotency_key');"
    Check 'order_no, ticket_no, idempotency_key use utf8mb4_0900_bin' ((($coll -split ',').Count -eq 3) -and (-not ($coll -match 'ai_ci'))) $coll

    $null = As-Admin "CALL seed(5);"
    $r = As-Admin "INSERT INTO orders(order_no,user_id,reserved_until) VALUES ('ORD-aaa',1,NOW(3)),('ORD-AAA',1,NOW(3));"
    Check 'order_no is case-sensitive (ORD-aaa and ORD-AAA both accepted)' (Allowed $r)
    $r = As-Admin "INSERT INTO orders(order_no,user_id,reserved_until) VALUES ('ORD-aaa',1,NOW(3));"
    Check 'identical order_no is still rejected' (Dup $r)
    $r = As-Admin "INSERT INTO orders(order_no,user_id,idempotency_key,reserved_until) VALUES ('ORD-k1',1,'Abc',NOW(3)),('ORD-k2',1,'abc',NOW(3));"
    Check 'idempotency keys Abc and abc are different for one user' (Allowed $r)
    $r = As-Admin "INSERT INTO orders(order_no,user_id,idempotency_key,reserved_until) VALUES ('ORD-k3',1,'abc',NOW(3));"
    Check 'the same key for the same user is rejected' (Dup $r)
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,priority,title,description) VALUES ('TKT-a',1,'OTHER','LOW','t','d'),('TKT-A',1,'OTHER','LOW','t','d');"
    Check 'ticket_no is case-sensitive (TKT-a and TKT-A both accepted)' (Allowed $r)
    $null = As-Admin "INSERT INTO ticket_comments(ticket_id,user_id,content) SELECT id,1,'orig' FROM tickets WHERE ticket_no='TKT-a';"
    $r = As-Admin "INSERT INTO ticket_comments(ticket_id,user_id,corrects_comment_id,content) SELECT t.id,1,c.id,'fix' FROM tickets t JOIN ticket_comments c ON c.ticket_id=t.id WHERE t.ticket_no='TKT-a' AND c.content='orig';"
    Check 'a correction comment can point at a comment of the same ticket' (Allowed $r)
    $r = As-Admin "INSERT INTO ticket_comments(ticket_id,user_id,corrects_comment_id,content) SELECT t.id,1,c.id,'fix again' FROM tickets t JOIN ticket_comments c ON c.content='orig' WHERE t.ticket_no='TKT-a' AND c.content='orig';"
    Check 'a comment can be corrected more than once' (Allowed $r)
    $r = As-Admin "INSERT INTO ticket_comments(ticket_id,user_id,corrects_comment_id,content) SELECT t.id,1,c.id,'wrong ticket' FROM tickets t JOIN ticket_comments c ON c.content='orig' WHERE t.ticket_no='TKT-A';"
    Check 'a correction pointing at another ticket comment is rejected by the database' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "INSERT INTO ticket_comments(ticket_id,user_id,corrects_comment_id,content) SELECT id,1,999999,'missing' FROM tickets WHERE ticket_no='TKT-a';"
    Check 'a correction pointing at a missing comment is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "DELETE FROM ticket_comments WHERE content='orig';"
    Check 'a corrected comment cannot be deleted while a correction points at it' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1451'))
    $r = As-Admin "INSERT INTO users(username,password_hash,email) VALUES ('Alice','x','alice@example.com'),('alice','x','alice2@example.com');"
    Check 'usernames keep the case-insensitive rule (Alice vs alice rejected)' (Dup $r)
    Check 'users.must_change_password defaults to FALSE' ((Scalar "SELECT CONCAT('RESULT=', COLUMN_DEFAULT) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='users' AND column_name='must_change_password';") -eq '0')
    Check 'users.failed_login_count defaults to 0' ((Scalar "SELECT CONCAT('RESULT=', COLUMN_DEFAULT) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='users' AND column_name='failed_login_count';") -eq '0')
    $r = As-Admin "INSERT INTO email_verification_codes(email,purpose,code_hash,expires_at) VALUES ('x@example.com','OTHER','h',NOW(3));"
    Check 'email_verification_codes rejects an unknown purpose' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $null = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,priority,title,description) VALUES ('TKT-batch',1,'INVENTORY_EXCEPTION','HIGH','batch','several products');"
    $r = As-Admin "INSERT INTO ticket_inventory(ticket_id,inventory_id) SELECT t.id,i.id FROM tickets t, inventory i WHERE t.ticket_no='TKT-batch' AND i.id IN (1,2,3);"
    Check 'one ticket can cover several inventory records' ((Allowed $r) -and ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM ticket_inventory;") -eq '3')) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO ticket_inventory(ticket_id,inventory_id) SELECT t.id,1 FROM tickets t WHERE t.ticket_no='TKT-batch';"
    Check 'the same inventory record cannot be linked to a ticket twice' (Dup $r)
    $r = As-Admin "INSERT INTO ticket_inventory(ticket_id,inventory_id) SELECT t.id,999999 FROM tickets t WHERE t.ticket_no='TKT-batch';"
    Check 'a link to a missing inventory record is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "DELETE FROM tickets WHERE ticket_no='TKT-batch';"
    Check 'a ticket with linked inventory records cannot be deleted' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1451'))
    $null = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,priority,title,description) VALUES ('TKT-first',1,'OTHER','LOW','first','first problem');"
    $r = As-Admin "UPDATE tickets SET status='CANCELLED', cancel_reason='opened by mistake', cancelled_by=1, cancelled_at=NOW(3) WHERE ticket_no='TKT-first';"
    Check 'a ticket can be CANCELLED with a reason, a canceller, and a time' ((Allowed $r) -and ((Scalar "SELECT CONCAT('RESULT=', status, '|', cancel_reason) FROM tickets WHERE ticket_no='TKT-first';") -eq 'CANCELLED|opened by mistake')) (Short-Err $r.Err)
    $r = As-Admin "UPDATE tickets SET status='REOPENED' WHERE ticket_no='TKT-first';"
    Check 'there is no REOPENED status' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    $null = As-Admin "UPDATE tickets SET status='CLOSED', closed_by=1, closed_at=NOW(3) WHERE ticket_no='TKT-first';"
    Check 'a ticket can be CLOSED with a closer and a time' ((Scalar "SELECT CONCAT('RESULT=', status, '|', closed_by) FROM tickets WHERE ticket_no='TKT-first';") -eq 'CLOSED|1')
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,priority,title,description,follows_ticket_id) SELECT 'TKT-second',1,'OTHER','LOW','second','continues the first',id FROM tickets WHERE ticket_no='TKT-first';"
    Check 'a ticket can follow an earlier ticket' ((Allowed $r) -and ((Scalar "SELECT CONCAT('RESULT=', COUNT(*)) FROM tickets f JOIN tickets p ON p.id=f.follows_ticket_id WHERE f.ticket_no='TKT-second' AND p.ticket_no='TKT-first';") -eq '1')) (Short-Err $r.Err)
    $r = As-Admin "INSERT INTO tickets(ticket_no,created_by,type,priority,title,description,follows_ticket_id) VALUES ('TKT-third',1,'OTHER','LOW','third','missing',999999);"
    Check 'following a ticket that does not exist is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "DELETE FROM tickets WHERE ticket_no='TKT-first';"
    Check 'a ticket that has a follow-up cannot be deleted' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1451'))
    $r = As-Admin "UPDATE tickets SET closed_by=999999 WHERE ticket_no='TKT-second';"
    Check 'closed_by must be an existing user' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $r = As-Admin "UPDATE tickets SET cancelled_by=999999 WHERE ticket_no='TKT-second';"
    Check 'cancelled_by must be an existing user' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 1452'))
    $itx = "INSERT INTO inventory_transactions(inventory_id,operator_id,type,quantity,available_before,available_after,locked_before,locked_after,reason) "
    $r = As-Admin ($itx + "VALUES (1,1,'WRITE_OFF',2,10,8,0,0,'damaged in storage');")
    Check 'a WRITE_OFF with a reason and an operator is accepted' (Allowed $r) (Short-Err $r.Err)
    $r = As-Admin ($itx + "VALUES (1,1,'WRITE_OFF',2,10,12,0,0,'wrong direction');")
    Check 'a WRITE_OFF that raises stock is rejected' (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    foreach ($ty in 'RESTOCK','WRITE_OFF','ADJUST_IN','ADJUST_OUT') {
        $after = if ($ty -in 'RESTOCK','ADJUST_IN') { 12 } else { 8 }
        $r = As-Admin ($itx + "VALUES (1,1,'$ty',2,10,$after,0,0,NULL);")
        Check "$ty without a reason is rejected" (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
        $r = As-Admin ($itx + "VALUES (1,1,'$ty',2,10,$after,0,0,'   ');")
        Check "$ty with a blank reason is rejected" (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
        $r = As-Admin ($itx + "VALUES (1,NULL,'$ty',2,10,$after,0,0,'no operator');")
        Check "$ty without an operator is rejected" (($r.Code -ne 0) -and ($r.Err -match 'ERROR 3819'))
    }

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
        "INSERT INTO orders(id,order_no,user_id,status,reserved_until) VALUES (100,'ORD-p',1,'CREATED',NOW(3)); " +
        "INSERT INTO order_items(order_id,product_id,product_sku,product_name,unit_price,quantity,subtotal) VALUES (100,1,'P1','Widget',10.00,1,10.00); " +
        "INSERT INTO inventory_transactions(inventory_id,type,quantity,available_before,available_after,locked_before,locked_after,reason,operator_id) VALUES (1,'RESTOCK',1,5,6,0,0,'initial',1); " +
        "INSERT INTO tickets(id,ticket_no,created_by,type,priority,title,description) VALUES (100,'TKT-p',1,'OTHER','LOW','t','d'); " +
        "INSERT INTO ticket_assignment_history(ticket_id,from_user_id,to_user_id,changed_by) VALUES (100,NULL,2,1); " +
        "INSERT INTO ticket_comments(ticket_id,user_id,content) VALUES (100,1,'c'); " +
        "INSERT INTO ticket_inventory(ticket_id,inventory_id) VALUES (100,1); " +        "INSERT INTO operation_logs(action,target_type,target_id) VALUES ('A','ORDER',100); " +
        "INSERT INTO email_verification_codes(email,purpose,code_hash,expires_at) VALUES ('a@example.com','REGISTER','h',NOW(3));")

    Check 'app: update mutable tables (users, roles, products, inventory, orders, tickets, email_verification_codes)' (Allowed (As-App "UPDATE email_verification_codes SET attempts=attempts+1 WHERE email='a@example.com'; UPDATE users SET failed_login_count=1, last_failed_login_at=NOW(3), locked_until=NOW(3), password_changed_at=NOW(3) WHERE id=1; UPDATE users SET status='ACTIVE' WHERE id=1; UPDATE roles SET name='R2' WHERE id=1; UPDATE products SET name='W2' WHERE id=1; UPDATE inventory SET sold_quantity=sold_quantity WHERE id=1; UPDATE orders SET remark='x' WHERE id=100; UPDATE tickets SET status='IN_PROGRESS' WHERE id=100;"))
    Check 'app: insert into every table it writes' (Allowed (As-App ("INSERT INTO users(username,password_hash,email) VALUES ('n1','x','n1@example.com'); INSERT INTO ticket_comments(ticket_id,user_id,content) VALUES (100,1,'second'); INSERT INTO ticket_inventory(ticket_id,inventory_id) VALUES (100,2); " +
        "INSERT INTO ticket_assignment_history(ticket_id,from_user_id,to_user_id,changed_by) VALUES (100,2,3,1); INSERT INTO operation_logs(action,target_type,target_id) VALUES ('B','ORDER',100); " +
        "INSERT INTO inventory_transactions(inventory_id,type,quantity,available_before,available_after,locked_before,locked_after,reason,operator_id) VALUES (1,'RESTOCK',1,6,7,0,0,'delivery',1); INSERT INTO user_roles(user_id,role_id) VALUES (2,1); INSERT INTO email_verification_codes(email,purpose,code_hash,expires_at) VALUES ('b@example.com','RESET_PASSWORD','h',NOW(3));")))
    Check 'app: SELECT ... FOR UPDATE on inventory' (Allowed (As-App "START TRANSACTION; SELECT id FROM inventory WHERE id=1 FOR UPDATE; COMMIT;"))
    $refusals = @(
        @('UPDATE inventory_transactions', "UPDATE inventory_transactions SET reason='x';"),
        @('DELETE inventory_transactions', "DELETE FROM inventory_transactions;"),
        @('UPDATE ticket_assignment_history', "UPDATE ticket_assignment_history SET reason='x';"),
        @('DELETE ticket_assignment_history', "DELETE FROM ticket_assignment_history;"),
        @('UPDATE ticket_comments', "UPDATE ticket_comments SET content='edited';"),
        @('DELETE ticket_comments', "DELETE FROM ticket_comments;"),
        @('UPDATE ticket_inventory', "UPDATE ticket_inventory SET inventory_id=2;"),
        @('DELETE ticket_inventory', "DELETE FROM ticket_inventory;"),        @('UPDATE operation_logs', "UPDATE operation_logs SET remark='x';"),
        @('DELETE operation_logs', "DELETE FROM operation_logs;"),
        @('UPDATE order_items (frozen)', "UPDATE order_items SET quantity=9;"),
        @('DELETE order_items', "DELETE FROM order_items;"),
        @('UPDATE user_roles', "UPDATE user_roles SET role_id=1;"),
        @('DELETE user_roles (until granted)', "DELETE FROM user_roles;"),
        @('DELETE orders', "DELETE FROM orders;"),
        @('DELETE users', "DELETE FROM users WHERE id=3;"),
        @('DELETE products', "DELETE FROM products;"),
        @('DELETE inventory', "DELETE FROM inventory;"),
        @('DELETE email_verification_codes', "DELETE FROM email_verification_codes;"),
        @('CREATE TABLE', "CREATE TABLE zzz (id INT);"),
        @('DROP TABLE', "DROP TABLE ticket_comments;")
    )
    foreach ($t in $refusals) { Check ('app is refused: ' + $t[0]) (Denied (As-App $t[1])) }
    $g = Allowed (As-Admin "GRANT DELETE ON $DB.user_roles TO '$APP'@'localhost';")
    $d = Allowed (As-App "DELETE FROM user_roles WHERE user_id=2;")
    Check 'app can delete user_roles once DELETE is granted on that table only' ($g -and $d)
    $null = As-Admin "REVOKE DELETE ON $DB.user_roles FROM '$APP'@'localhost';"

    # -----------------------------------------------------------------------------------------
    Section "Concurrency ($Rounds rounds per race; sessions run as the restricted application account)"
    # -----------------------------------------------------------------------------------------
    $items2 = '[{"product_id":1,"qty":3},{"product_id":2,"qty":2}]'
    $item1 = '[{"product_id":1,"qty":1}]'
    $item2q = '[{"product_id":1,"qty":2}]'
    $item3q = '[{"product_id":1,"qty":3}]'
    $item4q = '[{"product_id":1,"qty":4}]'
    $pairItems = '[{"product_id":1,"qty":1},{"product_id":2,"qty":1}]'

    # S1. Confirm and cancel at the same time.
    $bad = 0; $confirmWon = 0; $cancelWon = 0
    for ($r = 1; $r -le $Rounds; $r++) {
        $o = Result (As-Admin "CALL seed(10); CALL create_order(1,'ORD-s1-$r',NULL,'$items2',1800,@r,@o); SELECT CONCAT('RESULT=',@r,',',@o);")
        if ($o -notmatch '^CREATED,(\d+)$') { $bad++; continue }
        $id = $Matches[1]
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL confirm_order($id,NULL,@r); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL cancel_order($id,'CANCELLED','USER_CANCEL',NULL,@r); SELECT CONCAT('RESULT=',@r);" })
        $a = Result $res[0]; $b = Result $res[1]
        $st = Scalar ("SELECT CONCAT('RESULT=', o.status, '|', (SELECT COUNT(*) FROM inventory_transactions WHERE type='CONFIRM'), '|', (SELECT COUNT(*) FROM inventory_transactions WHERE type='RELEASE'), '|', " +
            "i1.available_quantity, ',', i1.locked_quantity, ',', i1.sold_quantity, '|', i2.available_quantity, ',', i2.locked_quantity, ',', i2.sold_quantity) " +
            "FROM orders o, inventory i1, inventory i2 WHERE o.id=$id AND i1.id=3 AND i2.id=2;")
        $expect = ''
        if ($st -like 'COMPLETED|*') { $expect = 'COMPLETED|2|0|7,0,3|8,0,2'; $confirmWon++ }
        elseif ($st -like 'CANCELLED|*') { $expect = 'CANCELLED|0|2|10,0,0|10,0,0'; $cancelWon++ }
        $loserOk = ($a -like 'ALREADY_*') -or ($b -like 'ALREADY_*')
        $winnerOk = ($a -eq 'COMPLETED') -or ($b -eq 'CANCELLED')
        $bothWon = ($a -eq 'COMPLETED') -and ($b -eq 'CANCELLED')
        if (($st -ne $expect) -or (-not $loserOk) -or (-not $winnerOk) -or $bothWon) { $bad++ }
    }
    Check 'confirm racing cancel: exactly one wins, stock and transactions always consistent' ($bad -eq 0) "$Rounds rounds, confirm won $confirmWon, cancel won $cancelWon, bad $bad"

    # S2. Repeated submission.
    $bad = 0
    for ($r = 1; $r -le $Rounds; $r++) {
        $null = As-Admin "CALL seed(10);"
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(1,'ORD-s2-$r-a','K1','$item2q',1800,@r,@o); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(1,'ORD-s2-$r-b','K1','$item2q',1800,@r,@o); SELECT CONCAT('RESULT=',@r);" })
        $a = Result $res[0]; $b = Result $res[1]
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders WHERE idempotency_key='K1'), '|', (SELECT CONCAT(available_quantity,',',locked_quantity) FROM inventory WHERE id=3), '|', (SELECT COUNT(*) FROM inventory_transactions WHERE type='RESERVE'));"
        $pair = @($a, $b) | Sort-Object
        if (($st -ne '1|8,2|1') -or (($pair -join ',') -ne 'CREATED,DUPLICATE')) { $bad++ }
    }
    Check 'repeated submission: one order, stock reserved once' ($bad -eq 0) "$Rounds rounds, bad $bad"
    $null = As-Admin "CALL seed(10);"
    $res = Run-Parallel @(
        @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(1,'ORD-s2-x1','K2','$item1',1800,@r,@o); SELECT CONCAT('RESULT=',@r);" },
        @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(2,'ORD-s2-x2','K2','$item1',1800,@r,@o); SELECT CONCAT('RESULT=',@r);" })
    Check 'the same key from two different users creates two orders' (((Result $res[0]) -eq 'CREATED') -and ((Result $res[1]) -eq 'CREATED'))

    # S3. Multi-item order with one item short.
    $itemsShort = '[{"product_id":1,"qty":2},{"product_id":2,"qty":3},{"product_id":3,"qty":1}]'
    $null = As-Admin "CALL seed(10); UPDATE inventory SET available_quantity=1 WHERE product_id=2;"
    $res = Result (As-App "CALL create_order(1,'ORD-s3',NULL,'$itemsShort',1800,@r,@o); SELECT CONCAT('RESULT=',@r);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders), '|', (SELECT COUNT(*) FROM order_items), '|', (SELECT COUNT(*) FROM inventory_transactions), '|', (SELECT GROUP_CONCAT(available_quantity ORDER BY id) FROM inventory), '|', (SELECT SUM(locked_quantity) FROM inventory));"
    Check 'one item short: whole order rolled back, no order, no stock change, no transaction' (($res -eq 'OUT_OF_STOCK') -and ($st -eq '0|0|0|10,1,10|0')) "$res / $st"

    $bad = 0
    for ($r = 1; $r -le $Rounds; $r++) {
        $null = As-Admin "CALL seed(10); UPDATE inventory SET available_quantity=1 WHERE product_id=2;"
        $specs = @()
        for ($i = 1; $i -le 5; $i++) {
            $u = (($i - 1) % 3) + 1
            $specs += @{ User = $APP; Pass = $appPw; Sql = "CALL create_order($u,'ORD-s3-$r-$i',NULL,'$pairItems',1800,@r,@o); SELECT CONCAT('RESULT=',@r);" }
        }
        $res = Run-Parallel $specs
        $created = @($res | Where-Object { (Result $_) -eq 'CREATED' }).Count
        $oos = @($res | Where-Object { (Result $_) -eq 'OUT_OF_STOCK' }).Count
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders), '|', (SELECT CONCAT(available_quantity,',',locked_quantity) FROM inventory WHERE id=2), '|', (SELECT CONCAT(available_quantity,',',locked_quantity) FROM inventory WHERE id=3), '|', (SELECT COUNT(*) FROM inventory_transactions));"
        if (($created -ne 1) -or ($oos -ne 4) -or ($st -ne '1|0,1|9,1|2')) { $bad++ }
    }
    Check 'five buyers race for the last unit: one wins, four see out of stock, nothing half-done' ($bad -eq 0) "$Rounds rounds, bad $bad"

    # S4. Many reservations on one stock row: no oversell, and an unbroken snapshot chain.
    $chainSql = "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM (SELECT available_before ab, locked_before lb, LAG(available_after) OVER (ORDER BY id) pa, LAG(locked_after) OVER (ORDER BY id) pl FROM inventory_transactions WHERE inventory_id=3) t WHERE pa IS NOT NULL AND (ab <> pa OR lb <> pl)));"
    $bad = 0
    $rounds4 = [Math]::Max(3, [int]($Rounds / 2))
    for ($r = 1; $r -le $rounds4; $r++) {
        $null = As-Admin "CALL seed(20);"
        $specs = @()
        for ($i = 1; $i -le 10; $i++) {
            $u = (($i - 1) % 3) + 1
            $specs += @{ User = $APP; Pass = $appPw; Sql = "CALL create_order($u,'ORD-s4-$r-$i',NULL,'$item3q',1800,@r,@o); SELECT CONCAT('RESULT=',@r);" }
        }
        $res = Run-Parallel $specs
        $created = @($res | Where-Object { (Result $_) -eq 'CREATED' }).Count
        $st = Scalar "SELECT CONCAT('RESULT=', available_quantity, ',', locked_quantity, '|', (SELECT COUNT(*) FROM inventory_transactions WHERE inventory_id=3 AND type='RESERVE'), '|', (SELECT available_before FROM inventory_transactions WHERE inventory_id=3 ORDER BY id LIMIT 1), '|', (SELECT available_after FROM inventory_transactions WHERE inventory_id=3 ORDER BY id DESC LIMIT 1)) FROM inventory WHERE id=3;"
        $broken = Scalar $chainSql
        if (($created -ne 6) -or ($st -ne '2,18|6|20|2') -or ($broken -ne '0')) { $bad++ }
    }
    Check 'ten buyers, stock for six: no oversell, snapshot chain unbroken and ends at the real stock' ($bad -eq 0) "$rounds4 rounds, bad $bad"

    # Negative control: the same race with an unlocked read must break the chain.
    $null = As-Admin "CALL seed(20);"
    $ids = Scalar "INSERT INTO orders(order_no,user_id,status,reserved_until) VALUES ('C1',1,'CREATED',NOW(3)),('C2',1,'CREATED',NOW(3)),('C3',1,'CREATED',NOW(3)),('C4',1,'CREATED',NOW(3)),('C5',1,'CREATED',NOW(3)),('C6',1,'CREATED',NOW(3)); SELECT CONCAT('RESULT=', GROUP_CONCAT(id)) FROM orders;"
    $specs = @()
    foreach ($oid in ($ids -split ',')) { $specs += @{ User = $APP; Pass = $appPw; Sql = "CALL reserve_unsafe($oid,3,3);" } }
    $null = Run-Parallel $specs -NoRetry
    $broken = [int](Scalar $chainSql)
    Check 'negative control: reading stock without a lock DOES break the snapshot chain' ($broken -gt 0) "broken links: $broken"

    # S5. Timeout and expired confirmation.
    $bad = 0
    $rounds5 = [Math]::Max(3, [int]($Rounds / 3))
    for ($r = 1; $r -le $rounds5; $r++) {
        $null = As-Admin ("CALL seed(10); CALL create_order(1,'ORD-s5-$r-1',NULL,'$item1',-30,@r,@o); CALL create_order(1,'ORD-s5-$r-2',NULL,'$item1',-30,@r,@o); " +
            "CALL create_order(2,'ORD-s5-$r-3',NULL,'$item1',-30,@r,@o); CALL create_order(2,'ORD-s5-$r-4',NULL,'$item1',1800,@r,@o);")
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL timeout_job(100,@n); SELECT CONCAT('RESULT=',@n);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL timeout_job(100,@n); SELECT CONCAT('RESULT=',@n);" }) -NoRetry
        $n = 0
        foreach ($x in $res) { $v = Result $x; if ($v -match '^\d+$') { $n += [int]$v } else { $n = -999 } }
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT COUNT(*) FROM orders WHERE status='CANCELLED' AND status_reason='RESERVATION_TIMEOUT'), '|', (SELECT COUNT(*) FROM orders WHERE status='CREATED'), '|', (SELECT COUNT(*) FROM inventory_transactions WHERE type='RELEASE'), '|', (SELECT CONCAT(available_quantity,',',locked_quantity) FROM inventory WHERE id=3));"
        if (($n -ne 3) -or ($st -ne '3|1|3|9,1')) { $bad++ }
    }
    Check 'two timeout jobs at once: each expired order cancelled and released exactly once, live order untouched' ($bad -eq 0) "$rounds5 rounds, bad $bad"

    $null = As-Admin ("CALL seed(10); CALL create_order(1,'ORD-s5e',NULL,'$item2q',-5,@r,@o); CALL create_order(1,'ORD-s5l',NULL,'$item3q',1800,@r,@o);")
    $ids = Scalar "SELECT CONCAT('RESULT=', (SELECT id FROM orders WHERE order_no='ORD-s5e'), ',', (SELECT id FROM orders WHERE order_no='ORD-s5l'));"
    $eid, $lid = $ids -split ','
    $c1 = Result (As-App "CALL confirm_order($eid,NULL,@r); SELECT CONCAT('RESULT=',@r);")
    $c2 = Result (As-App "CALL confirm_order($eid,NULL,@r); SELECT CONCAT('RESULT=',@r);")
    $c3 = Result (As-App "CALL confirm_order(999999,NULL,@r); SELECT CONCAT('RESULT=',@r);")
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT CONCAT(available_quantity,',',locked_quantity,',',sold_quantity) FROM inventory WHERE id=3), '|', (SELECT COUNT(*) FROM inventory_transactions WHERE type='RELEASE'));"
    Check 'confirming an expired order cancels it and releases the stock at once' (($c1 -eq 'EXPIRED_CANCELLED') -and ($c2 -eq 'ALREADY_CANCELLED') -and ($st -eq '7,3,0|1')) "$c1 / $c2 / $st"
    Check 'confirming an unknown order reports not found' ($c3 -eq 'NOT_FOUND') $c3
    $res = Run-Parallel @(
        @{ User = $APP; Pass = $appPw; Sql = "CALL confirm_order($lid,NULL,@r); SELECT CONCAT('RESULT=',@r);" },
        @{ User = $APP; Pass = $appPw; Sql = "CALL confirm_order($lid,NULL,@r); SELECT CONCAT('RESULT=',@r);" })
    $pair = @((Result $res[0]), (Result $res[1])) | Sort-Object
    $st = Scalar "SELECT CONCAT('RESULT=', (SELECT CONCAT(available_quantity,',',locked_quantity,',',sold_quantity) FROM inventory WHERE id=3), '|', (SELECT COUNT(*) FROM inventory_transactions WHERE type='CONFIRM'));"
    Check 'two confirmations at once: one completes, the other sees it done, stock deducted once' ((($pair -join ',') -eq 'ALREADY_COMPLETED,COMPLETED') -and ($st -eq '7,0,3|1')) "$($pair -join ',') / $st"
    $c4 = Result (As-App "CALL cancel_order($lid,'CANCELLED','late',NULL,@r); SELECT CONCAT('RESULT=',@r);")
    Check 'cancelling a completed order is refused' ($c4 -eq 'ALREADY_COMPLETED') $c4

    $bad = 0
    for ($r = 1; $r -le $rounds5; $r++) {
        $oid = (Result (As-Admin "CALL seed(10); CALL create_order(1,'ORD-s5c-$r',NULL,'$item4q',1800,@r,@o); SELECT CONCAT('RESULT=',@r,',',@o);")) -replace '^CREATED,', ''
        $res = Run-Parallel @(
            @{ User = $APP; Pass = $appPw; Sql = "CALL cancel_order($oid,'CANCELLED','a',NULL,@r); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL cancel_order($oid,'CANCELLED','b',NULL,@r); SELECT CONCAT('RESULT=',@r);" })
        $pair = @((Result $res[0]), (Result $res[1])) | Sort-Object
        $st = Scalar "SELECT CONCAT('RESULT=', (SELECT CONCAT(available_quantity,',',locked_quantity) FROM inventory WHERE id=3), '|', (SELECT COUNT(*) FROM inventory_transactions WHERE type='RELEASE'));"
        if ((($pair -join ',') -ne 'ALREADY_CANCELLED,CANCELLED') -or ($st -ne '10,0|1')) { $bad++ }
    }
    Check 'two cancellations at once: stock released once' ($bad -eq 0) "$rounds5 rounds, bad $bad"

    # S6. Deadlocks and lock waits.
    $null = As-Admin "CALL seed(10);"
    $sa = "START TRANSACTION;`nINSERT INTO operation_logs(action,target_type,target_id) VALUES ('A','T',1);`nSELECT id FROM inventory WHERE id=1 FOR UPDATE;`nDO SLEEP(1.5);`nSELECT id FROM inventory WHERE id=2 FOR UPDATE;`nCOMMIT;`nSELECT 'RESULT=COMMITTED';"
    $sb = "START TRANSACTION;`nINSERT INTO operation_logs(action,target_type,target_id) VALUES ('B','T',1);`nSELECT id FROM inventory WHERE id=2 FOR UPDATE;`nDO SLEEP(1.5);`nSELECT id FROM inventory WHERE id=1 FOR UPDATE;`nCOMMIT;`nSELECT 'RESULT=COMMITTED';"
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
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(1,'ORD-s6-$r-a',NULL,'$fwd',1800,@r,@o); SELECT CONCAT('RESULT=',@r);" },
            @{ User = $APP; Pass = $appPw; Sql = "CALL create_order(2,'ORD-s6-$r-b',NULL,'$rev',1800,@r,@o); SELECT CONCAT('RESULT=',@r);" })
        if (((Result $res[0]) -ne 'CREATED') -or ((Result $res[1]) -ne 'CREATED')) { $bad++ }
    }
    Check 'orders listing items in opposite order never deadlock (locks are taken by ascending inventory id)' (($bad -eq 0) -and ($script:retries -eq 0)) "$Rounds rounds, retries needed $($script:retries), failed $bad"

    # A lock wait timeout rolls back only the statement that timed out.
    $null = As-Admin "CALL seed(10);"
    $holder = "START TRANSACTION;`nSELECT id FROM inventory WHERE id=1 FOR UPDATE;`nDO SLEEP(7);`nCOMMIT;"
    $waiter = "SET SESSION innodb_lock_wait_timeout = 2;`nSTART TRANSACTION;`nINSERT INTO operation_logs(action,target_type,target_id) VALUES ('W','T',1);`nUPDATE inventory SET sold_quantity = sold_quantity WHERE id=1;`nSELECT CONCAT('RESULT=after_timeout:', (SELECT COUNT(*) FROM operation_logs WHERE action='W'));`nROLLBACK;`nSELECT CONCAT('RESULT=after_rollback:', (SELECT COUNT(*) FROM operation_logs WHERE action='W'));"
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
