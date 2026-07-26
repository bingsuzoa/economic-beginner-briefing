# Self-check for scripts\health-check.ps1. No admin, no services, no framework:
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-health-check.ps1
#
# It exists because of one branch: health-check.ps1 lets a 503 pass when the app's only
# complaint is a stale pipeline run. Get that wrong in either direction and a deploy either
# rolls back a perfectly good release, or declares a broken one healthy. The canned responses
# below pin both directions down.
#
# A separate process serves them, not a background job - Stop-Job deadlocks against a
# listener parked in AcceptTcpClient.
#
# ASCII only on purpose: Windows PowerShell 5.1 reads a BOM-less .ps1 as ANSI.

$ErrorActionPreference = 'Stop'

$Target   = Join-Path $PSScriptRoot 'health-check.ps1'
$Tmp      = Join-Path $env:TEMP "briefing-healthcheck-test-$PID"
$StubFile = Join-Path $Tmp 'stub.ps1'
$BodyFile = Join-Path $Tmp 'body.json'
$port     = 3990
$fails    = 0
$total    = 0

New-Item -ItemType Directory $Tmp -Force | Out-Null
@'
param([int]$Port, [int]$Code, [string]$Reason, [string]$BodyFile)
$body  = Get-Content $BodyFile -Raw
$l     = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
$l.Start()
$bytes = [Text.Encoding]::UTF8.GetBytes($body)
$head  = [Text.Encoding]::UTF8.GetBytes(
    "HTTP/1.1 $Code $Reason`r`nContent-Type: application/json`r`nContent-Length: $($bytes.Length)`r`nConnection: close`r`n`r`n")
while ($true) {
    $c = $l.AcceptTcpClient()
    $s = $c.GetStream()
    $buf = New-Object byte[] 4096
    $null = $s.Read($buf, 0, $buf.Length)
    $s.Write($head, 0, $head.Length); $s.Write($bytes, 0, $bytes.Length); $s.Flush(); $c.Close()
}
'@ | Set-Content $StubFile -Encoding ASCII

function Check($label, $code, $reason, $body, $expected, $extra) {
    $script:port++
    $script:total++
    Set-Content $BodyFile -Value $body -Encoding ASCII -NoNewline

    $stub = Start-Process powershell -PassThru -WindowStyle Hidden -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $StubFile,
        '-Port', $script:port, '-Code', $code, '-Reason', $reason, '-BodyFile', $BodyFile)
    Start-Sleep -Milliseconds 1200
    try {
        $a = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Target,
               '-Url', "http://127.0.0.1:$($script:port)/api/health/briefing",
               '-TimeoutSec', '4', '-IntervalSec', '1') + $extra
        $out = & powershell @a
        $rc  = $LASTEXITCODE
    } finally { Stop-Process -Id $stub.Id -Force -ErrorAction SilentlyContinue }

    if ($rc -eq $expected) {
        "[PASS] {0,-42} exit={1}" -f $label, $rc
    } else {
        "[FAIL] {0,-42} expected={1} got={2}" -f $label, $expected, $rc
        $out | ForEach-Object { "         $_" }
        $script:fails++
    }
}

$fresh = '{"status":"UP","dbConnected":true,"reasons":[]}'
$stale = '{"status":"DOWN","dbConnected":true,"reasons":["no successful run in 245m (limit 180m)"]}'
$never = '{"status":"DOWN","dbConnected":true,"reasons":["no successful pipeline run recorded yet"]}'
$mixed = '{"status":"DOWN","dbConnected":true,"reasons":["no successful run in 245m (limit 180m)","scheduler cron is invalid"]}'
$dbout = '{"status":"DOWN","dbConnected":false,"reasons":["database unreachable: JDBCConnectionException"]}'

try {
    Check '200 UP'                          200 'OK'                    $fresh 0 @()
    Check '503 stale-run only  -> tolerate' 503 'Service Unavailable'   $stale 0 @()
    Check '503 never-ran       -> tolerate' 503 'Service Unavailable'   $never 0 @()
    Check '503 stale + bad cron-> fail'     503 'Service Unavailable'   $mixed 1 @()
    Check '503 db unreachable  -> fail'     503 'Service Unavailable'   $dbout 1 @()
    Check '503 stale + -Strict -> fail'     503 'Service Unavailable'   $stale 1 @('-Strict')
    Check '500                 -> fail'     500 'Internal Server Error' '{}'   1 @()
} finally {
    Remove-Item $Tmp -Recurse -Force -ErrorAction SilentlyContinue
}

""
if ($fails) { "RESULT: $fails of $total FAILED"; exit 1 } else { "RESULT: all $total passed"; exit 0 }
