@echo off
setlocal
set "ROOT=%~dp0"
set "JAR=%ROOT%gradle\wrapper\gradle-wrapper.jar"
if not exist "%JAR%" (
    echo Downloading Gradle Wrapper 8.9 from the official Gradle source repository...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar' -OutFile '%JAR%'; $hash=(Get-FileHash '%JAR%' -Algorithm SHA256).Hash.ToLower(); if ($hash -ne '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17') {throw 'Wrapper hash mismatch'}"
    if errorlevel 1 exit /b 1
)
java -cp "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %errorlevel%
