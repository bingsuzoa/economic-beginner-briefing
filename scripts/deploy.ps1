# Build and deploy the EconomicBriefing service, rolling back if the result is not healthy.
#
#   Manual (from an ELEVATED PowerShell, inside the production directory):
#     powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy.ps1
#
#   CI (GitHub Actions self-hosted runner, building in the runner workspace):
#     powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy.ps1 `
#         -ProdRoot "C:\economic-beginner-briefing"
#
# Two directories, deliberately:
#   RepoRoot - where this script lives, and where the build runs.
#   ProdRoot - where the service runs. Only build\libs\*.jar, frontend\dist, .env and logs
#              matter there at runtime, so CI never touches the production git tree. That
#              keeps the developer's checkout (branch, uncommitted work) out of the blast
#              radius of a deploy. When the two are the same path it builds in place, which
#              is the manual case and the behaviour this script has always had.
#
# The stop-before-build order for the in-place case is mandatory, not tidiness: Windows keeps
# an exclusive handle on the running JAR, so 'gradlew clean' fails with "Unable to delete
# file" while the service is up. Building out of place has no such constraint, so CI stops
# the service only after a green build - downtime is the restart, not the whole build.
#
# ASCII only on purpose: Windows PowerShell 5.1 reads a BOM-less .ps1 as ANSI.

param(
    [string]$ProdRoot,
    [switch]$Test,
    [switch]$SkipFrontend,
    [int]   $HealthTimeoutSec = 180,
    [int]   $KeepBackups      = 3
)

$ErrorActionPreference = 'Stop'

$ServiceName = 'EconomicBriefing'
$RepoRoot    = Split-Path -Parent $PSScriptRoot
if (-not $ProdRoot) { $ProdRoot = $RepoRoot }
$ProdRoot = (Resolve-Path $ProdRoot).Path
$InPlace  = ($RepoRoot -eq $ProdRoot)

function Fail($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; throw $msg }
function Step($msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
        ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'ERROR: Administrator rights required.' -ForegroundColor Red; exit 1
}

$stamp     = Get-Date -Format 'yyyyMMdd-HHmmss'
$LogDir    = Join-Path $ProdRoot 'logs'
$BackupDir = Join-Path $ProdRoot "backup\$stamp"
$ProdLibs  = Join-Path $ProdRoot 'build\libs'
$ProdDist  = Join-Path $ProdRoot 'frontend\dist'

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory $LogDir -Force | Out-Null }
try { Start-Transcript -Path (Join-Path $LogDir "deploy-$stamp.log") | Out-Null } catch { }

Write-Host "Deploy $stamp"
Write-Host "  build from : $RepoRoot"
Write-Host "  deploy to  : $ProdRoot$(if ($InPlace) { '  (in place)' })"

# Returns the fat JAR in a build\libs, ignoring the -plain.jar bootJar also leaves behind.
function Get-BootJar($libs) {
    Get-ChildItem $libs -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*-plain.jar' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

function Stop-App {
    if (-not (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue)) { return }
    Write-Host "Stopping $ServiceName ..."
    Stop-Service $ServiceName -Force
    # Stop-Service returns once the SCM reports STOPPED, but the JVM's file handle can outlive
    # that by a moment. Wait for the JAR to actually be deletable before anyone touches it.
    $jar = Get-BootJar $ProdLibs
    for ($i = 0; $i -lt 30 -and $jar; $i++) {
        try { [IO.File]::Open($jar.FullName, 'Open', 'ReadWrite', 'None').Dispose(); break }
        catch { Start-Sleep -Milliseconds 500 }
    }
}

# ---------------------------------------------------------------- backup (before anything)
Step "Backing up current release"
$backedUpJar = $null
New-Item -ItemType Directory $BackupDir -Force | Out-Null
$currentJar = Get-BootJar $ProdLibs
if ($currentJar) {
    Copy-Item $currentJar.FullName $BackupDir
    $backedUpJar = Join-Path $BackupDir $currentJar.Name
    Write-Host "  jar  : $($currentJar.Name)"
} else {
    Write-Host "  jar  : none (first deploy) - rollback will not be available" -ForegroundColor Yellow
}
if (Test-Path $ProdDist) {
    Copy-Item $ProdDist (Join-Path $BackupDir 'dist') -Recurse
    Write-Host "  dist : frontend\dist"
}
# Keep the last few releases only; the fat JAR is ~64 MB each.
Get-ChildItem (Join-Path $ProdRoot 'backup') -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -Skip $KeepBackups |
    ForEach-Object { Write-Host "  prune: $($_.Name)"; Remove-Item $_.FullName -Recurse -Force }

function Invoke-Rollback($why) {
    Write-Host ""
    Write-Host "ROLLBACK: $why" -ForegroundColor Red
    if (-not $backedUpJar) {
        Write-Host "No previous JAR was backed up; nothing to roll back to." -ForegroundColor Red
        return $false
    }
    try {
        Stop-App
        New-Item -ItemType Directory $ProdLibs -Force | Out-Null
        Get-ChildItem $ProdLibs -Filter '*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
        Copy-Item $backedUpJar $ProdLibs
        $oldDist = Join-Path $BackupDir 'dist'
        if (Test-Path $oldDist) {
            if (Test-Path $ProdDist) { Remove-Item $ProdDist -Recurse -Force }
            Copy-Item $oldDist $ProdDist -Recurse
        }
        & (Join-Path $PSScriptRoot 'install-service.ps1') -ProjectRoot $ProdRoot
        & (Join-Path $PSScriptRoot 'health-check.ps1') -TimeoutSec $HealthTimeoutSec
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Rolled back to $(Split-Path $backedUpJar -Leaf) - service is healthy." -ForegroundColor Yellow
            return $true
        }
        Write-Host "Rollback ran but the service is still unhealthy. Manual attention needed." -ForegroundColor Red
        return $false
    } catch {
        Write-Host "Rollback itself failed: $_" -ForegroundColor Red
        return $false
    }
}

$stopped = $false
try {
    # ------------------------------------------------------------------------------- test
    if ($Test) {
        Step "Running tests"
        & (Join-Path $RepoRoot 'gradlew.bat') test --console=plain
        if ($LASTEXITCODE -ne 0) { Fail "tests failed; service untouched." }
    }

    # ------------------------------------------------------------------------------ build
    # In place: the running JVM locks the JAR, so the service has to go down first.
    if ($InPlace) { Step "Stopping service (in-place build locks the JAR)"; Stop-App; $stopped = $true }

    Step "Building JAR"
    & (Join-Path $RepoRoot 'gradlew.bat') clean bootJar --console=plain
    if ($LASTEXITCODE -ne 0) { Fail "build failed." }

    $newJar = Get-BootJar (Join-Path $RepoRoot 'build\libs')
    if (-not $newJar) { Fail "no boot JAR produced in $RepoRoot\build\libs" }
    Write-Host "  $($newJar.Name)  ($([int]($newJar.Length / 1MB)) MB)"

    # The app serves the UI straight off disk (application.yml -> file:${FRONTEND_DIST}), so a
    # frontend change reaches production only if dist is rebuilt. Skipping this would make the
    # pipeline silently backend-only.
    if (-not $SkipFrontend -and (Test-Path (Join-Path $RepoRoot 'frontend\package.json'))) {
        Step "Building frontend"
        Push-Location (Join-Path $RepoRoot 'frontend')
        try {
            if (-not (Test-Path 'node_modules')) {
                if (Test-Path 'package-lock.json') { & npm.cmd ci } else { & npm.cmd install }
                if ($LASTEXITCODE -ne 0) { Fail "npm install failed." }
            }
            & npm.cmd run build
            if ($LASTEXITCODE -ne 0) { Fail "frontend build failed." }
        } finally { Pop-Location }
    }

    # ----------------------------------------------------------------------------- publish
    if (-not $InPlace) {
        Step "Stopping service"
        Stop-App; $stopped = $true

        Step "Publishing artifacts to $ProdRoot"
        New-Item -ItemType Directory $ProdLibs -Force | Out-Null
        Get-ChildItem $ProdLibs -Filter '*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
        Copy-Item $newJar.FullName $ProdLibs
        Write-Host "  jar  -> $ProdLibs\$($newJar.Name)"

        $newDist = Join-Path $RepoRoot 'frontend\dist'
        if (Test-Path $newDist) {
            if (Test-Path $ProdDist) { Remove-Item $ProdDist -Recurse -Force }
            New-Item -ItemType Directory (Split-Path $ProdDist -Parent) -Force | Out-Null
            Copy-Item $newDist $ProdDist -Recurse
            Write-Host "  dist -> $ProdDist"
        }
    }

    # ----------------------------------------------------------------------------- install
    # Re-registers from scratch, so a changed .env is picked up too.
    Step "Registering and starting service"
    & (Join-Path $PSScriptRoot 'install-service.ps1') -ProjectRoot $ProdRoot

    Step "Health check"
    & (Join-Path $PSScriptRoot 'health-check.ps1') -TimeoutSec $HealthTimeoutSec
    if ($LASTEXITCODE -ne 0) {
        $ok = Invoke-Rollback "new release failed its health check"
        try { Stop-Transcript | Out-Null } catch { }
        exit $(if ($ok) { 1 } else { 2 })
    }

    Write-Host ""
    Write-Host "DEPLOY OK  ($(Split-Path $newJar.Name -Leaf))" -ForegroundColor Green
    Write-Host "  backup : $BackupDir"
    Write-Host "  log    : $LogDir\deploy-$stamp.log"
    try { Stop-Transcript | Out-Null } catch { }
    exit 0

} catch {
    Write-Host ""
    Write-Host "DEPLOY FAILED: $_" -ForegroundColor Red
    # Only roll back if the service was actually taken down. A failure before that (tests, or
    # an out-of-place build) left production running the old release untouched.
    if ($stopped) {
        $ok = Invoke-Rollback "deploy step failed"
        try { Stop-Transcript | Out-Null } catch { }
        exit $(if ($ok) { 1 } else { 2 })
    }
    Write-Host "Service was never stopped; production still runs the previous release." -ForegroundColor Yellow
    try { Stop-Transcript | Out-Null } catch { }
    exit 1
}
