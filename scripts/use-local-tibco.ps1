<#
.SYNOPSIS
    Builds migrate-tibco from this repo and makes it the active bal tool, so WSO2 Integrator uses the local build.
.PARAMETER SkipBuild
    Reuse the bala already packed under cli-tibco/src/main/ballerina/tool-bi-migrate-tibco/target.
.EXAMPLE
    ./scripts/use-local-tibco.ps1
#>
param(
    [switch]$SkipBuild
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

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$toolDir = Join-Path $repoRoot 'cli-tibco/src/main/ballerina/tool-bi-migrate-tibco'
$version = (Select-String -Path (Join-Path $repoRoot 'gradle.properties') -Pattern '^tibcoVersion=(.+)$').Matches[0].Groups[1].Value.Trim()
$toolRef = "migrate-tibco:$version"
# A tool pulled from the local repo runs straight from this directory, so it must hold the fresh build.
$localBalaDir = Join-Path $HOME ".ballerina/repositories/local/bala/wso2/tool_migrate_tibco/$version"
$tomls = @(
    'cli-tibco/src/main/ballerina/tool-bi-migrate-tibco/Ballerina.toml',
    'cli-tibco/src/main/ballerina/tool-bi-migrate-tibco/BalTool.toml'
)

if (-not (Get-Command bal -ErrorAction SilentlyContinue)) {
    throw "'bal' was not found on PATH. Install Ballerina first."
}

Push-Location $repoRoot
try {
    if (-not $SkipBuild) {
        if (-not $env:packageUser -or -not $env:packagePAT) {
            Write-Warning "packageUser/packagePAT are not set; Gradle may fail to resolve GitHub Packages dependencies."
        }
        Write-Host "==> Building and packing $toolRef" -ForegroundColor Cyan
        Invoke-Native 'Gradle tibcoJar' { & ./gradlew.bat :cli-tibco:tibcoJar :cli-tibco:updateTomlFile }
        # bal pack is run here rather than via tibcoPack: launched from Gradle it runs out of heap.
        Push-Location $toolDir
        try {
            Invoke-Native 'bal pack' { bal pack }
        } finally {
            Pop-Location
        }
    }

    # bal push does not reliably replace an existing bala of the same version, so clear it first.
    if (Test-Path $localBalaDir) {
        Remove-Item -Recurse -Force $localBalaDir
    }

    Write-Host "==> Pushing $toolRef to the local repository" -ForegroundColor Cyan
    Push-Location $toolDir
    try {
        Invoke-Native 'bal push --repository=local' { bal push --repository=local }
    } finally {
        Pop-Location
    }
} finally {
    # updateTomlFile rewrites these in place; restore them so the working tree stays clean.
    if (git status --porcelain -- $tomls) {
        git checkout -- $tomls
    }
    Pop-Location
}

Write-Host "==> Activating $toolRef from the local repository" -ForegroundColor Cyan
# bal exits 0 even when these fail, so success is checked through 'bal tool list' below.
# pull registers the tool when needed; use is still required to override a version pinned by an earlier 'use'.
Invoke-NativeCommand { bal tool pull $toolRef --repository=local } | Out-Null
Invoke-NativeCommand { bal tool use $toolRef --repository=local } | Out-Null

$toolList = (& bal tool list 2>&1) -join "`n"
Write-Host $toolList
if ($toolList -notmatch "migrate-tibco\s*\|\s*$([regex]::Escape($version))\s*\[repo = local\]") {
    throw "migrate-tibco:$version from the local repository is not active."
}
Write-Host ""
Write-Host "Done. Reload VS Code (Developer: Reload Window) so WSO2 Integrator picks up the local $toolRef." -ForegroundColor Green
Write-Host "Run ./scripts/use-central-tibco.ps1 to switch back to the Central release."
