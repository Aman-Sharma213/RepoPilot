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
# Paths and demo configuration
# Change TargetRepo only if your Kafka clone is elsewhere.
# ------------------------------------------------------------

$Tool = $PSScriptRoot
$Jar = Join-Path $Tool "target\repo-pilot.jar"

$TargetRepo = "C:\Users\abc\Downloads\repo-pilot-targets\kafka"

$Pr = 1402
$Owner = "apache"
$RepoName = "kafka"

# ------------------------------------------------------------
# Repo Pilot wrapper
# ------------------------------------------------------------

function RepoPilot {
    & java `
        "-Dfile.encoding=UTF-8" `
        "-Dstdout.encoding=UTF-8" `
        "-Dstderr.encoding=UTF-8" `
        --enable-native-access=ALL-UNNAMED `
        -jar $Jar @args

    if ($LASTEXITCODE -ne 0) {
        throw "Repo Pilot failed with exit code $LASTEXITCODE"
    }
}

# ------------------------------------------------------------
# Demo
# ------------------------------------------------------------

try {
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " Repo Pilot 1.1 - Apache Kafka PR #1402 Demo"
    Write-Host "============================================================"
    Write-Host ""

    # --------------------------------------------------------
    # 1. Check tools and application JAR
    # --------------------------------------------------------

    Write-Host "[1/6] Checking Java, Git, and Repo Pilot JAR..."

    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw "Java was not found in PATH. Install JDK 21 or newer."
    }

    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw "Git was not found in PATH. Install Git first."
    }

    if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) {
        throw "Repo Pilot JAR not found at '$Jar'. Run 'mvn clean package' from the project root."
    }

    Write-Host "      OK: $Jar"

    # --------------------------------------------------------
    # 2. Check target repository
    # --------------------------------------------------------

    Write-Host ""
    Write-Host "[2/6] Checking Kafka repository..."

    if (-not (Test-Path -LiteralPath $TargetRepo -PathType Container)) {
        throw "Kafka repository not found at '$TargetRepo'. Update TargetRepo in this script."
    }

    git -C "$TargetRepo" rev-parse --git-dir > $null

    if ($LASTEXITCODE -ne 0) {
        throw "'$TargetRepo' is not a valid Git repository."
    }

    Write-Host "      OK: $TargetRepo"

    # --------------------------------------------------------
    # 3. Show application version
    # --------------------------------------------------------

    Write-Host ""
    Write-Host "[3/6] Repo Pilot version..."

    RepoPilot --version

    # --------------------------------------------------------
    # 4. Check existing index workspace
    # --------------------------------------------------------

    Write-Host ""
    Write-Host "[4/6] Checking existing Kafka Repo Pilot workspace..."

    $IndexDir = Join-Path $TargetRepo ".repo-pilot"

    if (-not (Test-Path -LiteralPath $IndexDir -PathType Container)) {
        Write-Host ""
        Write-Host "No Repo Pilot workspace was found."
        Write-Host "Run these commands from the Repo Pilot project root:"
        Write-Host ""
        Write-Host ".\bin\repo-pilot.ps1 init -r `"$TargetRepo`""
        Write-Host ".\bin\repo-pilot.ps1 index -r `"$TargetRepo`""
        Write-Host ""

        throw "Prepare and index the Kafka repository before running this demo."
    }

    Write-Host "      Workspace found."
    Write-Host "      Reusing the existing index."
    Write-Host "      Indexing is skipped for this demo."

    # --------------------------------------------------------
    # 5. Read GitHub PR metadata and verify commits
    # --------------------------------------------------------

    Write-Host ""
    Write-Host "[5/6] Reading Apache Kafka PR #$Pr..."

    $Headers = @{
        Accept = "application/vnd.github+json"
        "User-Agent" = "repo-pilot-demo"
    }

    if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_TOKEN)) {
        $Headers.Authorization = "Bearer $env:GITHUB_TOKEN"
    }

    $Metadata = Invoke-RestMethod `
        -Uri "https://api.github.com/repos/$Owner/$RepoName/pulls/$Pr" `
        -Headers $Headers

    $Base = [string]$Metadata.base.sha
    $Head = [string]$Metadata.head.sha

    if ([string]::IsNullOrWhiteSpace($Base) -or
            [string]::IsNullOrWhiteSpace($Head)) {
        throw "GitHub did not return valid base and head commit IDs."
    }

    Write-Host ""
    Write-Host "PR #$($Metadata.number)"
    Write-Host "Title : $($Metadata.title)"
    Write-Host "State : $($Metadata.state)"
    Write-Host "URL   : $($Metadata.html_url)"
    Write-Host ""
    Write-Host "Base  : $Base"
    Write-Host "Head  : $Head"

    Write-Host ""
    Write-Host "Fetching PR #$Pr..."

    git -C "$TargetRepo" fetch origin `
        "+refs/pull/${Pr}/head:refs/remotes/origin/pr-${Pr}"

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to fetch PR #$Pr. Check the origin remote and network connection."
    }

    git -C "$TargetRepo" fetch origin $Base

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to fetch the base commit."
    }

    Write-Host "Verifying base commit..."

    git -C "$TargetRepo" cat-file -e "${Base}^{commit}"

    if ($LASTEXITCODE -ne 0) {
        throw "Base commit verification failed."
    }

    Write-Host "Verifying head commit..."

    git -C "$TargetRepo" cat-file -e "${Head}^{commit}"

    if ($LASTEXITCODE -ne 0) {
        throw "Head commit verification failed."
    }

    Write-Host "Base and head verified."

    # --------------------------------------------------------
    # Show actual PR changed files
    # --------------------------------------------------------

    Write-Host ""
    Write-Host "============================================================"
    Write-Host " ACTUAL PR CHANGES"
    Write-Host "============================================================"
    Write-Host ""

    git -C "$TargetRepo" diff --name-status $Base $Head

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to list the PR changed files."
    }

    # --------------------------------------------------------
    # 6. Run Repo Pilot PR analysis
    # --------------------------------------------------------

    Write-Host ""
    Write-Host "[6/6] Running Repo Pilot impact analysis..."
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " REPO PILOT IMPACT ANALYSIS"
    Write-Host "============================================================"
    Write-Host ""

    RepoPilot pr `
        -r "$TargetRepo" `
        --org $Owner `
        --repo $RepoName `
        --pr $Pr `
        --radius 2

    # --------------------------------------------------------
    # Finished
    # --------------------------------------------------------

    Write-Host ""
    Write-Host "============================================================"
    Write-Host " DEMO COMPLETE"
    Write-Host "============================================================"
    Write-Host ""

    Write-Host "PR #$Pr - $($Metadata.title)"
    Write-Host ""
    Write-Host "The demonstration showed:"
    Write-Host ""
    Write-Host "  1. Actual PR changed files"
    Write-Host "  2. Deterministic changed targets"
    Write-Host "  3. Directly modified symbols"
    Write-Host "  4. Indirectly affected symbols"
    Write-Host "  5. Test impact"
    Write-Host "  6. Analysis coverage"
    Write-Host "  7. Unresolved/dynamic relationship warnings"
    Write-Host ""
    Write-Host "This script did not run a Kafka Maven build or tests."
    Write-Host "Groq description-based planning is a separate demo."
    Write-Host ""

    exit 0
}
catch {
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " DEMO FAILED"
    Write-Host "============================================================"
    Write-Host ""
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""

    exit 1
}