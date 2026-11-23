#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$JdkPath = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolchainRoot = Join-Path (Split-Path -Parent $projectRoot) 'toolchains'
$portableJdk = Join-Path $toolchainRoot 'jdk17\jdk-17.0.21.0.2+1'
$portableSdk = Join-Path $toolchainRoot 'android-sdk'
if (-not $SdkPath -and (Test-Path -LiteralPath (Join-Path $portableSdk 'platforms\android-35\android.jar'))) { $SdkPath = $portableSdk }
if (-not $SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $JdkPath -and (Test-Path -LiteralPath (Join-Path $portableJdk 'bin\javac.exe'))) { $JdkPath = $portableJdk }
if (-not $JdkPath) { $JdkPath = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
$androidJar = Join-Path $SdkPath 'platforms\android-35\android.jar'
$buildTools = Join-Path $SdkPath 'build-tools\35.0.0'
$javac = Join-Path $JdkPath 'bin\javac.exe'
$java = Join-Path $JdkPath 'bin\java.exe'
$jar = Join-Path $JdkPath 'bin\jar.exe'
$keytool = Join-Path $JdkPath 'bin\keytool.exe'
$aapt2 = Join-Path $buildTools 'aapt2.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$d8 = Join-Path $buildTools 'lib\d8.jar'
$apksigner = Join-Path $buildTools 'lib\apksigner.jar'

foreach ($needed in @($androidJar, $javac, $java, $jar, $keytool, $aapt2, $zipalign, $d8, $apksigner)) {
    if (-not (Test-Path -LiteralPath $needed -PathType Leaf)) {
        throw "Missing tool: $needed. Install JDK 17+ and Android SDK Platform 35 + Build-Tools 35.0.0. See README.md."
    }
}

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build step failed ($LASTEXITCODE): $Executable" }
}

& (Join-Path $PSScriptRoot 'Test-Home.ps1') -JdkPath $JdkPath

# Every build has its own work directory; no recursive deletion or wildcard cleanup.
$buildRoot = Join-Path $projectRoot 'build'
$runRoot = Join-Path $buildRoot ('run-' + [Guid]::NewGuid().ToString('N'))
$generated = Join-Path $runRoot 'generated'
$classes = Join-Path $runRoot 'classes'
$dex = Join-Path $runRoot 'dex'
$localKeys = Join-Path $projectRoot '.local'
foreach ($directory in @($buildRoot, $runRoot, $generated, $classes, $dex, $localKeys)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$moduleRoot = Join-Path $projectRoot 'vendor\contour\home'
$manifestPath = Join-Path $moduleRoot 'AndroidManifest.xml'
[xml]$manifest = [IO.File]::ReadAllText($manifestPath)
$version = $manifest.DocumentElement.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
$apkName = "ContourHome-$version.apk"
$compiledResources = Join-Path $runRoot 'resources.zip'
$resourceApk = Join-Path $runRoot 'resources.apk'
Invoke-Checked $aapt2 @('compile', '--dir', (Join-Path $moduleRoot 'res'), '-o', $compiledResources)
Invoke-Checked $aapt2 @('link', '-o', $resourceApk, '-I', $androidJar, '--manifest',
    $manifestPath, '--java', $generated, '-R', $compiledResources,
    '--min-sdk-version', '31', '--target-sdk-version', '35', '--auto-add-overlay')

$sourceList = Join-Path $runRoot 'sources.txt'
$sourcePaths = @(Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'src'), $generated -Filter '*.java' -Recurse -File |
    ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
[IO.File]::WriteAllLines($sourceList, [string[]]$sourcePaths, [Text.UTF8Encoding]::new($false))
Invoke-Checked $javac @('--release', '8', '-encoding', 'UTF-8', '-classpath', $androidJar,
    '-d', $classes, ('@' + $sourceList))
$classJar = Join-Path $runRoot 'classes.jar'
Invoke-Checked $jar @('--create', '--file', $classJar, '-C', $classes, '.')
Invoke-Checked $java @('-cp', $d8, 'com.android.tools.r8.D8', '--lib', $androidJar,
    '--min-api', '31', '--output', $dex, $classJar)

$unsigned = Join-Path $runRoot 'unsigned.apk'
Copy-Item -LiteralPath $resourceApk -Destination $unsigned
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($dexFile in Get-ChildItem -LiteralPath $dex -Filter '*.dex' -File) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $dexFile.FullName, $dexFile.Name,
            [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
$aligned = Join-Path $runRoot 'aligned.apk'
Invoke-Checked $zipalign @('-f', '-p', '4', $unsigned, $aligned)

# This is a development signing key, never an Android platform or release key.
$keystore = Join-Path $localKeys 'home-development.keystore'
if (-not (Test-Path -LiteralPath $keystore)) {
    Invoke-Checked $keytool @('-genkeypair', '-keystore', $keystore, '-storepass', 'android',
        '-keypass', 'android', '-alias', 'androiddebugkey', '-dname', 'CN=Contour Development,O=Local,C=XX',
        '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000', '-storetype', 'JKS', '-noprompt')
}
$signed = Join-Path $runRoot $apkName
Invoke-Checked $java @('-jar', $apksigner, 'sign', '--ks', $keystore, '--ks-key-alias', 'androiddebugkey',
    '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--v1-signing-enabled', 'false',
    '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
    '--out', $signed, $aligned)
Invoke-Checked $java @('-jar', $apksigner, 'verify', '--verbose', '--print-certs', $signed)
Invoke-Checked $zipalign @('-c', '-p', '4', $signed)
$finalApk = Join-Path $buildRoot $apkName
Copy-Item -LiteralPath $signed -Destination $finalApk -Force
$sha = (Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash.ToLowerInvariant()
$metadata = [ordered]@{
    package = 'app.contouros.home'; version = $version; minSdk = 31; targetSdk = 35
    sha256 = $sha; bytes = (Get-Item -LiteralPath $finalApk).Length
    builtUtc = [DateTime]::UtcNow.ToString('o'); signing = 'local-development-key'
    sdkPlatform = 35; buildTools = '35.0.0'
}
[IO.File]::WriteAllText((Join-Path $buildRoot 'build-info.json'), ($metadata | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
Write-Output "APK ready: $finalApk"
Write-Output "SHA256: $sha"
Write-Output 'No installation, reboot or change of default launcher was performed.'
