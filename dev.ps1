$ErrorActionPreference = "Stop"

Write-Host "[dev] Building..." -ForegroundColor Cyan
& .\mvnw.cmd clean compile -q

Write-Host "[dev] Starting server..." -ForegroundColor Cyan
$serverJob = Start-Job -ScriptBlock {
    & .\mvnw.cmd exec:java@server
}

try {
    Write-Host "[dev] Starting client..." -ForegroundColor Cyan
    & .\mvnw.cmd javafx:run
}
finally {
    Write-Host "[dev] Stopping server..." -ForegroundColor Cyan
    Stop-Job -Job $serverJob
    Remove-Job -Job $serverJob
}
