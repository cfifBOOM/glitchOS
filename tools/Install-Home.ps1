#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$AdbPath = 'adb',
    [switch]$Open
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$targetSerial = '39f26a79' # Deliberately restricted to the experimental OnePlus.
if (-not $ApkPath) {
    $projectRoot = Split-Path -Parent $PSScriptRoot
    [xml]$manifest = [IO.File]::ReadAllText((Join-Path $projectRoot 'vendor\contour\home\AndroidManifest.xml'))
    $version = $manifest.DocumentElement.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
    $ApkPath = Join-Path $projectRoot "build\ContourHome-$version.apk"
}
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw "APK not found: $ApkPath. Run Build-Home.ps1 first." }

function Read-DeviceValue {
    param([string[]]$AdbArguments)
    # Windows PowerShell 5.1 can turn native stderr into terminating errors
    # when redirected under Stop. ADB writes normal daemon startup to stderr.
    $savedErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $AdbPath '-s' $targetSerial @AdbArguments 2>&1
        $adbExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedErrorAction
    }
    if ($adbExitCode -ne 0) { throw "ADB failed: $($output -join ' ')" }
    return ($output -join "`n").Trim()
}

# Keep startup diagnostics separate from values such as the exact device state.
$serverMessage = Read-DeviceValue @('start-server')
if ($serverMessage) { Write-Output $serverMessage }
if ((Read-DeviceValue @('get-state')) -ne 'device') { throw 'The OnePlus must be booted into Android and USB debugging must be authorized.' }
$device = Read-DeviceValue @('shell', 'getprop', 'ro.product.device')
$model = Read-DeviceValue @('shell', 'getprop', 'ro.product.model')
if ($device -notin @('OnePlus6T', 'fajita')) { throw "Unexpected device: $device / $model. Nothing was installed." }
$sdkText = Read-DeviceValue @('shell', 'getprop', 'ro.build.version.sdk')
$sdk = 0
if (-not [int]::TryParse($sdkText, [ref]$sdk) -or $sdk -lt 31) { throw "Unsupported Android API: $sdkText" }

# Optional sibling build metadata detects an accidental APK replacement.
$infoPath = Join-Path (Split-Path -Parent $ApkPath) 'build-info.json'
if (Test-Path -LiteralPath $infoPath) {
    $info = Get-Content -LiteralPath $infoPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $actualHash = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash
    if ($info.package -ne 'app.contouros.home' -or $actualHash -ne $info.sha256) { throw 'APK metadata or SHA256 mismatch. Nothing was installed.' }
}
Write-Output "Installing the experimental home screen on $model ($targetSerial)."
$installResult = Read-DeviceValue @('install', '-r', $ApkPath)
Write-Output $installResult
if ($installResult -notmatch '(?m)^Success\s*$') { throw "Installation success was not confirmed: $installResult" }
Write-Output (Read-DeviceValue @('shell', 'pm', 'path', 'app.contouros.home'))
if ($Open) {
    Write-Output (Read-DeviceValue @('shell', 'am', 'start', '-n', 'app.contouros.home/.HomeActivity'))
}
Write-Output 'Open Contour from the app list. Choosing it as Home is optional and happens on the phone.'
