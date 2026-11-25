@echo off
setlocal
set "AUTIO_ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%AUTIO_ADB%" (
    echo Android platform tools were not found at "%AUTIO_ADB%".
    pause
    exit /b 1
)
echo Connect and unlock your Pixel with USB debugging enabled.
echo On the phone, open Android Auto settings, then More options,
echo and choose Start head unit server before continuing.
echo.
pause
"%AUTIO_ADB%" devices
"%AUTIO_ADB%" forward tcp:5277 tcp:5277
if errorlevel 1 (
    echo Could not connect to the phone. Check its USB debugging authorization.
    pause
    exit /b 1
)
pushd "%~dp0.tools\dhu"
desktop-head-unit.exe --adb=5277
popd
"%AUTIO_ADB%" forward --remove tcp:5277
echo.
echo When finished, stop the head unit server in Android Auto settings on the phone.
pause
endlocal
