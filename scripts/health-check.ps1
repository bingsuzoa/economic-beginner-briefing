# Waits for the briefing app to report healthy and prints why if it never does.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\health-check.ps1
#
# Exit 0 = healthy, 1 = not. Two callers: scripts\deploy.ps1 (decides whether to roll back)
# and .github\workflows\deploy.yml (final gate of the deploy job).
#
# ASCII only on purpose: Windows PowerShell 5.1 reads a BOM-less .ps1 as ANSI.
#
# Why a 503 is not automatically a failure: /api/health/briefing answers "are briefings still
# being produced?", so it reports DOWN once the last successful pipeline run is older than
# briefing.health.max-success-age (3h). Restarting the service does not run the pipeline - it
# waits for the next hour tick - so a deploy more than 3h after the last run gets a legitimate
# 503 from a perfectly healthy app. That one reason is tolerated. Anything else (database
# unreachable, invalid cron) still fails. Pass -Strict to demand a literal 200.

param(
    [string]$Url         = 'http://localhost:3000/api/health/briefing',
    [int]   $TimeoutSec  = 180,
    [int]   $IntervalSec = 3,
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'

# The stale-run reasons the app emits; both mean "nothing ran recently", not "app is broken".
$StaleReason = '^no successful (pipeline run recorded yet|run in \d+m)'

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$attempt  = 0
$last     = 'no response (service not listening yet)'

Write-Host "Health check: $Url (timeout ${TimeoutSec}s)"

while ($true) {
    $attempt++
    $code = 0
    $body = $null

    try {
        $r    = Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 20
        $code = [int]$r.StatusCode
        $body = $r.Content
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $code = [int]$resp.StatusCode
            # Windows PowerShell 5.1 has already drained the error response stream by the time
            # the exception surfaces, so GetResponseStream() reads back empty and the body is
            # only on ErrorDetails. Reading the stream first would silently lose the reasons
            # this whole script exists to inspect. Stream fallback is for PowerShell 7+.
            if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
                $body = $_.ErrorDetails.Message
            } else {
                try { $body = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd() } catch { }
            }
        } else {
            $last = "connection failed: $($_.Exception.Message)"
        }
    }

    if ($code -eq 200) {
        Write-Host "HEALTHY (200 after $attempt attempt(s))" -ForegroundColor Green
        if ($body) { Write-Host "  $body" }
        exit 0
    }

    if ($code -eq 503 -and $body) {
        $last = "HTTP 503 $body"
        if (-not $Strict) {
            $json = $null
            try { $json = $body | ConvertFrom-Json } catch { }
            # Tolerate ONLY the documented stale-run case, and only with the DB actually up.
            if ($json -and $json.dbConnected -eq $true -and $json.reasons -and
                -not ($json.reasons | Where-Object { $_ -notmatch $StaleReason })) {
                Write-Host "HEALTHY (503, stale-run only - app and DB are up)" -ForegroundColor Yellow
                Write-Host "  reasons: $($json.reasons -join '; ')"
                Write-Host "  The pipeline runs on the next hour tick; this is expected right after a deploy."
                exit 0
            }
        }
    } elseif ($code -ne 0) {
        $last = "HTTP $code $body"
    }

    if ((Get-Date) -ge $deadline) { break }
    Start-Sleep -Seconds $IntervalSec
}

Write-Host "UNHEALTHY after ${TimeoutSec}s ($attempt attempts)" -ForegroundColor Red
Write-Host "  last: $last"
exit 1
