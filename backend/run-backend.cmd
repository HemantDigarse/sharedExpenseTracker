@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "JAVA17_HOME="

if exist "D:\Java17\jdk-17.0.12\bin\java.exe" set "JAVA17_HOME=D:\Java17\jdk-17.0.12"

if not defined JAVA17_HOME (
  for /d %%D in ("C:\Program Files\Java\jdk-17*") do (
    if exist "%%~fD\bin\java.exe" set "JAVA17_HOME=%%~fD"
  )
)

if not defined JAVA17_HOME (
  for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do (
    if exist "%%~fD\bin\java.exe" set "JAVA17_HOME=%%~fD"
  )
)

if not defined JAVA17_HOME (
  echo Java 17 was not found.
  echo Install Java 17, then set JAVA_HOME to that JDK before running Maven.
  exit /b 1
)

set "JAVA_HOME=%JAVA17_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Using JAVA_HOME=%JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
echo.
echo Maven runtime:
mvn -version
echo.

echo Checking PostgreSQL on localhost:5432...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$c = New-Object Net.Sockets.TcpClient; try { $c.Connect('localhost', 5432); exit 0 } catch { exit 1 } finally { $c.Close() }"
if errorlevel 1 (
  echo PostgreSQL is not running. Starting Docker postgres service...
  docker compose -f "%ROOT_DIR%\docker-compose.yml" up -d postgres
  if errorlevel 1 (
    echo.
    echo Could not start PostgreSQL with Docker.
    echo Start PostgreSQL manually or run from the repository root:
    echo   docker compose up -d postgres
    exit /b 1
  )

  echo Waiting for PostgreSQL to accept connections...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline = (Get-Date).AddSeconds(60); do { try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('localhost', 5432); $c.Close(); exit 0 } catch { Start-Sleep -Seconds 2 } } while ((Get-Date) -lt $deadline); exit 1"
  if errorlevel 1 (
    echo PostgreSQL did not become ready within 60 seconds.
    echo Check Docker with:
    echo   docker compose -f "%ROOT_DIR%\docker-compose.yml" logs postgres
    exit /b 1
  )
) else (
  echo PostgreSQL is already running.
)
echo.

mvn spring-boot:run
