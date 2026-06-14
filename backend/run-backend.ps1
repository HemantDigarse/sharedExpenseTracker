$candidateHomes = @(
    'D:\Java17\jdk-17.0.12'
) + @(Get-ChildItem 'C:\Program Files\Java' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) `
  + @(Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)

$java17Home = $candidateHomes | Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1

if (-not $java17Home) {
    Write-Error 'Java 17 was not found. Install Java 17, then set JAVA_HOME to that JDK before running Maven.'
    exit 1
}

$env:JAVA_HOME = $java17Home
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:SPRING_DOCKER_COMPOSE_ENABLED = 'true'

Write-Host 'Using Java:'
& "$env:JAVA_HOME\bin\java.exe" -version
Write-Host ''
Write-Host 'Maven runtime:'
mvn -version
Write-Host ''

mvn spring-boot:run
