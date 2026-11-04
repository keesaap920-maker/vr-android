$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root 'app\src\main\assets\hand_landmarker.task'
$url = 'https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task'
Write-Host 'Downloading official MediaPipe hand landmarker model...'
Invoke-WebRequest -Uri $url -OutFile $dest
if ((Get-Item $dest).Length -lt 1000000) { throw 'Model file looks too small.' }
Write-Host "Model saved to $dest"
