<#
.SYNOPSIS
  在本机独立全量核对库测量只读 SQL 成本，不执行授权写入。
.DESCRIPTION
  PowerShell 7+ 与 MySQL 8.4 CLI；Windows/Linux 均可，mysql 可在 PATH 或由 -MySql 指定。
  凭据仅通过事先配置的 mysql login-path 提供，本脚本不读取口令、不生成临时口令文件。
  只连接本机 rbac_verify_full，在 READ ONLY 事务中测查询及 EXPLAIN ANALYZE。
  输出包含计数、代理ID、SQL和计划，不保存查询的员工字段或密码摘要。
  SELECT * 的结果仍经过客户端内存后丢弃；仅在受控副本上运行，不读取生产资料作演示。
  此工具不测 Java/HTTP、写入或 COMMIT，也不自动宣布任何端到端性能指标通过。
#>
#Requires -Version 7.0
[CmdletBinding()]
param(
  [string]$OutFile = '',
  [string]$MySql = 'mysql',
  [ValidatePattern('^[A-Za-z0-9_.-]{1,64}$')][string]$LoginPath = 'rbac-verify',
  [ValidateSet('127.0.0.1', 'localhost', '::1')][string]$HostName = '127.0.0.1',
  [ValidateRange(1, 65535)][int]$Port = 3306,
  [ValidateRange(1, 101)][int]$Runs = 5,
  [ValidateRange(1, 300)][int]$ProcessTimeoutSeconds = 60,
  [ValidateRange(100, 300000)][int]$QueryTimeoutMs = 30000
)

function Get-Median([ValidateNotNullOrEmpty()][double[]]$Values) {
  foreach ($value in $Values) {
    if ([double]::IsNaN($value) -or [double]::IsInfinity($value) -or $value -lt 0) {
      throw 'Invalid timing sample.'
    }
  }
  $sorted = @($Values | Sort-Object)
  $middle = [int][math]::Floor($sorted.Count / 2)
  if ($sorted.Count % 2) { return $sorted[$middle] }
  return ($sorted[$middle - 1] + $sorted[$middle]) / 2
}

function Assert-ReadOnlySql([string]$Sql) {
  # 本守卫检查本脚本自己的固定 SQL 构造器，不作为接收外部任意 SQL 的防火墙。
  # 除只读事务和会话设置外，禁止数据/结构写入、文件输出和 mysql 客户端命令。
  if ([string]::IsNullOrWhiteSpace($Sql) -or
      $Sql -match '(?i)\b(INSERT|UPDATE|DELETE|REPLACE|MERGE|CREATE|ALTER|DROP|TRUNCATE|CALL|LOAD|GRANT|REVOKE|OUTFILE|DUMPFILE|COMMIT)\b' -or
      $Sql -match '(?i)\bSET\s+(GLOBAL|PERSIST)\b' -or
      $Sql -match '(?m)^\s*(\\|source\b|system\b|!|#|--)' -or $Sql.Contains('/*')) {
    throw 'Only the fixed read-only profile SQL is allowed.'
  }
  foreach ($statement in $Sql.Split(';')) {
    if ($statement.Trim() -and $statement.Trim() -notmatch
        '(?is)^(SELECT\b|WITH\s+RECURSIVE\b|EXPLAIN\s+ANALYZE\s+FORMAT=TREE\s+(SELECT|WITH)\b|SET\s+(@|SESSION\b)|START\s+TRANSACTION\s+READ\s+ONLY\s*$|ROLLBACK\s*$)') {
      throw 'Unexpected statement in read-only profile.'
    }
  }
}

function Invoke-ProfileClient {
  param([string]$Executable, [string[]]$Arguments, [string]$Sql,
        [ValidateRange(1, 300)][int]$TimeoutSeconds = 60)
  Assert-ReadOnlySql $Sql
  $psi = [Diagnostics.ProcessStartInfo]::new()
  $psi.FileName = $Executable
  foreach ($argument in $Arguments) { $psi.ArgumentList.Add($argument) }
  $psi.UseShellExecute = $false
  $psi.RedirectStandardInput = $true
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true
  $psi.StandardInputEncoding = [Text.UTF8Encoding]::new($false)
  $psi.StandardOutputEncoding = [Text.Encoding]::UTF8
  $psi.StandardErrorEncoding = [Text.Encoding]::UTF8
  # 不继承环境变量口令；连接凭据必须来自指定 login-path。
  [void]$psi.Environment.Remove('MYSQL_PWD')
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $psi
  $started = $false
  try {
    if (-not $process.Start()) { throw 'MySQL client did not start.' }
    $started = $true
    $outputTask = $process.StandardOutput.ReadToEndAsync()
    $errorTask = $process.StandardError.ReadToEndAsync()
    # 异步写入也计入进程期限，防止子进程不读 stdin 时无限阻塞。
    $writeTask = $process.StandardInput.WriteAsync($Sql)
    $clock = [Diagnostics.Stopwatch]::StartNew()
    if (-not $writeTask.Wait($TimeoutSeconds * 1000)) { throw 'MySQL client timed out.' }
    $process.StandardInput.Close()
    $remaining = [math]::Max(0, $TimeoutSeconds * 1000 - $clock.ElapsedMilliseconds)
    if (-not $process.WaitForExit([int]$remaining)) { throw 'MySQL client timed out.' }
    $remaining = [math]::Max(1, $TimeoutSeconds * 1000 - $clock.ElapsedMilliseconds)
    if (-not [Threading.Tasks.Task]::WhenAll([Threading.Tasks.Task[]]@($outputTask, $errorTask)).Wait([int]$remaining)) { throw 'MySQL client output timed out.' }
    $output = $outputTask.GetAwaiter().GetResult()
    $errorText = $errorTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) {
      # 客户端错误可包含 SQL 或资料；只保留数值错误号，不打印原始 stderr。
      $code = [regex]::Match($errorText, '(?i)\bERROR\s+(\d{3,5})\b')
      $suffix = if ($code.Success) { ', MySQL code ' + $code.Groups[1].Value } else { '' }
      throw "MySQL client failed (exit $($process.ExitCode)$suffix)."
    }
    foreach ($line in $output -split '\r?\n') {
      if ($line -match '^##(?:P|ROLE|LP|LG|UR|E|T)\|' -or $line.StartsWith('->')) { $line }
    }
  }
  finally {
    # 只终止本函数创建的子进程；关闭连接也会结束只读事务。
    if ($started -and -not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
    $process.Dispose()
  }
}

function ConvertFrom-TimingLines {
  param([string[]]$Lines, [string[]]$TimedNames, [string[]]$ExplainNames,
        [ValidateRange(1, 101)][int]$Runs)
  $timings = [ordered]@{}; $plans = [ordered]@{}; $seen = @{}; $pending = $null
  foreach ($key in $TimedNames) { $timings[$key] = [Collections.Generic.List[double]]::new() }
  foreach ($key in $ExplainNames) { $plans[$key] = [ordered]@{ samples = [Collections.Generic.List[double]]::new(); plan = '' } }
  foreach ($line in $Lines) {
    if ($line.StartsWith('->')) {
      if (-not $pending) { throw 'Unexpected EXPLAIN output.' }
      $match = [regex]::Match($line, 'actual time=([0-9.eE+-]+)\.\.([0-9.eE+-]+)')
      if (-not $match.Success) { throw 'EXPLAIN output has no usable timing.' }
      $value = [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture)
      if ($pending.iteration -gt 0) { $plans[$pending.name].samples.Add($value) }
      $plans[$pending.name].plan = $line.Replace('\n', "`n")
      $pending = $null
      continue
    }
    $fields = $line.Split('|')
    if ($fields.Count -lt 3 -or $fields[0] -notin @('##E', '##T')) { throw 'Unexpected timing marker.' }
    $key = $fields[1]; $iteration = 0
    if (-not [int]::TryParse($fields[2], [ref]$iteration) -or $iteration -lt 0 -or $iteration -gt $Runs) {
      throw 'Invalid timing iteration.'
    }
    $identity = "$($fields[0])|$key|$iteration"
    if ($seen.ContainsKey($identity) -or $pending) { throw 'Duplicate or incomplete timing marker.' }
    $seen[$identity] = $true
    if ($fields[0] -eq '##E') {
      if ($fields.Count -ne 3 -or -not $plans.Contains($key)) { throw 'Unknown EXPLAIN marker.' }
      $pending = @{ name = $key; iteration = $iteration }
    } else {
      if ($fields.Count -ne 4 -or -not $timings.Contains($key) -or $fields[3] -notmatch '^\d+$') {
        throw 'Invalid statement timing marker.'
      }
      if ($iteration -gt 0) { $timings[$key].Add([double]$fields[3] / 1000) }
    }
  }
  if ($pending) { throw 'Missing EXPLAIN result.' }
  $result = [ordered]@{ statementSequenceMs = [ordered]@{}; explainAnalyzeMs = [ordered]@{} }
  foreach ($key in $TimedNames) {
    if ($timings[$key].Count -ne $Runs -or -not $seen.ContainsKey("##T|$key|0")) { throw "Incomplete samples: $key" }
    $result.statementSequenceMs[$key] = [ordered]@{ runs = @($timings[$key]); median = Get-Median $timings[$key]; max = ($timings[$key] | Measure-Object -Maximum).Maximum }
  }
  foreach ($key in $ExplainNames) {
    if ($plans[$key].samples.Count -ne $Runs -or -not $seen.ContainsKey("##E|$key|0")) { throw "Incomplete EXPLAIN samples: $key" }
    $result.explainAnalyzeMs[$key] = [ordered]@{ runs = @($plans[$key].samples); median = Get-Median $plans[$key].samples; max = ($plans[$key].samples | Measure-Object -Maximum).Maximum; plan = $plans[$key].plan }
  }
  return $result
}

function Quote-ProfileId([string]$Id) {
  if ($Id -notmatch '^[1-9][0-9]*$') { throw 'Profile requires a positive numeric surrogate ID.' }
  return "'$Id'"
}
function SqlFindUser($Id) { "SELECT * FROM sys_user WHERE id=$(Quote-ProfileId $Id);" }
function SqlRoleIds($Id, [bool]$EnabledOnly) {
  $sql = "SELECT r.id FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=$(Quote-ProfileId $Id)"
  if ($EnabledOnly) { $sql += ' AND r.status=1' }
  $sql + ' ORDER BY r.id;'
}
function SqlGrants($Id) { "SELECT rp.role_id,p.code,rp.data_scope FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.role_id=$(Quote-ProfileId $Id) ORDER BY p.id;" }
function SqlDept($Id) { "SELECT * FROM sys_dept WHERE id=$(Quote-ProfileId $Id);" }
function SqlView($Id, [ValidateSet('sys:f339:read', 'hr:f046:read')][string]$Code) {
  "SELECT data_scope FROM v_user_permission WHERE user_id=$(Quote-ProfileId $Id) AND permission_code='$Code';"
}
$SqlDeptAll = 'SELECT * FROM sys_dept ORDER BY sort_no,id;'
$SqlUserAll = 'SELECT * FROM sys_user ORDER BY id;'

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
SELECT CONCAT('##LG|',u.id,'|',IFNULL(u.dept_id,'')) FROM sys_user u WHERE (@lscope='ALL' OR u.dept_id IN (SELECT id FROM t)) ORDER BY u.id LIMIT 20;
SELECT CONCAT('##UR|',ur.user_id,'|',GROUP_CONCAT(r.id ORDER BY r.id)) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id AND r.status=1
 WHERE ur.user_id IN (@ad,@la,@sa) GROUP BY ur.user_id;
"@

function SqlRoleUsers($Id) { "SELECT user_id FROM sys_user_role WHERE role_id=$(Quote-ProfileId $Id) ORDER BY user_id;" }

function New-ReadOnlyEnvelope([string]$Body, [ValidateRange(100, 300000)][int]$TimeoutMs) {
  $sql = "SET SESSION MAX_EXECUTION_TIME=$TimeoutMs;`nSET SESSION innodb_lock_wait_timeout=5;`nSET SESSION TRANSACTION READ ONLY;`nSTART TRANSACTION READ ONLY;`n" + $Body + "`nROLLBACK;`n"
  Assert-ReadOnlySql $sql
  return $sql
}

function New-TimingSql {
  param([System.Collections.IDictionary]$Explain, [System.Collections.IDictionary]$Timed,
        [ValidateRange(1, 101)][int]$Count)
  $builder = [Text.StringBuilder]::new()
  foreach ($key in $Explain.Keys) {
    if ($key -notmatch '^[a-z0-9_]+$') { throw 'Invalid profile name.' }
    for ($index = 0; $index -le $Count; $index++) {
      [void]$builder.AppendLine("SELECT '##E|$key|$index';")
      [void]$builder.AppendLine('EXPLAIN ANALYZE FORMAT=TREE ' + $Explain[$key])
    }
  }
  foreach ($key in $Timed.Keys) {
    if ($key -notmatch '^[a-z0-9_]+$') { throw 'Invalid profile name.' }
    for ($index = 0; $index -le $Count; $index++) {
      [void]$builder.AppendLine('SET @t0=NOW(6);')
      foreach ($statement in $Timed[$key]) { [void]$builder.AppendLine($statement) }
      [void]$builder.AppendLine("SELECT CONCAT('##T|$key|$index|',TIMESTAMPDIFF(MICROSECOND,@t0,NOW(6)));")
    }
  }
  Assert-ReadOnlySql $builder.ToString()
  return $builder.ToString()
}

# 点加载只定义函数和固定查询，便于无数据库的独立进程/解析测试；不触发连接或凭据读取。
if ($MyInvocation.InvocationName -eq '.') { return }
$ErrorActionPreference = 'Stop'
try {
  $database = 'rbac_verify_full'
  $application = Get-Command -Name $MySql -CommandType Application -ErrorAction Stop | Select-Object -First 1
  # --no-defaults 必须靠前；MySQL 仍读取明确 login-path，避免继承普通配置中的 init-command。
  $clientArguments = @('--no-defaults', "--login-path=$LoginPath", '--batch', '--skip-column-names',
    '--default-character-set=utf8mb4', '--protocol=TCP', "--host=$HostName", "--port=$Port",
    '--connect-timeout=5', '--local-infile=0', '--skip-auto-rehash', $database)
  function Invoke-Sql([string]$Body) {
    Invoke-ProfileClient -Executable $application.Source -Arguments $clientArguments `
      -Sql (New-ReadOnlyEnvelope $Body $QueryTimeoutMs) -TimeoutSeconds $ProcessTimeoutSeconds
  }
  $properties = @{}; $roleSummary = @(); $listPage = @(); $ledgerPage = @(); $userRoles = @{}
  foreach ($line in (Invoke-Sql $Setup)) {
    $fields = $line.Split('|')
    switch ($fields[0]) {
      '##P' { if ($properties.ContainsKey($fields[1])) { throw 'Duplicate setup property.' }; $properties[$fields[1]] = @($fields[2..($fields.Count - 1)]) }
      '##ROLE' {
        if ($fields.Count -ne 7 -or $fields[2] -notmatch '^[A-Z][A-Z0-9_]{1,31}$') { throw 'Invalid role metadata.' }
        $roleSummary += [ordered]@{ id = $fields[1]; code = $fields[2]; version = $fields[3]; status = $fields[4]; grants = [int]$fields[5]; users = [int]$fields[6] }
      }
      '##LP' { if ($fields.Count -ne 3) { throw 'Invalid user-page metadata.' }; $listPage += ,@($fields[1], $fields[2]) }
      '##LG' { if ($fields.Count -ne 3) { throw 'Invalid ledger-page metadata.' }; $ledgerPage += ,@($fields[1], $fields[2]) }
      '##UR' { $userRoles[$fields[1]] = $fields[2].Split(',') }
      default { throw 'Unexpected setup marker.' }
    }
  }
  foreach ($key in @('db', 'version', 'admin', 'staff', 'ledger', 'list', 'n_dept', 'n_user', 'n_role', 'n_perm', 'n_grant')) {
    if (-not $properties.ContainsKey($key)) { throw "Missing setup property: $key" }
  }
  if ($properties['db'][0] -ne $database) { throw 'Unexpected database; no profiling performed.' }
  foreach ($key in @('n_dept', 'n_user', 'n_role', 'n_perm', 'n_grant')) {
    if ($properties[$key][0] -notmatch '^[1-9][0-9]*$') { throw 'The verification database must contain a non-empty imported catalog.' }
  }
  $admin = $properties['admin'][0]; $staff = $properties['staff'][0]
  $ledgerActor, $actorDepartment, $ledgerScope = $properties['ledger'][0..2]
  $filterDepartment, $filterRole = $properties['list'][0..1]
  foreach ($id in @($admin, $staff, $ledgerActor, $actorDepartment, $filterDepartment, $filterRole)) { [void](Quote-ProfileId $id) }
  if ($ledgerScope -notin @('ALL', 'DEPT', 'SELF') -or -not $listPage.Count -or -not $ledgerPage.Count) { throw 'No usable page/scope fixtures.' }
  foreach ($id in @($admin, $staff, $ledgerActor)) {
    if (-not $userRoles.ContainsKey($id) -or -not $userRoles[$id].Count) { throw 'Missing enabled-role fixture.' }
    foreach ($role in $userRoles[$id]) { [void](Quote-ProfileId $role) }
  }
  function AuthSeq($Id) {
    $sequence = @((SqlFindUser $Id), (SqlRoleIds $Id $true))
    foreach ($role in $userRoles[$Id]) { $sequence += SqlGrants $role }
    $sequence
  }
  $adminRole = @($roleSummary | Where-Object { $_.code -eq 'ADMIN' })
  if ($adminRole.Count -ne 1) { throw 'Expected exactly one ADMIN role fixture.' }
  $explain = [ordered]@{
    view_admin = SqlView $admin 'sys:f339:read'
    view_ledger_actor = SqlView $ledgerActor 'hr:f046:read'
    mapper_find_user = SqlFindUser $ledgerActor
    mapper_admin_grants = SqlGrants $adminRole[0].id
    mapper_departments = $SqlDeptAll
    mapper_users = $SqlUserAll
  }
  $timed = [ordered]@{ require_admin_sql = AuthSeq $admin; require_staff_sql = AuthSeq $staff; require_ledger_actor_sql = AuthSeq $ledgerActor }
  $list = (AuthSeq $admin) + @((SqlFindUser $admin), $SqlDeptAll, (SqlRoleUsers $filterRole), $SqlUserAll)
  foreach ($row in $listPage) { $list += SqlDept $row[1]; $list += SqlRoleIds $row[0] $false }
  $timed['users_page20_sql'] = $list
  $ledger = (AuthSeq $ledgerActor) + @((SqlFindUser $ledgerActor), $SqlUserAll)
  if ($ledgerScope -eq 'DEPT') { $ledger += $SqlDeptAll }
  # NULL 部门仍属于可见页面行，但 Mapper 会跳过该部门查询；不以 CONCAT(NULL) 隐式漏掉此行。
  foreach ($row in $ledgerPage) { if ($row[1]) { $ledger += SqlDept $row[1] } }
  $timed['ledger_page20_sql'] = $ledger
  Write-Host "Read-only SQL profile: 1 warm-up + $Runs measured samples. No DML or COMMIT."
  $body = New-TimingSql -Explain $explain -Timed $timed -Count $Runs
  $measurements = ConvertFrom-TimingLines -Lines @(Invoke-Sql $body) -TimedNames @($timed.Keys) -ExplainNames @($explain.Keys) -Runs $Runs
  $result = [ordered]@{
    utilityVersion = '2-readonly'
    measuredAt = [DateTimeOffset]::Now.ToString('o')
    scriptSha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
    scope = 'SQL/CLI only; warm cache; no JVM, HTTP, DML, COMMIT or end-to-end acceptance'
    readOnly = $true; measuredRuns = $Runs
    environment = [ordered]@{ os = [Runtime.InteropServices.RuntimeInformation]::OSDescription; logicalProcessors = [Environment]::ProcessorCount; mysql = $properties['version'][0]; host = $HostName; port = $Port; database = $database }
    counts = [ordered]@{ department = $properties['n_dept'][0]; user = $properties['n_user'][0]; role = $properties['n_role'][0]; permission = $properties['n_perm'][0]; grants = $properties['n_grant'][0] }
    parameters = [ordered]@{ ledgerScope = $ledgerScope; ledgerPageRows = $ledgerPage.Count; usersPageRows = $listPage.Count; statements = [ordered]@{} }
    statementSequenceMs = $measurements.statementSequenceMs
    explainAnalyzeMs = $measurements.explainAnalyzeMs
  }
  foreach ($key in $timed.Keys) { $result.parameters.statements[$key] = $timed[$key].Count }
  if (-not $OutFile) { $OutFile = Join-Path ([IO.Path]::GetTempPath()) ('rbac-read-perf-' + [guid]::NewGuid().ToString('N') + '.json') }
  $path = [IO.Path]::GetFullPath($OutFile)
  if ([IO.Path]::GetExtension($path) -ne '.json') { throw 'Output must be a new .json file.' }
  # CreateNew 防止覆盖已有证据或其他文件；不把输出写入凭据文件。
  $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($result | ConvertTo-Json -Depth 8) + "`n")
    $stream.Write($bytes, 0, $bytes.Length)
  } finally { $stream.Dispose() }
  Write-Host "Read-only measurements saved: $path"
  Write-Host 'No performance acceptance verdict is generated; review samples and deployment context.'
} catch {
  Write-Error -Message $_.Exception.Message -ErrorAction Continue
  exit 1
}
