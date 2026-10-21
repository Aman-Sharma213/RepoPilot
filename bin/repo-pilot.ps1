#$ErrorActionPreference = "Stop"
#$Jar = Join-Path (Split-Path $PSScriptRoot -Parent) "target\repo-pilot.jar"
#if (-not (Test-Path $Jar)) {
#  Write-Error "repo-pilot.jar not found. Build it first: mvn package"
#  exit 1
#}
#& java --enable-native-access=ALL-UNNAMED -jar $Jar @args
#exit $LASTEXITCODE


$ErrorActionPreference = "Stop"

# ------------------------------------------------------------
# UTF-8 configuration for PowerShell and Java output
# ------------------------------------------------------------

chcp.com 65001 > $null

$Utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $Utf8
[Console]::OutputEncoding = $Utf8
$OutputEncoding = $Utf8

# ------------------------------------------------------------
# Locate the application JAR
# ------------------------------------------------------------

$ProjectRoot = Split-Path $PSScriptRoot -Parent
$Jar = Join-Path $ProjectRoot "target\repo-pilot.jar"

if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) {
    Write-Host ""
    Write-Host "ERROR: Repo Pilot JAR not found:"
    Write-Host $Jar
    Write-Host ""
    Write-Host "Run this command from the project root:"
    Write-Host "mvn clean package"
    exit 1
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: Java was not found in PATH."
    Write-Host "Install JDK 21 or newer and check JAVA_HOME/PATH."
    exit 1
}

# ------------------------------------------------------------
# Forward all arguments to Repo Pilot
# JVM options must appear before -jar.
# ------------------------------------------------------------

& java `
    "-Dfile.encoding=UTF-8" `
    "-Dstdout.encoding=UTF-8" `
    "-Dstderr.encoding=UTF-8" `
    --enable-native-access=ALL-UNNAMED `
    -jar $Jar @args

exit $LASTEXITCODE