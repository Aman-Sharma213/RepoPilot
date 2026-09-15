$ErrorActionPreference = "Stop"
$Jar = Join-Path (Split-Path $PSScriptRoot -Parent) "target\repo-pilot.jar"
if (-not (Test-Path $Jar)) {
  Write-Error "repo-pilot.jar not found. Build it first: mvn package"
  exit 1
}
& java --enable-native-access=ALL-UNNAMED -jar $Jar @args
exit $LASTEXITCODE
