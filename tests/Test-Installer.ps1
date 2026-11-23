#Requires -Version 5.1
# A fake command replaces ADB for this test; no phone is accessed.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = Split-Path -Parent $PSScriptRoot
$installer = Join-Path $project 'tools\Install-Home.ps1'
$fixture = Join-Path $PSScriptRoot 'fixtures\adb.cmd'
$run = Join-Path $project ('build\installer-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run -Force | Out-Null
$savedCase = $env:CONTOUR_INSTALL_TEST_CASE
$savedLog = $env:CONTOUR_INSTALL_TEST_LOG
try {
    foreach ($case in @('success', 'wrong-device', 'unauthorized', 'install-error')) {
        $env:CONTOUR_INSTALL_TEST_CASE = $case
        $env:CONTOUR_INSTALL_TEST_LOG = Join-Path $run ($case + '.log')
        $caught = $null
        try { & $installer -AdbPath $fixture -Open | Out-Null }
        catch { $caught = $_.Exception.Message }
        $calls = Get-Content -LiteralPath $env:CONTOUR_INSTALL_TEST_LOG -Raw
        if ($case -eq 'success') {
            if ($caught) { throw "Startup diagnostics caused a failed install: $caught" }
            if ($calls -notmatch ' install -r ' -or $calls -notmatch ' shell am start ') { throw 'Install and launch were not both reached.' }
        } else {
            if (-not $caught) { throw "Expected refusal for: $case" }
            if ($calls -match ' shell am start ') { throw "Launched after failed verification: $case" }
            if ($case -ne 'install-error' -and $calls -match ' install -r ') { throw "Installed on an unverified device: $case" }
            if ($case -eq 'wrong-device' -and $caught -notmatch 'Unexpected device') { throw $caught }
            if ($case -eq 'unauthorized' -and $caught -notmatch 'USB debugging must be authorized') { throw $caught }
            if ($case -eq 'install-error' -and $caught -notmatch 'INSTALL_FAILED_TEST') { throw $caught }
        }
        Write-Output "PASS: installer $case (fake ADB)"
    }
} finally {
    $env:CONTOUR_INSTALL_TEST_CASE = $savedCase
    $env:CONTOUR_INSTALL_TEST_LOG = $savedLog
}
