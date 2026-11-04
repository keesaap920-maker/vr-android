$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
Write-Host 'VisionOS Mobile - Windows build'
if (!(Test-Path 'app\src\main\assets\hand_landmarker.task')) {
    Write-Host 'Hand model absent. Downloading official model...'
    & "$PSScriptRoot\get-hand-model.ps1"
}
& .\gradlew.bat :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Android build failed: $LASTEXITCODE" }
Write-Host 'APK: app\build\outputs\apk\debug\app-debug.apk'
