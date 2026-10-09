<#
.SYNOPSIS
    Reverts use-local-tibco.ps1: activates migrate-tibco from Ballerina Central and removes the local build.
.PARAMETER Version
    Central version to activate. Defaults to the latest version on Central.
.EXAMPLE
    ./scripts/use-central-tibco.ps1
    ./scripts/use-central-tibco.ps1 -Version 1.2.16
#>
param(
    [string]$Version
)

$ErrorActionPreference = 'Stop'

function Invoke-NativeCommand([scriptblock]$Command) {
    # Native tools log progress to stderr; only the exit code signals failure.
    $ErrorActionPreference = 'Continue'
    & $Command 2>&1 | ForEach-Object { "$_" } | Out-Host
    return $LASTEXITCODE
}

function Invoke-Native([string]$Description, [scriptblock]$Command) {
    $exitCode = Invoke-NativeCommand $Command
    if ($exitCode -ne 0) {
        throw "$Description failed (exit code $exitCode)"
    }
}

function Get-CentralTibcoVersions {
    $centralBalaRoot = Join-Path $HOME '.ballerina/repositories/central.ballerina.io/bala/wso2/tool_migrate_tibco'
    if (-not (Test-Path $centralBalaRoot)) {
        return @()
    }
    Get-ChildItem -Directory $centralBalaRoot | ForEach-Object { $_.Name }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$localVersion = (Select-String -Path (Join-Path $repoRoot 'gradle.properties') -Pattern '^tibcoVersion=(.+)$').Matches[0].Groups[1].Value.Trim()
$localBalaDir = Join-Path $HOME ".ballerina/repositories/local/bala/wso2/tool_migrate_tibco/$localVersion"

if (-not (Get-Command bal -ErrorAction SilentlyContinue)) {
    throw "'bal' was not found on PATH. Install Ballerina first."
}

# Switch to Central first so the local build is no longer active when its bala is deleted.
Write-Host "==> Activating migrate-tibco from Ballerina Central" -ForegroundColor Cyan
if ($Version) {
    if ((Get-CentralTibcoVersions) -notcontains $Version) {
        Invoke-Native 'bal tool pull' { bal tool pull "migrate-tibco:$Version" }
    }
    # pull alone does not override a version previously pinned with 'bal tool use'.
    Invoke-Native 'bal tool use' { bal tool use "migrate-tibco:$Version" }
} else {
    # Fails harmlessly when the latest version is already installed; the use below activates it either way.
    Invoke-NativeCommand { bal tool pull migrate-tibco } | Out-Null
    $latest = Get-CentralTibcoVersions | Sort-Object { [version]($_ -replace '-.*$', '') } | Select-Object -Last 1
    if (-not $latest) {
        throw "No Central version of migrate-tibco is installed and pulling the latest failed."
    }
    Invoke-Native 'bal tool use' { bal tool use "migrate-tibco:$latest" }
}

Write-Host "==> Removing local migrate-tibco:$localVersion" -ForegroundColor Cyan
# Not 'bal tool remove --repository=local': when Central has the same version it deletes the Central bala too.
# The inactive local entry left in bal-tools.toml is harmless and is reused by use-local-tibco.ps1.
if (Test-Path $localBalaDir) {
    Remove-Item -Recurse -Force $localBalaDir
}

# bal exits 0 even when 'bal tool use' fails, so confirm the result here.
$toolList = (& bal tool list 2>&1) -join "`n"
Write-Host $toolList
if ($toolList -notmatch 'migrate-tibco\s*\|\s*[^\s|]+\s*\|') {
    throw "No Central version of migrate-tibco is active."
}
Write-Host ""
Write-Host "Done. Reload VS Code (Developer: Reload Window) so WSO2 Integrator picks up the Central migrate-tibco." -ForegroundColor Green
