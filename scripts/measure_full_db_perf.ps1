<#
.SYNOPSIS
  DB-side performance evidence on the full course-data MySQL database (stage 1, NFR-PERF-01/02).

.DESCRIPTION
  Replays, statement by statement, the SQL that backend-procedural issues for:
    - one authorization decision (AuthorizationService.require / target with DEPT scope)
    - GET /api/v1/users with departmentId + roleId, page 20
    - GET /api/v1/ledger/employees (DEPT scope), page 20
    - PUT /api/v1/roles/{id}/permissions (whole-set replace) inside a transaction that is ROLLED BACK
  plus EXPLAIN ANALYZE of the key queries and of the single-query view / recursive-CTE forms.
  Every item: 1 warm-up + N measured runs; median and max are reported.

  Safety: only database rbac_verify_full is accepted; the grant-save runs end with ROLLBACK and a
  before/after fingerprint of sys_role, sys_role_permission, sys_user and sys_user_role is compared.
  The password is read with Read-Host -AsSecureString into a temporary --defaults-extra-file that is
  deleted in finally. Result rows (names, phones) are read into memory and discarded; the JSON output
  holds only surrogate ids, counts, permission codes, timings and query plans.
#>
#Requires -Version 7.0
param(
  [string]$OutFile = (Join-Path ([IO.Path]::GetTempPath()) 'rbac-full-db-perf.json'),
  [string]$MySql = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
  [string]$HostName = '127.0.0.1',
  [int]$Port = 3306,
  [string]$User = 'root',
  [int]$Runs = 5
)
$ErrorActionPreference = 'Stop'
$Database = 'rbac_verify_full'
$utf8 = New-Object Text.UTF8Encoding $false
if (-not (Test-Path $MySql)) { throw "mysql client not found: $MySql" }

$tmp = Join-Path ([IO.Path]::GetTempPath()) ('rbacperf-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null
$cnf = Join-Path $tmp 'client.cnf'

function Invoke-Sql([string]$sql) {
  $psi = New-Object Diagnostics.ProcessStartInfo
  $psi.FileName = $MySql
  $psi.Arguments = "--defaults-extra-file=`"$cnf`" --batch --skip-column-names --default-character-set=utf8mb4 $Database"
  $psi.UseShellExecute = $false
  $psi.RedirectStandardInput = $true
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true
  $psi.StandardOutputEncoding = [Text.Encoding]::UTF8
  $psi.StandardErrorEncoding = [Text.Encoding]::UTF8
  $psi.StandardInputEncoding = $utf8
  $p = [Diagnostics.Process]::Start($psi)
  $outTask = $p.StandardOutput.ReadToEndAsync()
  $errTask = $p.StandardError.ReadToEndAsync()
  $p.StandardInput.Write($sql)
  $p.StandardInput.Close()
  $p.WaitForExit()
  $out = $outTask.Result
  $err = $errTask.Result
  if ($p.ExitCode -ne 0) { throw "mysql exited $($p.ExitCode): $err" }
  # keep only marker lines and EXPLAIN trees; data rows are dropped here
  $keep = New-Object Collections.Generic.List[string]
  foreach ($line in $out -split "`r?`n") {
    if ($line.StartsWith('##') -or $line.StartsWith('->')) { $keep.Add($line) }
  }
  $out = $null
  return $keep
}

function Q([string]$s) { "'" + $s.Replace('\', '\\').Replace("'", "''") + "'" }

# ---- SQL text exactly as the mappers send it (Connector/J inlines setString values as quoted strings)
function SqlFindUser($id) { "SELECT * FROM sys_user WHERE id=$(Q $id);" }
function SqlRoleIds($id, [bool]$enabledOnly) {
  $s = "SELECT r.id FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=$(Q $id)"
  if ($enabledOnly) { $s += ' AND r.status=1' }
  $s + ' ORDER BY r.id;'
}
function SqlGrants($roleId) {
  "SELECT rp.role_id,p.code,rp.data_scope FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.role_id=$(Q $roleId) ORDER BY p.id;"
}
$SqlDeptAll = 'SELECT * FROM sys_dept ORDER BY sort_no,id;'
function SqlDept($id) { "SELECT * FROM sys_dept WHERE id=$(Q $id);" }
$SqlUserAll = 'SELECT * FROM sys_user ORDER BY id;'
function SqlRoleUsers($rid) { "SELECT user_id FROM sys_user_role WHERE role_id=$(Q $rid) ORDER BY user_id;" }
function SqlView($uid, $code) {
  "SELECT data_scope FROM v_user_permission WHERE user_id=$(Q $uid) AND permission_code=$(Q $code);"
}
function SqlCteCovers($actorDept, $target) {
  "WITH RECURSIVE t AS (SELECT id FROM sys_dept WHERE id=$(Q $actorDept) UNION ALL SELECT d.id FROM sys_dept d JOIN t ON d.parent_id=t.id) SELECT u.id FROM sys_user u JOIN t ON t.id=u.dept_id WHERE u.id=$(Q $target);"
}

$Integrity = @"
SELECT CONCAT('##I|sys_role_permission|',COUNT(*),'|',BIT_XOR(CRC32(CONCAT_WS('|',role_id,permission_id,data_scope)))) FROM sys_role_permission;
SELECT CONCAT('##I|sys_role|',COUNT(*),'|',BIT_XOR(CRC32(CONCAT_WS('|',id,code,status,permission_version)))) FROM sys_role;
SELECT CONCAT('##I|sys_user|',COUNT(*),'|',BIT_XOR(CRC32(CONCAT_WS('|',id,status,role_version,IFNULL(updated_at,''))))) FROM sys_user;
SELECT CONCAT('##I|sys_user_role|',COUNT(*),'|',BIT_XOR(CRC32(CONCAT_WS('|',user_id,role_id)))) FROM sys_user_role;
"@

$Setup = @"
SELECT CONCAT('##P|db|',DATABASE());
SELECT CONCAT('##P|version|',VERSION());
SELECT CONCAT('##P|buffer_pool_mb|',@@innodb_buffer_pool_size DIV 1048576);
SELECT CONCAT('##P|isolation|',@@transaction_isolation);
SELECT CONCAT('##P|n_dept|',COUNT(*)) FROM sys_dept;
SELECT CONCAT('##P|n_user|',COUNT(*)) FROM sys_user;
SELECT CONCAT('##P|n_user_role|',COUNT(*)) FROM sys_user_role;
SELECT CONCAT('##P|n_role|',COUNT(*)) FROM sys_role;
SELECT CONCAT('##P|n_perm|',COUNT(*)) FROM sys_permission;
SELECT CONCAT('##P|n_grant|',COUNT(*)) FROM sys_role_permission;
SELECT CONCAT('##P|n_view|',COUNT(*)) FROM v_user_permission;
SELECT CONCAT('##P|data_mb|',ROUND(SUM(data_length+index_length)/1048576,1)) FROM information_schema.tables WHERE table_schema=DATABASE();
SELECT CONCAT('##ROLE|',r.id,'|',r.code,'|',r.permission_version,'|',r.status,'|',
  (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id=r.id),'|',
  (SELECT COUNT(*) FROM sys_user_role ur WHERE ur.role_id=r.id)) FROM sys_role r ORDER BY r.id;
SET @ad=NULL; SELECT id FROM sys_user WHERE username='admin' INTO @ad;
SET @la=NULL,@lad=NULL,@lscope=NULL;
WITH RECURSIVE anc AS (SELECT id AS root, id FROM sys_dept UNION ALL SELECT a.root, d.id FROM sys_dept d JOIN anc a ON d.parent_id=a.id),
 sz AS (SELECT a.root, COUNT(u.id) AS nu FROM anc a LEFT JOIN sys_user u ON u.dept_id=a.id GROUP BY a.root)
SELECT v.user_id, u.dept_id, v.data_scope FROM v_user_permission v JOIN sys_user u ON u.id=v.user_id JOIN sz ON sz.root=u.dept_id
 WHERE v.permission_code='hr:f046:read' ORDER BY FIELD(v.data_scope,'DEPT','ALL','SELF'), sz.nu DESC, v.user_id LIMIT 1 INTO @la,@lad,@lscope;
SET @lt=NULL,@lo=NULL;
WITH RECURSIVE t AS (SELECT id, 0 AS lvl FROM sys_dept WHERE id=@lad UNION ALL SELECT d.id, t.lvl+1 FROM sys_dept d JOIN t ON d.parent_id=t.id)
SELECT u.id FROM sys_user u JOIN t ON t.id=u.dept_id WHERE u.id<>@la ORDER BY t.lvl DESC, u.id DESC LIMIT 1 INTO @lt;
WITH RECURSIVE t AS (SELECT id FROM sys_dept WHERE id=@lad UNION ALL SELECT d.id FROM sys_dept d JOIN t ON d.parent_id=t.id)
SELECT u.id FROM sys_user u WHERE u.dept_id NOT IN (SELECT id FROM t) ORDER BY u.id DESC LIMIT 1 INTO @lo;
SET @lnd=NULL,@lnu=NULL;
WITH RECURSIVE t AS (SELECT id FROM sys_dept WHERE id=@lad UNION ALL SELECT d.id FROM sys_dept d JOIN t ON d.parent_id=t.id)
SELECT (SELECT COUNT(*) FROM t), (SELECT COUNT(*) FROM sys_user u WHERE u.dept_id IN (SELECT id FROM t)) INTO @lnd,@lnu;
SELECT CONCAT('##P|ledger|',IFNULL(@la,''),'|',IFNULL(@lad,''),'|',IFNULL(@lscope,''),'|',IFNULL(@lt,''),'|',IFNULL(@lo,''),'|',IFNULL(@lnd,''),'|',IFNULL(@lnu,''));
SET @sa=NULL,@scode=NULL;
SELECT ur.user_id FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE r.code='STAFF'
 AND NOT EXISTS (SELECT 1 FROM sys_user_role x WHERE x.user_id=ur.user_id AND x.role_id<>ur.role_id) ORDER BY ur.user_id LIMIT 1 INTO @sa;
SELECT p.code FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id JOIN sys_role r ON r.id=rp.role_id
 WHERE r.code='STAFF' ORDER BY p.id LIMIT 1 INTO @scode;
SELECT CONCAT('##P|admin|',IFNULL(@ad,''));
SELECT CONCAT('##P|staff|',IFNULL(@sa,''),'|',IFNULL(@scode,''));
SET @fd=NULL,@fr=NULL,@fn=NULL,@fd2=NULL,@fr2=NULL,@fn2=NULL;
WITH RECURSIVE anc AS (SELECT id AS root, id FROM sys_dept UNION ALL SELECT a.root, d.id FROM sys_dept d JOIN anc a ON d.parent_id=a.id),
 nd AS (SELECT root, COUNT(*) AS c FROM anc GROUP BY root)
SELECT a.root, ur.role_id, COUNT(*) AS n FROM anc a JOIN nd ON nd.root=a.root AND nd.c>1
 JOIN sys_dept rd ON rd.id=a.root AND rd.parent_id IS NOT NULL
 JOIN sys_user u ON u.dept_id=a.id JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id AND r.code<>'ADMIN'
 GROUP BY a.root, ur.role_id ORDER BY n DESC, a.root, ur.role_id LIMIT 1 INTO @fd,@fr,@fn;
WITH RECURSIVE anc AS (SELECT id AS root, id FROM sys_dept UNION ALL SELECT a.root, d.id FROM sys_dept d JOIN anc a ON d.parent_id=a.id)
SELECT a.root, ur.role_id, COUNT(*) AS n FROM anc a
 JOIN sys_user u ON u.dept_id=a.id JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id AND r.code<>'ADMIN'
 GROUP BY a.root, ur.role_id ORDER BY n DESC, a.root, ur.role_id LIMIT 1 INTO @fd2,@fr2,@fn2;
SET @fr=IF(@fd IS NULL,@fr2,@fr), @fn=IF(@fd IS NULL,@fn2,@fn), @fd=IFNULL(@fd,@fd2);
SET @fnd=NULL;
WITH RECURSIVE t AS (SELECT id FROM sys_dept WHERE id=@fd UNION ALL SELECT d.id FROM sys_dept d JOIN t ON d.parent_id=t.id)
SELECT COUNT(*) FROM t INTO @fnd;
SELECT CONCAT('##P|list|',IFNULL(@fd,''),'|',IFNULL(@fr,''),'|',IFNULL(@fn,''),'|',IFNULL(@fnd,''));
WITH RECURSIVE t AS (SELECT id FROM sys_dept WHERE id=@fd UNION ALL SELECT d.id FROM sys_dept d JOIN t ON d.parent_id=t.id)
SELECT CONCAT('##LP|',u.id,'|',u.dept_id) FROM sys_user u JOIN t ON t.id=u.dept_id
 JOIN sys_user_role ur ON ur.user_id=u.id AND ur.role_id=@fr ORDER BY u.id LIMIT 20;
WITH RECURSIVE t AS (SELECT id FROM sys_dept WHERE id=@lad UNION ALL SELECT d.id FROM sys_dept d JOIN t ON d.parent_id=t.id)
SELECT CONCAT('##LG|',u.id,'|',u.dept_id) FROM sys_user u WHERE (@lscope='ALL' OR u.dept_id IN (SELECT id FROM t)) ORDER BY u.id LIMIT 20;
SELECT CONCAT('##UR|',ur.user_id,'|',GROUP_CONCAT(r.id ORDER BY r.id)) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id AND r.status=1
 WHERE ur.user_id IN (@ad,@la,@sa) GROUP BY ur.user_id;
SET @gr=NULL,@gv=NULL;
SELECT r.id, r.permission_version FROM sys_role r WHERE r.code<>'ADMIN'
 ORDER BY (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id=r.id) DESC, r.id LIMIT 1 INTO @gr,@gv;
SELECT CONCAT('##GR|',@gr,'|',@gv,'|',(SELECT code FROM sys_role WHERE id=@gr));
SELECT CONCAT('##G|',p.code,'|',rp.data_scope) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.role_id=@gr ORDER BY p.id;
SELECT CONCAT('##C|',code) FROM sys_permission ORDER BY id;
"@

function Median([double[]]$xs) { $s = $xs | Sort-Object; $s[[int][math]::Floor(($s.Count - 1) / 2)] }

try {
  $sec = Read-Host -AsSecureString "MySQL password for $User@${HostName}:$Port"
  $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
  try { $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
  finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
  $escaped = $plain.Replace('\', '\\').Replace('"', '\"')
  [IO.File]::WriteAllText($cnf, "[client]`nuser=$User`npassword=`"$escaped`"`nhost=$HostName`nport=$Port`n", $utf8)
  $plain = $null; $escaped = $null

  Write-Host 'Phase 1: environment, data volume and parameters...'
  $lines = Invoke-Sql ($Setup + $Integrity)
  $P = @{}; $roles = @(); $listPage = @(); $ledgerPage = @(); $userRoles = @{}; $grants = @(); $codes = @(); $before = @{}
  foreach ($l in $lines) {
    $f = $l.Split('|')
    switch ($f[0]) {
      '##P' { $P[$f[1]] = @($f[2..($f.Count - 1)]) }
      '##ROLE' { $roles += [ordered]@{ id = $f[1]; code = $f[2]; version = $f[3]; status = $f[4]; grants = [int]$f[5]; users = [int]$f[6] } }
      '##LP' { $listPage += , @($f[1], $f[2]) }
      '##LG' { $ledgerPage += , @($f[1], $f[2]) }
      '##UR' { $userRoles[$f[1]] = $f[2].Split(',') }
      '##G' { $grants += , @($f[1], $f[2]) }
      '##C' { $codes += $f[1] }
      '##GR' { $P['grant_role'] = @($f[1..3]) }
      '##I' { $before[$f[1]] = "$($f[2])|$($f[3])" }
    }
  }
  if ($P['db'][0] -ne $Database) { throw "connected to $($P['db'][0]), expected $Database" }
  $admin = $P['admin'][0]; $la, $lad, $lscope, $lt, $lo, $lnd, $lnu = $P['ledger']
  $sa, $scode = $P['staff']; $fd, $fr, $fn, $fnd = $P['list']; $gr, $gv, $gcode = $P['grant_role']
  foreach ($v in @($admin, $la, $lad, $lt, $lo, $sa, $fd, $fr, $gr)) { if (-not $v) { throw 'parameter selection failed; see ##P lines' } }
  # a page holds min(20, visible rows); record the real size instead of padding
  Write-Host ("  users list: subtree depts {0}, role users {1}, page rows {2}" -f $fnd, $fn, $listPage.Count)
  Write-Host ("  ledger actor: scope {0}, subtree depts {1}, users {2}, page rows {3}" -f $lscope, $lnd, $lnu, $ledgerPage.Count)
  Write-Host ("  grant role: {0} with {1} grants; all codes {2}" -f $gcode, $grants.Count, $codes.Count)
  $adminRole = ($roles | Where-Object { $_.code -eq 'ADMIN' }).id

  function AuthSeq($uid) {
    $s = @((SqlFindUser $uid), (SqlRoleIds $uid $true))
    foreach ($r in $userRoles[$uid]) { $s += SqlGrants $r }
    $s
  }

  $explain = [ordered]@{
    'view_admin_sys_f339_read' = SqlView $admin 'sys:f339:read'
    'view_ledger_actor_hr_f046_read' = SqlView $la 'hr:f046:read'
    'view_staff_denied_sys_f339_read' = SqlView $sa 'sys:f339:read'
    'mapper_findById_user' = SqlFindUser $la
    'mapper_roleIds_enabled_admin' = SqlRoleIds $admin $true
    'mapper_findGrants_admin_role' = SqlGrants $adminRole
    'mapper_findGrants_grant_role' = SqlGrants $gr
    'mapper_dept_findAll' = $SqlDeptAll
    'cte_covers_dept_in_scope' = SqlCteCovers $lad $lt
    'cte_covers_dept_out_of_scope' = SqlCteCovers $lad $lo
    'mapper_user_findAll' = $SqlUserAll
    'mapper_role_userIds' = SqlRoleUsers $fr
  }
  $timed = [ordered]@{
    'single_view_admin' = @(SqlView $admin 'sys:f339:read')
    'single_view_ledger_actor' = @(SqlView $la 'hr:f046:read')
    'single_cte_covers_in_scope' = @(SqlCteCovers $lad $lt)
    'require_admin' = AuthSeq $admin
    'require_ledger_actor' = AuthSeq $la
    'require_staff' = AuthSeq $sa
    'target_dept_scope' = (AuthSeq $la) + @((SqlFindUser $la), (SqlFindUser $lt), $SqlDeptAll)
  }
  $list = (AuthSeq $admin) + @((SqlFindUser $admin), $SqlDeptAll, (SqlRoleUsers $fr), $SqlUserAll)
  foreach ($row in $listPage) { $list += SqlDept $row[1]; $list += SqlRoleIds $row[0] $false }
  $timed['users_list_dept_role_page20'] = $list
  $ledger = (AuthSeq $la) + @((SqlFindUser $la), $SqlUserAll)
  if ($lscope -eq 'DEPT') { $ledger += $SqlDeptAll }
  foreach ($row in $ledgerPage) { $ledger += SqlDept $row[1] }
  $timed['ledger_search_page20'] = $ledger

  $sb = New-Object Text.StringBuilder
  foreach ($k in $explain.Keys) {
    for ($i = 0; $i -le $Runs; $i++) { [void]$sb.AppendLine("SELECT '##E|$k|$i';"); [void]$sb.AppendLine('EXPLAIN ANALYZE ' + $explain[$k]) }
  }
  foreach ($k in $timed.Keys) {
    for ($i = 0; $i -le $Runs; $i++) {
      [void]$sb.AppendLine('SET @t0=NOW(6);')
      foreach ($s in $timed[$k]) { [void]$sb.AppendLine($s) }
      [void]$sb.AppendLine("SELECT CONCAT('##T|$k|$i|',TIMESTAMPDIFF(MICROSECOND,@t0,NOW(6)));")
    }
  }
  # PUT /roles/{id}/permissions: RoleService.replacePermissions under READ COMMITTED, then ROLLBACK
  $sets = [ordered]@{ "grant_save_same_set_$($grants.Count)" = $grants; "grant_save_upper_bound_$($codes.Count)" = @($codes | ForEach-Object { , @($_, 'ALL') }) }
  [void]$sb.AppendLine('SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;')
  foreach ($k in $sets.Keys) {
    for ($i = 0; $i -le $Runs; $i++) {
      [void]$sb.AppendLine('SET @t0=NOW(6);')
      [void]$sb.AppendLine('START TRANSACTION;')
      foreach ($s in (AuthSeq $admin)) { [void]$sb.AppendLine($s) }
      [void]$sb.AppendLine("SELECT * FROM sys_role WHERE id=$(Q $gr) FOR UPDATE;")
      [void]$sb.AppendLine('SELECT code FROM sys_permission ORDER BY id;')
      [void]$sb.AppendLine("UPDATE sys_role SET permission_version=permission_version+1 WHERE id=$(Q $gr) AND permission_version=$(Q $gv);")
      [void]$sb.AppendLine("DELETE FROM sys_role_permission WHERE role_id=$(Q $gr);")
      foreach ($g in $sets[$k]) {
        [void]$sb.AppendLine("INSERT INTO sys_role_permission(role_id,permission_id,data_scope) SELECT $(Q $gr),id,$(Q $g[1]) FROM sys_permission WHERE code=$(Q $g[0]);")
      }
      [void]$sb.AppendLine("SELECT * FROM sys_role WHERE id=$(Q $gr);")
      [void]$sb.AppendLine((SqlGrants $gr))
      [void]$sb.AppendLine("SELECT CONCAT('##T|$k|$i|',TIMESTAMPDIFF(MICROSECOND,@t0,NOW(6)));")
      [void]$sb.AppendLine("SELECT CONCAT('##V|$k|$i|',(SELECT COUNT(*) FROM sys_role_permission WHERE role_id=$(Q $gr)),'|',(SELECT permission_version FROM sys_role WHERE id=$(Q $gr)));")
      [void]$sb.AppendLine('SET @t1=NOW(6);')
      [void]$sb.AppendLine('ROLLBACK;')
      [void]$sb.AppendLine("SELECT CONCAT('##RB|$k|$i|',TIMESTAMPDIFF(MICROSECOND,@t1,NOW(6)));")
    }
  }
  [void]$sb.AppendLine('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;')

  Write-Host "Phase 2: measuring (1 warm-up + $Runs runs per item)..."
  $out = Invoke-Sql $sb.ToString()

  Write-Host 'Phase 3: integrity check after rollback...'
  $after = @{}
  foreach ($l in (Invoke-Sql $Integrity)) { $f = $l.Split('|'); $after[$f[1]] = "$($f[2])|$($f[3])" }
  $same = $true
  foreach ($k in $before.Keys) { if ($before[$k] -ne $after[$k]) { $same = $false } }

  $E = [ordered]@{}; $T = [ordered]@{}; $V = @(); $RB = [ordered]@{}; $pending = $null
  foreach ($l in $out) {
    if ($l.StartsWith('->')) {
      if ($pending) {
        # MySQL prints tiny times in e-notation (e.g. 100e-6 ms)
        $m = [regex]::Match($l, 'actual time=([0-9.eE+-]+?)\.\.([0-9.eE+-]+)')
        if (-not $E.Contains($pending[0])) { $E[$pending[0]] = [ordered]@{ ms = @(); plan = '' } }
        if ([int]$pending[1] -gt 0) { $E[$pending[0]].ms += [double]::Parse($m.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture) }
        $E[$pending[0]].plan = $l.Replace('\n', "`n")
        $pending = $null
      }
      continue
    }
    $f = $l.Split('|')
    switch ($f[0]) {
      '##E' { $pending = @($f[1], $f[2]) }
      '##T' { if (-not $T.Contains($f[1])) { $T[$f[1]] = @() }; if ([int]$f[2] -gt 0) { $T[$f[1]] += [double]$f[3] / 1000 } }
      '##V' { $V += "$($f[1])#$($f[2]): grants=$($f[3]) version=$($f[4])" }
      '##RB' { if (-not $RB.Contains($f[1])) { $RB[$f[1]] = @() }; if ([int]$f[2] -gt 0) { $RB[$f[1]] += [double]$f[3] / 1000 } }
    }
  }

  $cpu = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name.Trim()
  $ramGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
  $result = [ordered]@{
    measuredAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz')
    environment = [ordered]@{ os = [Environment]::OSVersion.VersionString; cpu = $cpu; ramGb = $ramGb; mysql = $P['version'][0]; bufferPoolMb = $P['buffer_pool_mb'][0]; dataMb = $P['data_mb'][0]; client = 'mysql CLI over 127.0.0.1 TCP' }
    counts = [ordered]@{ dept = $P['n_dept'][0]; user = $P['n_user'][0]; userRole = $P['n_user_role'][0]; role = $P['n_role'][0]; permission = $P['n_perm'][0]; rolePermission = $P['n_grant'][0]; viewRows = $P['n_view'][0] }
    roles = $roles
    parameters = [ordered]@{
      adminEnabledRoles = $userRoles[$admin].Count
      ledgerActorScope = $lscope; ledgerActorEnabledRoles = $userRoles[$la].Count; ledgerSubtreeDepts = $lnd; ledgerSubtreeUsers = $lnu
      staffEnabledRoles = $userRoles[$sa].Count; staffFirstCode = $scode
      listFilterSubtreeDepts = $fnd; listFilterRoleUsersInSubtree = $fn; listFilterRoleCode = ($roles | Where-Object { $_.id -eq $fr }).code; listPageRows = $listPage.Count; ledgerPageRows = $ledgerPage.Count
      grantRoleCode = $gcode; grantRoleGrants = $grants.Count; allPermissionCodes = $codes.Count
      statements = [ordered]@{}
    }
    explainAnalyzeMs = [ordered]@{}
    timedMs = [ordered]@{}
    rollbackMs = [ordered]@{}
    grantChecksInsideTxn = $V
    integrity = [ordered]@{ before = $before; after = $after; unchanged = $same }
  }
  foreach ($k in $timed.Keys) { $result.parameters.statements[$k] = $timed[$k].Count }
  foreach ($k in $sets.Keys) { $result.parameters.statements[$k] = (AuthSeq $admin).Count + 7 + $sets[$k].Count }
  foreach ($k in $E.Keys) { $result.explainAnalyzeMs[$k] = [ordered]@{ runs = $E[$k].ms; median = (Median $E[$k].ms); max = ($E[$k].ms | Measure-Object -Maximum).Maximum; sql = $explain[$k]; plan = $E[$k].plan } }
  foreach ($k in $T.Keys) { $result.timedMs[$k] = [ordered]@{ runs = $T[$k]; median = (Median $T[$k]); max = ($T[$k] | Measure-Object -Maximum).Maximum } }
  foreach ($k in $RB.Keys) { $result.rollbackMs[$k] = [ordered]@{ median = (Median $RB[$k]); max = ($RB[$k] | Measure-Object -Maximum).Maximum } }
  [IO.File]::WriteAllText($OutFile, ($result | ConvertTo-Json -Depth 8), $utf8)

  Write-Host ''
  Write-Host 'EXPLAIN ANALYZE (top node, ms)      median      max'
  foreach ($k in $result.explainAnalyzeMs.Keys) { '{0,-36} {1,9:N3} {2,9:N3}' -f $k, $result.explainAnalyzeMs[$k].median, $result.explainAnalyzeMs[$k].max | Write-Host }
  Write-Host 'Statement sequences (server wall, ms) median      max'
  foreach ($k in $result.timedMs.Keys) { '{0,-36} {1,9:N3} {2,9:N3}' -f $k, $result.timedMs[$k].median, $result.timedMs[$k].max | Write-Host }
  Write-Host "Grant txn checks: $($V.Count) runs, e.g. $($V[0])"
  Write-Host "Integrity unchanged after rollback: $same"
  Write-Host "Result JSON (ids, counts, timings, plans only): $OutFile"
  if (-not $same) { Write-Host 'WARNING: fingerprints differ - investigate before trusting the database.' -ForegroundColor Red; exit 2 }
}
finally {
  if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
}
