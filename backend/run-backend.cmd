@echo off
setlocal

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

mvn spring-boot:run
