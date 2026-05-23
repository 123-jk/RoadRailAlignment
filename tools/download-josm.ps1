param(
    [string]$Url = "https://josm.openstreetmap.de/josm-tested.jar",
    [string]$OutFile = ".\lib\josm-tested.jar"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location -LiteralPath $root

$outDir = Split-Path -Parent (Join-Path $root $OutFile)
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}

Invoke-WebRequest -Uri $Url -OutFile $OutFile
Write-Host "Downloaded $Url to $OutFile"

