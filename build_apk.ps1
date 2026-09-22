$ErrorActionPreference = 'Stop'
$ProjectRoot = $PSScriptRoot
$JavaCandidates = @(@(
    $env:JAVA_HOME,
    'C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot'
) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) })

if (-not $JavaCandidates) {
    throw 'JDK 17 is required. Install Microsoft.OpenJDK.17 before building.'
}

$env:JAVA_HOME = $JavaCandidates[0]
$env:GRADLE_USER_HOME = Join-Path $ProjectRoot '.gradle-user-home'
$output = Join-Path $ProjectRoot 'app\build\outputs\apk\release\app-release.apk'
$release = Join-Path $ProjectRoot 'release\AI-Memo-Android-v0.9.6.apk'

Push-Location $ProjectRoot
try {
    & .\gradlew.bat --no-daemon testDebugUnitTest lintRelease assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    New-Item -ItemType Directory -Path (Split-Path $release) -Force | Out-Null
    Copy-Item -LiteralPath $output -Destination $release -Force
    Write-Host "APK created: $release"
}
finally {
    Pop-Location
}
