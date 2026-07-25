# Economic Briefing server launcher (used by the EconomicBriefingServer scheduled task).
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-server.ps1
#
# ASCII only on purpose: Windows PowerShell 5.1 reads a .ps1 without a BOM as ANSI,
# which mangles non-ASCII literals. Keeping this file ASCII removes that dependency.
#
# Secrets come from .env in the project root:
#   OPENAI_API_KEY, ADMIN_TOKEN, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Jar         = Join-Path $ProjectRoot 'build\libs\economic-briefing-0.1.0.jar'
$EnvFile     = Join-Path $ProjectRoot '.env'
$LogDir      = Join-Path $ProjectRoot 'logs'

# The scheduler may start us from a different directory.
Set-Location $ProjectRoot

if (-not (Test-Path $Jar))     { throw "JAR not found: $Jar  (run .\gradlew build first)" }
if (-not (Test-Path $EnvFile)) { throw ".env not found: $EnvFile" }
if (-not (Test-Path $LogDir))  { New-Item -ItemType Directory -Path $LogDir | Out-Null }

# Load .env (KEY=VALUE, skipping comments and blanks)
Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*#') { return }
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
    }
}

# JDK 21 provisioned by Gradle. The system JAVA_HOME is 17, so pin it here.
$Jdk21 = Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
if (Test-Path $Jdk21) {
    $env:JAVA_HOME = $Jdk21
} elseif (-not $env:JAVA_HOME) {
    throw "JDK 21 not found at $Jdk21 and JAVA_HOME is unset"
}
$Java = Join-Path $env:JAVA_HOME 'bin\java.exe'

# One log file per day; restarts on the same day append.
$LogFile = Join-Path $LogDir ("server-{0}.log" -f (Get-Date -Format 'yyyy-MM-dd'))

# Drop logs older than 30 days.
Get-ChildItem $LogDir -Filter 'server-*.log' -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } |
    Remove-Item -Force -ErrorAction SilentlyContinue

# Append the banner as raw UTF-8 bytes. Out-File would add a BOM mid-file and
# PowerShell redirection would re-encode to UTF-16, both of which corrupt the log.
$banner = "=== server start {0} ===`r`n" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
[System.IO.File]::AppendAllText($LogFile, $banner, [System.Text.UTF8Encoding]::new($false))

# stdout.encoding / stderr.encoding are required, not optional: since Java 19 System.out uses
# the console encoding (cp949 here) when redirected, and file.encoding does not affect it.
# Without these two, every Korean log line lands as mojibake.
# cmd does the redirection so PowerShell never re-encodes the stream.
& cmd /c "`"$Java`" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Duser.timezone=Asia/Seoul -jar `"$Jar`" >> `"$LogFile`" 2>&1"
