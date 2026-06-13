@echo off
echo Building MediaCrop standalone .exe...
echo.

dotnet publish MediaCrop/MediaCrop.csproj ^
  -c Release ^
  -r win-x64 ^
  --self-contained true ^
  -p:PublishSingleFile=true ^
  -p:EnableCompressionInSingleFile=true ^
  -o publish\

if %errorlevel% neq 0 (
  echo.
  echo Build failed. Make sure the .NET 8 SDK is installed:
  echo   https://dotnet.microsoft.com/download/dotnet/8.0
  pause
  exit /b 1
)

echo.
echo Done! Standalone exe is at:
echo   %~dp0publish\MediaCrop.exe
echo.
pause
