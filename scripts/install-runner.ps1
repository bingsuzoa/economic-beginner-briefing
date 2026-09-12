# Installs the GitHub Actions self-hosted runner as a Windows service.
#
#   Run from an ELEVATED PowerShell:
#     powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-runner.ps1 -Token AXXXXX...
#
# The token is the short-lived REGISTRATION token from
#   GitHub -> repo -> Settings -> Actions -> Runners -> New self-hosted runner
# (the value in the ./config.cmd --token line). It expires in one hour and is not a PAT.
# Nothing writes it to disk or to the log.
#
# The runner service runs as LocalSystem on purpose. The deploy it performs calls
# Stop-Service / nssm / sc.exe, all of which need administrator rights, and the runner's
# own default account (NETWORK SERVICE) does not have them. LocalSystem also needs no
# stored password and runs with no user logged in - the same reason the app service uses it.
#
# SECURITY: this repository is public. Never add a pull_request trigger to a workflow that
# targets this runner - it would let anyone's fork run code as SYSTEM on this machine.
# The deploy workflow triggers on push to main only, which only collaborators can do.
#
# ASCII only on purpose: Windows PowerShell 5.1 reads a BOM-less .ps1 as ANSI.

param(
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$RepoUrl   = 'https://github.com/bingsuzoa/economic-beginner-briefing',
    [string]$RunnerDir = 'C:\actions-runner',
    [string]$Name      = "$env:COMPUTERNAME-briefing",
    [string]$Labels    = 'economic-briefing'
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Fail($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
        ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Fail 'Administrator rights required. Re-run this script from an elevated PowerShell.'
}

# --- Resolve the current runner release ------------------------------------------------
# Pinning a version here would rot; the runner also self-updates after registration anyway.
Write-Host "Resolving latest runner release ..."
$tag     = (Invoke-RestMethod 'https://api.github.com/repos/actions/runner/releases/latest' -UseBasicParsing).tag_name
$version = $tag.TrimStart('v')
$zipUrl  = "https://github.com/actions/runner/releases/download/$tag/actions-runner-win-x64-$version.zip"
$zipPath = Join-Path $env:TEMP "actions-runner-win-x64-$version.zip"
Write-Host "  $tag"

if (-not (Test-Path $RunnerDir)) { New-Item -ItemType Directory $RunnerDir -Force | Out-Null }

if (Test-Path (Join-Path $RunnerDir 'config.cmd')) {
    Write-Host "Runner files already present in $RunnerDir; skipping download."
} else {
    if (-not (Test-Path $zipPath)) {
        Write-Host "Downloading $zipUrl ..."
        Invoke-WebRequest $zipUrl -OutFile $zipPath -UseBasicParsing
    }
    Write-Host "Extracting to $RunnerDir ..."
    Expand-Archive -Path $zipPath -DestinationPath $RunnerDir -Force
}

# --- Register ---------------------------------------------------------------------------
# --replace makes this re-runnable: re-registering an existing runner name takes over the
# old entry instead of erroring out.
Push-Location $RunnerDir
try {
    Write-Host "Registering '$Name' with $RepoUrl ..."
    & .\config.cmd --unattended --replace `
        --url    $RepoUrl `
        --token  $Token `
        --name   $Name `
        --labels $Labels `
        --work   '_work' `
        --runasservice `
        --windowslogonaccount 'NT AUTHORITY\SYSTEM'
    if ($LASTEXITCODE -ne 0) { Fail "config.cmd failed (exit $LASTEXITCODE). A registration token expires after 1 hour - generate a fresh one if that is the cause." }
} finally { Pop-Location }

# --- Verify -------------------------------------------------------------------------------
$svc = Get-Service | Where-Object { $_.Name -like 'actions.runner.*' } | Select-Object -First 1
if (-not $svc) { Fail 'Runner service was not created.' }

# config.cmd sets Automatic start; make it survive a crash the same way the app service does.
& sc.exe failure $svc.Name reset= 86400 actions= restart/5000/restart/5000/restart/30000 | Out-Null

if ($svc.Status -ne 'Running') { Start-Service $svc.Name }
$svc.Refresh()

Write-Host ""
Write-Host "Runner installed" -ForegroundColor Green
Write-Host "  service : $($svc.Name)  [$($svc.Status)]"
Write-Host "  account : LocalSystem   (runs with no user logged in)"
Write-Host "  labels  : self-hosted, Windows, X64, $Labels"
Write-Host "  dir     : $RunnerDir"
Write-Host ""
Write-Host "Confirm it shows as Idle at $RepoUrl/settings/actions/runners"
