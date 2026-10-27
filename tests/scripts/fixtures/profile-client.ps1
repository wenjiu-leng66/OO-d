# 仅供独立工具测试的假客户端：读取 SQL 数据但不执行，不连接任何数据库。
$mode = $args[0]
$inputSql = [Console]::In.ReadToEnd()
[Console]::Out.WriteLine('10001`tSYNTHETIC_EMPLOYEE_FIELD`tSYNTHETIC_PASSWORD_DIGEST')
if ($mode -eq 'fail') {
  [Console]::Error.WriteLine('ERROR 1045 (28000): SYNTHETIC_CREDENTIAL must never be surfaced')
  exit 7
}
if ($mode -eq 'hang') { Start-Sleep -Seconds 30; exit 0 }
foreach ($line in $inputSql -split '\r?\n') {
  if ($line -match "^SELECT '##E\|([^']+)';$") {
    [Console]::Out.WriteLine('##E|' + $Matches[1])
    [Console]::Out.WriteLine('-> Synthetic scan (actual time=0.0001..100e-6 rows=1 loops=1)')
  } elseif ($line -match "^SELECT CONCAT\('##T\|([^']+)'\s*,") {
    $marker = $Matches[1].TrimEnd('|')
    $iteration = [int]$marker.Split('|')[-1]
    $microseconds = if ($iteration -eq 0) { 999999 } else { $iteration * 1000 }
    [Console]::Out.WriteLine("##T|$marker|$microseconds")
  }
}
