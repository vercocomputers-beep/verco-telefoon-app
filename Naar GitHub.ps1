<#
  Verco Telefoon app - deze map naar GitHub
  -----------------------------------------
  Legt wijzigingen vast (commit), haalt op wat op GitHub staat (pull --rebase)
  en stuurt alles naar GitHub (push). Veilig om vaker te draaien.
  Eerste keer aanmelden bij GitHub: gh auth login  (GitHub.com -> HTTPS -> browser).
#>
[CmdletBinding()]
param([switch]$Pauze)
$ErrorActionPreference = "Stop"
function Info($t){ Write-Host $t -ForegroundColor Cyan }
function Ok($t){ Write-Host $t -ForegroundColor Green }
function Stop-Net($t,$c=1){ Write-Host ""; Write-Host $t -ForegroundColor Red; if($Pauze){Read-Host "Enter om te sluiten"|Out-Null}; exit $c }

Set-Location $PSScriptRoot
# Als beheerder ziet git de map (van gebruiker marcel, OneDrive) als "niet van mij" en weigert dan stilletjes.
# Daarom deze map voor dit script als veilig aanmerken.
$env:GIT_CONFIG_COUNT = "1"; $env:GIT_CONFIG_KEY_0 = "safe.directory"; $env:GIT_CONFIG_VALUE_0 = "*"
Info "=== Verco Telefoon app -> GitHub (map: $PSScriptRoot) ==="

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Info "git installeren (winget)..."
    winget install --id Git.Git -e --accept-package-agreements --accept-source-agreements
    $env:Path = [Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [Environment]::GetEnvironmentVariable("Path","User")
}
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Stop-Net "git nog niet gevonden. Sluit en start opnieuw." }
if (-not (Test-Path ".git")) { Stop-Net "Dit is geen git-map. Draai dit script in de map 'Verco Telefoon app'." }

git add -A
if ($LASTEXITCODE -ne 0) { Stop-Net "Vastleggen mislukt (git add)." }
$wijz = git status --porcelain
if ($wijz) { git commit -q -m "Back-up vanaf $env:COMPUTERNAME ($(Get-Date -Format 'yyyy-MM-dd HH:mm'))" }

$tak = (git rev-parse --abbrev-ref HEAD).Trim()
git pull --rebase --autostash origin $tak 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { git rebase --abort 2>$null | Out-Null; Stop-Net "Ophalen botste - handmatig bekijken (niets gepusht)." }

git push origin $tak 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { if ($wijz) { Ok "Gepusht naar GitHub." } else { Write-Host "Niets nieuws - al bij." -ForegroundColor Gray } }
else { Stop-Net "Push mislukt. Ben je aangemeld? (gh auth login)" }
if ($Pauze) { Read-Host "Enter om te sluiten" | Out-Null }
