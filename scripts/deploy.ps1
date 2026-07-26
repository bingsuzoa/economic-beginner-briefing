# Rebuild and redeploy the EconomicBriefing service.
#
#   Run from an ELEVATED PowerShell:
#     powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy.ps1
#
# The stop-before-build order is mandatory, not tidiness: Windows keeps an exclusive
# handle on the running JAR, so 'gradlew clean' fails with "Unable to delete file" while
# the service is up. Use this script instead of calling gradlew directly.
#
# ASCII only on purpose: Windows PowerShell 5.1 reads a BOM-less .ps1 as ANSI.

$ErrorActionPreference = 'Stop'

$ServiceName = 'EconomicBriefing'
$ProjectRoot = Split-Path -Parent $PSScriptRoot

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
        ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'ERROR: Administrator rights required.' -ForegroundColor Red; exit 1
}

Set-Location $ProjectRoot

if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    Write-Host "Stopping $ServiceName ..."
    Stop-Service $ServiceName -Force
    # Stop-Service returns once the SCM reports STOPPED, but the JVM's file handle can
    # outlive that by a moment. Wait for the JAR to actually be deletable.
    $jar = Join-Path $ProjectRoot 'build\libs\economic-briefing-0.1.0.jar'
    for ($i = 0; $i -lt 30 -and (Test-Path $jar); $i++) {
        try { [IO.File]::Open($jar, 'Open', 'ReadWrite', 'None').Dispose(); break }
        catch { Start-Sleep -Milliseconds 500 }
    }
}

Write-Host "Building ..."
& (Join-Path $ProjectRoot 'gradlew.bat') clean bootJar --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: build failed; service left stopped." -ForegroundColor Red
    exit 1
}

# install-service.ps1 re-registers from scratch, so a changed .env is picked up too.
& (Join-Path $PSScriptRoot 'install-service.ps1')
