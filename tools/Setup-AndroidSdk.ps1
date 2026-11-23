#Requires -Version 5.1
[CmdletBinding()]
param([string]$JdkPath = $env:JAVA_HOME, [string]$CommandLineToolsZip)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolchainRoot = Join-Path (Split-Path -Parent $projectRoot) 'toolchains'
$portableJdk = Join-Path $toolchainRoot 'jdk17\jdk-17.0.21.0.2+1'
if (-not $JdkPath -and (Test-Path -LiteralPath (Join-Path $portableJdk 'bin\javac.exe'))) { $JdkPath = $portableJdk }
if (-not $JdkPath) { $JdkPath = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
if (-not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin\javac.exe'))) { throw 'JDK 17+ is required. Supply -JdkPath or install Android Studio.' }
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'
$cache = Join-Path $toolchainRoot 'downloads'
New-Item -ItemType Directory -Path $sdkRoot, $cache -Force | Out-Null
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
    if (-not $CommandLineToolsZip) {
        $CommandLineToolsZip = Join-Path $cache 'commandlinetools-win-15859902_latest.zip'
        if (-not (Test-Path -LiteralPath $CommandLineToolsZip)) {
            Write-Output 'Downloading official Android command-line tools (about 156 MB)...'
            Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $CommandLineToolsZip
        }
    }
    $expected = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    if ((Get-FileHash -LiteralPath $CommandLineToolsZip -Algorithm SHA256).Hash -ne $expected) {
        throw 'Official command-line tools SHA256 does not match. No downloaded code was run.'
    }
    $unpack = Join-Path $cache ('sdk-unpack-' + [Guid]::NewGuid().ToString('N'))
    Expand-Archive -LiteralPath $CommandLineToolsZip -DestinationPath $unpack
    $toolsParent = Join-Path $sdkRoot 'cmdline-tools'
    New-Item -ItemType Directory -Path $toolsParent -Force | Out-Null
    $toolsTarget = Join-Path $toolsParent 'latest'
    if (Test-Path -LiteralPath $toolsTarget) { throw "Existing incomplete SDK folder: $toolsTarget. Inspect it before continuing." }
    Copy-Item -LiteralPath (Join-Path $unpack 'cmdline-tools') -Destination $toolsTarget -Recurse
}

$oldJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JdkPath
    Write-Output 'The official SDK manager will show Google license terms and ask for your answer.'
    Write-Output 'Nothing accepts those terms automatically. You may decline.'
    & $sdkManager "--sdk_root=$sdkRoot" 'platforms;android-35' 'build-tools;35.0.0'
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK setup did not finish successfully.' }
} finally {
    $env:JAVA_HOME = $oldJava
}
foreach ($needed in @('platforms\android-35\android.jar', 'build-tools\35.0.0\aapt2.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $sdkRoot $needed))) { throw "Required SDK package is still missing: $needed" }
}
Write-Output "SDK ready: $sdkRoot"
Write-Output 'Build separately with tools\Build-Home.ps1. The phone was not changed.'
