@echo off
cd /d "C:\Users\nhat6\OneDrive\Documents\DATN\datn\fileshareR-backend"
echo ===== COMPILE =====
call .\mvnw.cmd -q -DskipTests compile
if %errorlevel% neq 0 (
    echo Compile failed with error code %errorlevel%
    exit /b %errorlevel%
)
echo ===== TEST =====
call .\mvnw.cmd -q test
exit /b %errorlevel%
