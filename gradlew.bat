@echo off
setlocal
set GRADLE_VERSION=9.5.0
set GRADLE_SHA256=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set BASE_DIR=%~dp0
set ROOT_DIR=%GRADLE_USER_HOME%\userhunter-gradle
set INSTALL_DIR=%ROOT_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%INSTALL_DIR%\bin\gradle.bat
if not exist "%GRADLE_BIN%" (
  if not exist "%ROOT_DIR%" mkdir "%ROOT_DIR%"
  set ARCHIVE=%ROOT_DIR%\gradle-%GRADLE_VERSION%-bin.zip
  set EXTRACT_DIR=%ROOT_DIR%\extract-%GRADLE_VERSION%
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ROOT_DIR%\gradle-%GRADLE_VERSION%-bin.zip'"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$actual=(Get-FileHash -Algorithm SHA256 -LiteralPath '%ROOT_DIR%\gradle-%GRADLE_VERSION%-bin.zip').Hash.ToLower(); if ($actual -ne '%GRADLE_SHA256%') { Write-Error 'Gradle archive checksum mismatch'; exit 1 }"
  if errorlevel 1 (
    del /q "%ROOT_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
    exit /b 1
  )
  if exist "%EXTRACT_DIR%" rmdir /s /q "%EXTRACT_DIR%"
  if exist "%INSTALL_DIR%" rmdir /s /q "%INSTALL_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ROOT_DIR%\gradle-%GRADLE_VERSION%-bin.zip' -DestinationPath '%EXTRACT_DIR%' -Force"
  if errorlevel 1 exit /b 1
  move "%EXTRACT_DIR%\gradle-%GRADLE_VERSION%" "%INSTALL_DIR%" >nul
  rmdir /s /q "%EXTRACT_DIR%"
)
call "%GRADLE_BIN%" -p "%BASE_DIR%" %*
exit /b %ERRORLEVEL%
