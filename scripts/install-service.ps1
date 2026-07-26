# Installs/reinstalls the EconomicBriefing Windows service via NSSM.
#
#   Run from an ELEVATED PowerShell:
#     powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-service.ps1
#
# Idempotent: stops and removes any existing service first, so re-run it after
# a rebuild or after editing .env (env vars are baked into the service at install time).
#
# ASCII only on purpose: Windows PowerShell 5.1 reads a BOM-less .ps1 as ANSI,
# which mangles non-ASCII literals.
#
# Do NOT add '2>&1' to the nssm calls below. NSSM reports routine conditions on stderr
# ("service is not started" when stopping an already-stopped service), and PowerShell 5.1
# turns a native command's redirected stderr into a terminating NativeCommandError while
# $ErrorActionPreference is 'Stop' - which aborts this script halfway through the install.
# Letting stderr go straight to the console is both safer and more visible.
#
# -ProjectRoot exists for the CI deploy: the workflow checks out into the runner's workspace,
# so the script that runs is NOT the copy inside the production directory. Without it the
# service would be registered against the workspace, which the runner is free to wipe.

param([string]$ProjectRoot)

$ErrorActionPreference = 'Stop'

$ServiceName = 'EconomicBriefing'
if (-not $ProjectRoot) { $ProjectRoot = Split-Path -Parent $PSScriptRoot }
$ProjectRoot = (Resolve-Path $ProjectRoot).Path
$Nssm        = Join-Path $ProjectRoot 'tools\nssm.exe'
$EnvFile     = Join-Path $ProjectRoot '.env'
$LogDir      = Join-Path $ProjectRoot 'logs'
$DbService   = 'postgresql-x64-17'

function Fail($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
        ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Fail 'Administrator rights required. Re-run this script from an elevated PowerShell.'
}
if (-not (Test-Path $Nssm))    { Fail "nssm.exe not found: $Nssm" }
if (-not (Test-Path $EnvFile)) { Fail ".env not found: $EnvFile" }

# --- Resolve the fat JAR (exclude the -plain.jar that bootJar also leaves behind) ---
$Jar = Get-ChildItem (Join-Path $ProjectRoot 'build\libs') -Filter '*.jar' -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike '*-plain.jar' } |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $Jar) { Fail 'No boot JAR in build\libs. Run: .\gradlew.bat clean bootJar' }

# --- Resolve a JDK 21 runtime ---
# Ordered by durability, not convenience: a real installation first and Gradle's provisioned
# toolchain last. Gradle treats ~/.gradle/jdks as a cache it may re-provision or clear, so a
# service pointed at it breaks at the next boot rather than while someone is watching.
# The system JAVA_HOME is a 32-bit JDK 17 here, so every candidate is version-checked.
$GradleJdks = Join-Path $env:USERPROFILE '.gradle\jdks'
$JavaCandidates = @(
    Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'jdk-21*' } | Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' }
    Get-ChildItem 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like '*21*' } |
        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' }
    Get-ChildItem $GradleJdks -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' }
) | Where-Object { $_ -and (Test-Path $_) }

$Java = $null
foreach ($candidate in $JavaCandidates) {
    # 'java -version' prints to stderr; cmd swallows the redirection so PowerShell never sees a
    # native stderr stream (see the NativeCommandError note at the top of this file).
    $version = & cmd /c "`"$candidate`" -version 2>&1"
    if ($version -match 'version "21') { $Java = $candidate; break }
}
if (-not $Java) {
    Fail 'No JDK 21 found. Install it with: winget install EclipseAdoptium.Temurin.21.JDK'
}
if ($Java.StartsWith($GradleJdks, [StringComparison]::OrdinalIgnoreCase)) {
    Write-Host "WARNING: falling back to Gradle's provisioned JDK ($Java)." -ForegroundColor Yellow
    Write-Host "         Gradle may clear that cache. Install Temurin 21 system-wide:" -ForegroundColor Yellow
    Write-Host "         winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Yellow
}

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

# --- Duplicate-run guard: nothing else may hold the app port ---
$Port = 3000
Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
    $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
    if ($p -and $p.Id -ne $PID) {
        Write-Host "Port $Port held by PID $($p.Id) ($($p.ProcessName)); stopping it." -ForegroundColor Yellow
        Stop-Process -Id $p.Id -Force
    }
}

# --- Remove any previous instance so this script is safe to re-run ---
if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    Write-Host "Existing service found; removing."
    & $Nssm stop   $ServiceName          | Out-Null
    & $Nssm remove $ServiceName confirm  | Out-Null
    Start-Sleep -Seconds 2
}

# --- Install ---
# stdout/stderr encoding flags are required, not optional: since Java 19 System.out follows
# the console encoding (cp949 here) when redirected, and file.encoding does not affect it.
# Without them every Korean log line lands as mojibake in the NSSM log files.
$AppArgs = '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 ' +
           "-Duser.timezone=Asia/Seoul -jar `"$($Jar.FullName)`""

& $Nssm install $ServiceName $Java $AppArgs | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "nssm install failed (exit $LASTEXITCODE)" }

& $Nssm set $ServiceName AppDirectory  $ProjectRoot                          | Out-Null
& $Nssm set $ServiceName DisplayName   'Economic Beginner Briefing'          | Out-Null
& $Nssm set $ServiceName Description   'Economic briefing server (Spring Boot, port 3000)' | Out-Null

# LocalSystem runs regardless of whether any user is logged in.
& $Nssm set $ServiceName ObjectName    'LocalSystem'                         | Out-Null

# Delayed auto-start: PostgreSQL and cloudflared must settle first. DependOnService
# guarantees the DB ordering; the delay covers the rest of the boot storm.
& $Nssm set $ServiceName Start          'SERVICE_DELAYED_AUTO_START'         | Out-Null
& $Nssm set $ServiceName DependOnService $DbService                          | Out-Null

# Restart on any unexpected exit. Throttle stops a crash-loop from spinning the CPU:
# NSSM gives up auto-restart if the process dies within AppThrottle ms of starting.
& $Nssm set $ServiceName AppExit Default Restart                             | Out-Null
& $Nssm set $ServiceName AppRestartDelay 5000                                | Out-Null
& $Nssm set $ServiceName AppThrottle     10000                               | Out-Null

# Graceful shutdown: Ctrl-C first (Spring runs its shutdown hooks), kill only if it hangs.
& $Nssm set $ServiceName AppStopMethodSkip    0                              | Out-Null
& $Nssm set $ServiceName AppStopMethodConsole 15000                          | Out-Null

# Logs: append across restarts, rotate daily or at 10 MB.
& $Nssm set $ServiceName AppStdout (Join-Path $LogDir 'service-stdout.log')  | Out-Null
& $Nssm set $ServiceName AppStderr (Join-Path $LogDir 'service-stderr.log')  | Out-Null
& $Nssm set $ServiceName AppStdoutCreationDisposition 4                      | Out-Null
& $Nssm set $ServiceName AppStderrCreationDisposition 4                      | Out-Null
& $Nssm set $ServiceName AppRotateFiles   1                                  | Out-Null
& $Nssm set $ServiceName AppRotateOnline  1                                  | Out-Null
& $Nssm set $ServiceName AppRotateSeconds 86400                              | Out-Null
& $Nssm set $ServiceName AppRotateBytes   10485760                           | Out-Null

# --- Secrets from .env -> service environment (NSSM wants a NUL-free multiline blob) ---
# Same parsing rule the project has always used: only lines starting with '#' are comments,
# so a '#' inside a value (API keys, passwords) survives.
$pairs = Get-Content $EnvFile |
    Where-Object { -not $_.TrimStart().StartsWith('#') } |
    ForEach-Object { if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { "$($Matches[1])=$($Matches[2].Trim())" } }
if (-not $pairs) { Fail "No KEY=VALUE lines parsed from $EnvFile" }
& $Nssm set $ServiceName AppEnvironmentExtra ($pairs -join "`r`n") | Out-Null

# Windows' own recovery, as a backstop for the case NSSM itself dies.
& sc.exe failure $ServiceName reset= 86400 actions= restart/5000/restart/5000/restart/30000 | Out-Null

Write-Host ""
Write-Host "Installed '$ServiceName'" -ForegroundColor Green
Write-Host "  java : $Java"
Write-Host "  jar  : $($Jar.FullName)"
Write-Host "  env  : $($pairs.Count) variables from .env"
Write-Host "  logs : $LogDir\service-stdout.log"
Write-Host ""

Start-Service $ServiceName
Write-Host "Service started. Status: $((Get-Service $ServiceName).Status)"
