# Verco Telefoon app (deze map) naar GitHub: wijzigingen vastleggen en versturen.
# Start via "Naar GitHub.cmd" (dubbelklikken). Werkt op dezelfde manier als het script van de centrale.
param([switch]$Pauze)
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot
# Let op: de functie mag niet "Git" heten (PowerShell kijkt niet naar hoofdletters, dan roept hij zichzelf aan).
# safe.directory=*: als beheerder vertrouwt git de OneDrive-map van gebruiker marcel anders niet.
function Invoke-Git { & git.exe -c "safe.directory=*" @args; if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') mislukt" } }

try {
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) {
        throw "Git is niet geinstalleerd. Installeer Git voor Windows (https://git-scm.com) en probeer opnieuw."
    }
    Write-Host ""
    Write-Host "=== Verco Telefoon app -> GitHub ===" -ForegroundColor Cyan
    Write-Host "----------------------------------------"

    Invoke-Git add -A
    $wijzigingen = & git.exe -c "safe.directory=*" status --porcelain
    if ($wijzigingen) {
        Write-Host "Gewijzigde bestanden:"
        $wijzigingen | ForEach-Object { Write-Host "   $_" }
        Invoke-Git commit -q -m "Bijgewerkt vanaf $env:COMPUTERNAME ($(Get-Date -Format 'yyyy-MM-dd HH:mm'))"
        Write-Host "Vastgelegd." -ForegroundColor Green
    } else {
        Write-Host "Geen nieuwe wijzigingen." -ForegroundColor Green
    }

    Write-Host "Versturen naar GitHub..."
    Invoke-Git push origin HEAD
    Write-Host ""
    Write-Host "Klaar: GitHub is bijgewerkt." -ForegroundColor Green
}
catch {
    Write-Host ""
    Write-Host "MISLUKT: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Tip: staat er een inlogvenster van GitHub open? Log daar in en probeer opnieuw."
}
Write-Host ""
Read-Host "Druk op Enter om af te sluiten"
