#Requires -Version 7.0
# 无 MySQL/原始表/外部凭据依赖；真正启动可控假子进程，检查边界、超时和输出去敏。
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $repoRoot 'scripts/measure_full_db_perf.ps1')
$pwsh = (Get-Process -Id $PID).Path
$fixture = Join-Path $PSScriptRoot 'fixtures/profile-client.ps1'
$script:passed = 0
function Assert-Condition([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw $Message }
}
function Assert-Fails([scriptblock]$Action, [string]$Pattern) {
  $caught = $null
  try { & $Action | Out-Null } catch { $caught = $_.Exception.Message }
  Assert-Condition ($null -ne $caught -and $caught -match $Pattern) 'Expected controlled rejection.'
  return $caught
}
function Case([string]$Name, [scriptblock]$Action) {
  & $Action
  $script:passed++
  Write-Host "PASS $Name"
}
$explain = [ordered]@{ unit = 'SELECT 1;' }
$timed = [ordered]@{ unit = @('SELECT 1;') }
$body = New-TimingSql -Explain $explain -Timed $timed -Count 2
$sql = New-ReadOnlyEnvelope $body 1000

Case 'read-only envelope and builders reject writes and client commands' {
  Assert-ReadOnlySql $Setup
  Assert-ReadOnlySql $sql
  Assert-Condition ($sql -match 'START TRANSACTION READ ONLY') 'Read-only transaction missing.'
  foreach ($bad in @('DELETE FROM sys_user;', 'EXPLAIN ANALYZE FORMAT=TREE UPDATE sys_role SET status=0;',
      'SELECT 1 INTO OUTFILE ''x'';', 'SET GLOBAL x=1;', 'source other.sql', 'COMMIT;', '/* hidden */ SELECT 1;')) {
    [void](Assert-Fails { Assert-ReadOnlySql $bad } 'read-only|Unexpected')
  }
}
Case 'numeric ID validation prevents injected SQL fragments' {
  Assert-Condition ((SqlFindUser '12') -eq "SELECT * FROM sys_user WHERE id='12';") 'ID substitution changed.'
  [void](Assert-Fails { SqlFindUser '12; SELECT 2' } 'numeric')
}
Case 'run bounds reject empty and unbounded sample sets' {
  [void](Assert-Fails { New-TimingSql -Explain $explain -Timed $timed -Count 0 } 'range|greater|validate')
  [void](Assert-Fails { New-TimingSql -Explain $explain -Timed $timed -Count 102 } 'range|greater|validate')
}
Case 'median handles even and odd samples and rejects negative values' {
  Assert-Condition ((Get-Median @(1, 9)) -eq 5) 'Even median must average middle values.'
  Assert-Condition ((Get-Median @(9, 1, 3)) -eq 3) 'Odd median incorrect.'
  [void](Assert-Fails { Get-Median @(-1, 2) } 'Invalid')
}
Case 'real fake-client process filters data and parses exact measured samples' {
  $lines = @(Invoke-ProfileClient -Executable $pwsh -Arguments @('-NoLogo', '-NoProfile', '-File', $fixture, 'success') -Sql $sql -TimeoutSeconds 10)
  Assert-Condition (($lines -join "`n") -notmatch 'SYNTHETIC_EMPLOYEE|PASSWORD_DIGEST') 'Data rows escaped filtering.'
  $parsed = ConvertFrom-TimingLines -Lines $lines -TimedNames @('unit') -ExplainNames @('unit') -Runs 2
  Assert-Condition ($parsed.statementSequenceMs.unit.median -eq 1.5) 'Warm-up was not excluded.'
  Assert-Condition ($parsed.explainAnalyzeMs.unit.median -eq 0.0001) 'Scientific notation was parsed incorrectly.'
  Assert-Condition ($parsed.statementSequenceMs.unit.runs.Count -eq 2) 'Measured count incorrect.'
}
Case 'missing, duplicate, malformed and unknown markers cannot yield a report' {
  foreach ($lines in @(
      ,@('##T|unit|0|1', '##T|unit|1|1000'),
      ,@('##T|unit|0|1', '##T|unit|0|2'),
      ,@('##E|plan|0', '-> no timing fields'),
      ,@('##T|unknown|0|1'))) {
    [void](Assert-Fails { ConvertFrom-TimingLines -Lines $lines -TimedNames @('unit') -ExplainNames @() -Runs 2 } 'Incomplete|Duplicate|Unknown|Invalid')
  }
  [void](Assert-Fails { ConvertFrom-TimingLines -Lines @('##E|plan|0', '-> no timing fields') -TimedNames @() -ExplainNames @('plan') -Runs 2 } 'no usable timing')
  [void](Assert-Fails { ConvertFrom-TimingLines -Lines @('##T|unit|3|1000') -TimedNames @('unit') -ExplainNames @() -Runs 2 } 'Invalid timing iteration')
  [void](Assert-Fails { ConvertFrom-TimingLines -Lines @('##T|unit|0|not-a-time') -TimedNames @('unit') -ExplainNames @() -Runs 2 } 'Invalid statement timing')
}
Case 'client errors retain only exit/code and redact stderr data' {
  $message = Assert-Fails { Invoke-ProfileClient -Executable $pwsh -Arguments @('-NoLogo', '-NoProfile', '-File', $fixture, 'fail') -Sql 'SELECT 1;' -TimeoutSeconds 10 } 'exit 7.*1045'
  Assert-Condition ($message -notmatch 'SYNTHETIC_CREDENTIAL|EMPLOYEE|DIGEST') 'Client diagnostic leaked data.'
}
Case 'hung child is bounded and terminated' {
  $watch = [Diagnostics.Stopwatch]::StartNew()
  [void](Assert-Fails { Invoke-ProfileClient -Executable $pwsh -Arguments @('-NoLogo', '-NoProfile', '-File', $fixture, 'hang') -Sql 'SELECT 1;' -TimeoutSeconds 1 } 'timed out')
  Assert-Condition ($watch.Elapsed.TotalSeconds -lt 8) 'Timeout did not bound child lifetime.'
}
Write-Host "Read-only performance utility tests passed: $script:passed cases; no database used."
