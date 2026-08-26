@echo off
setlocal
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)

where docker >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
  echo Maven is not installed and Docker is unavailable. 1>&2
  exit /b 1
)

docker build --target build-env -t centralized-banking-build-env:java21-node22 . >nul
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
docker run --rm -v "%CD%:/workspace" -v "centralized-banking-m2:/root/.m2" -w /workspace centralized-banking-build-env:java21-node22 mvn %*
exit /b %ERRORLEVEL%
