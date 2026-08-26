$ErrorActionPreference = 'Stop'
& .\mvnw.cmd clean verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$violations = Get-ChildItem -Recurse -Filter pom.xml |
  Where-Object { $_.FullName -notlike '*\dtm-service\pom.xml' -and $_.FullName -ne (Join-Path $PWD 'pom.xml') } |
  Select-String -Pattern 'launchdarkly-java-server-sdk'
if ($violations) {
  $violations | Write-Output
  throw 'LaunchDarkly server SDK dependency found outside dtm-service'
}
Write-Output 'Validation and dependency boundary checks passed.'

