@echo off
setlocal

cd /d "%~dp0"

echo Starting PostgreSQL...
docker compose up -d postgres
if errorlevel 1 exit /b 1

echo.
echo Starting backend with Java 17...
cd backend
call run-backend.cmd
