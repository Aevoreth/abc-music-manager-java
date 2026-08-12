#Requires -Version 5.1
<#
.SYNOPSIS
  Build self-contained Windows ZIP + MSI for ABC Music Manager via jpackage.

.PARAMETER Version
  App version for --app-version and artifact names (e.g. 0.1.0). Must be numeric x.y.z.

.PARAMETER Jar
  Path to the shaded fat JAR (abc-music-manager.jar).
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Version,

    [Parameter(Mandatory = $true)]
    [string] $Jar
)

$ErrorActionPreference = 'Stop'

function Require-Command([string] $Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found on PATH: $Name"
    }
}

if ($Version -notmatch '^\d+(\.\d+){1,3}$') {
    throw "Version must be numeric like 0.1.0 (got: $Version)"
}

$JarPath = (Resolve-Path -LiteralPath $Jar).Path
if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "JAR not found: $Jar"
}

Require-Command jdeps
Require-Command jpackage

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
$BuildDir = Join-Path $ScriptDir 'build'
$InputDir = Join-Path $BuildDir 'input'
$ImageDest = Join-Path $BuildDir 'image'
$MsiDest = Join-Path $BuildDir 'msi'
$OutputDir = Join-Path $ScriptDir 'output'
$IconPath = Join-Path $RepoRoot 'abcmm-app\src\main\resources\com\aevoreth\abcmm\icons\app.ico'
$AppName = 'ABC-Music-Manager'
$MainJarName = 'abc-music-manager.jar'
$MainClass = 'com.aevoreth.abcmm.AbcMusicManagerMain'

if (-not (Test-Path -LiteralPath $IconPath)) {
    throw "Icon not found: $IconPath"
}

Write-Host "Cleaning build directories..."
if (Test-Path -LiteralPath $BuildDir) {
    Remove-Item -LiteralPath $BuildDir -Recurse -Force
}
if (Test-Path -LiteralPath $OutputDir) {
    Remove-Item -LiteralPath $OutputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $InputDir -Force | Out-Null
New-Item -ItemType Directory -Path $ImageDest -Force | Out-Null
New-Item -ItemType Directory -Path $MsiDest -Force | Out-Null
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

Copy-Item -LiteralPath $JarPath -Destination (Join-Path $InputDir $MainJarName) -Force

Write-Host "Resolving trimmed module set via jdeps..."
$jdepsOut = & jdeps --print-module-deps -R --ignore-missing-deps --multi-release 21 $JarPath 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "jdeps failed: $jdepsOut"
}
$jdepsLine = ($jdepsOut | Where-Object { $_ -match '\S' } | Select-Object -Last 1).ToString().Trim()

$extras = @(
    'jdk.charsets',
    'jdk.accessibility',
    'jdk.crypto.ec',
    'java.prefs',
    'java.management',
    'java.logging'
)

$modules = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($part in ($jdepsLine -split ',')) {
    $m = $part.Trim()
    if ($m) { [void]$modules.Add($m) }
}
foreach ($m in $extras) {
    [void]$modules.Add($m)
}

$addModules = ($modules | Sort-Object) -join ','
Write-Host "jdeps modules: $jdepsLine"
Write-Host "Merged --add-modules: $addModules"

$commonArgs = @(
    '--name', $AppName,
    '--app-version', $Version,
    '--vendor', 'Aevoreth',
    '--description', 'ABC Music Manager — LOTRO bandleader companion',
    '--icon', $IconPath,
    '--input', $InputDir,
    '--main-jar', $MainJarName,
    '--main-class', $MainClass,
    '--add-modules', $addModules,
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--java-options', '--add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED',
    '--java-options', '--add-exports=java.desktop/sun.awt.shell=ALL-UNNAMED'
)

Write-Host "Building app-image..."
& jpackage @commonArgs --type app-image --dest $ImageDest
if ($LASTEXITCODE -ne 0) {
    throw "jpackage app-image failed with exit code $LASTEXITCODE"
}

$appImageDir = Join-Path $ImageDest $AppName
if (-not (Test-Path -LiteralPath $appImageDir)) {
    throw "Expected app-image directory missing: $appImageDir"
}

# Clear read-only flags that some JDKs set on packaged files (breaks zip/cleanup).
Get-ChildItem -LiteralPath $appImageDir -Recurse -Force | ForEach-Object {
    if ($_.Attributes -band [IO.FileAttributes]::ReadOnly) {
        $_.Attributes = $_.Attributes -band (-bnot [IO.FileAttributes]::ReadOnly)
    }
}

$zipPath = Join-Path $OutputDir "$AppName-$Version.zip"
Write-Host "Creating $zipPath ..."
if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
Compress-Archive -Path (Join-Path $appImageDir '*') -DestinationPath $zipPath -CompressionLevel Optimal

Write-Host "Building MSI..."
& jpackage @commonArgs `
    --type msi `
    --dest $MsiDest `
    --win-menu `
    --win-shortcut
if ($LASTEXITCODE -ne 0) {
    throw "jpackage msi failed with exit code $LASTEXITCODE"
}

$producedMsi = Get-ChildItem -LiteralPath $MsiDest -Filter '*.msi' | Select-Object -First 1
if (-not $producedMsi) {
    throw "No MSI produced in $MsiDest"
}

$msiPath = Join-Path $OutputDir "$AppName-$Version.msi"
Copy-Item -LiteralPath $producedMsi.FullName -Destination $msiPath -Force

Write-Host "Done."
Write-Host "  $zipPath"
Write-Host "  $msiPath"
Write-Host "Modules: $addModules"
