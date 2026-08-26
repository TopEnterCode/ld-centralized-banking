$ErrorActionPreference = 'Stop'
if (-not $env:POC_MODE) { $env:POC_MODE = 'mock' }
docker compose up --build

