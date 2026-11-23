#Requires -Version 5.1
[CmdletBinding()]
param([string]$JdkPath = $env:JAVA_HOME, [switch]$StaticOnly)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$moduleRoot = Join-Path $projectRoot 'vendor\contour\home'
$utf8 = [Text.UTF8Encoding]::new($false, $true)

# Parse real XML and PowerShell syntax, including files with Cyrillic paths/text.
foreach ($file in Get-ChildItem -LiteralPath $moduleRoot -Filter '*.xml' -Recurse -File) {
    $xmlText = [IO.File]::ReadAllText($file.FullName, $utf8)
    [xml]$document = $xmlText
    if (-not $document.DocumentElement) { throw "Empty XML: $($file.FullName)" }
}
foreach ($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File) {
    $tokens = $null
    $parseErrors = $null
    [Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$parseErrors) | Out-Null
    if ($parseErrors.Count -gt 0) { throw "PowerShell syntax error in $($file.Name): $($parseErrors.Message -join '; ')" }
}
[xml]$manifest = [IO.File]::ReadAllText((Join-Path $moduleRoot 'AndroidManifest.xml'), $utf8)
$androidNs = 'http://schemas.android.com/apk/res/android'
if ($manifest.manifest.package -ne 'app.contouros.home') { throw 'Unexpected application ID' }
$permissions = @($manifest.SelectNodes('/manifest/uses-permission') | ForEach-Object { $_.GetAttribute('name', $androidNs) })
$expected = @('ACCESS_NETWORK_STATE','SCHEDULE_EXACT_ALARM','POST_NOTIFICATIONS','USE_FULL_SCREEN_INTENT',
    'FOREGROUND_SERVICE','FOREGROUND_SERVICE_MEDIA_PLAYBACK','WAKE_LOCK','VIBRATE','RECEIVE_BOOT_COMPLETED') |
    ForEach-Object { 'android.permission.' + $_ }
if (@(Compare-Object ($expected | Sort-Object) ($permissions | Sort-Object)).Count -ne 0) {
    throw 'Unexpected permission set for the local alarm milestone.'
}
foreach ($component in $manifest.SelectNodes('/manifest/application/activity | /manifest/application/service | /manifest/application/receiver')) {
    $name = $component.GetAttribute('name', $androidNs).TrimStart('.')
    $sourceFile = Join-Path $moduleRoot ('src\app\contouros\home\' + $name + '.java')
    if (-not (Test-Path -LiteralPath $sourceFile)) { throw "Missing Android component source: $name" }
}
Write-Output 'PASS: XML, PowerShell syntax, declared components and local-alarm permission set'
if ($StaticOnly) { Write-Output 'Java compilation and runtime tests were not requested.'; return }

if (-not $JdkPath) { $JdkPath = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
$javac = Join-Path $JdkPath 'bin\javac.exe'
$java = Join-Path $JdkPath 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javac) -or -not (Test-Path -LiteralPath $java)) { throw "JDK missing: $JdkPath" }
$testOutput = Join-Path $projectRoot ('build\test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testOutput -Force | Out-Null
& $javac '--release' '8' '-encoding' 'UTF-8' '-d' $testOutput `
    (Join-Path $moduleRoot 'src\app\contouros\home\TilePacking.java') `
    (Join-Path $moduleRoot 'src\app\contouros\home\TileSpec.java') `
    (Join-Path $projectRoot 'tests\app\contouros\home\TilePackingTest.java') `
    (Join-Path $moduleRoot 'src\app\contouros\home\OfflineCommand.java') `
    (Join-Path $moduleRoot 'src\app\contouros\home\PresenceMode.java') `
    (Join-Path $projectRoot 'tests\app\contouros\home\PresenceModeTest.java') `
    (Join-Path $projectRoot 'tests\app\contouros\home\OfflineCommandTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Java unit test compilation failed' }
& $java '-cp' $testOutput 'app.contouros.home.TilePackingTest'
if ($LASTEXITCODE -ne 0) { throw 'Java unit tests failed' }
& $java '-cp' $testOutput 'app.contouros.home.OfflineCommandTest'
if ($LASTEXITCODE -ne 0) { throw 'Offline command tests failed' }
& $java '-cp' $testOutput 'app.contouros.home.PresenceModeTest'
if ($LASTEXITCODE -ne 0) { throw 'Presence mode tests failed' }
& $javac '-encoding' 'UTF-8' '-d' $testOutput (Join-Path $projectRoot 'tests\SourceSyntaxCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Source parser compilation failed' }
$javaSources = @(Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'src') -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName })
& $java '-cp' $testOutput 'SourceSyntaxCheck' @javaSources
if ($LASTEXITCODE -ne 0) { throw 'Android source syntax parsing failed' }
