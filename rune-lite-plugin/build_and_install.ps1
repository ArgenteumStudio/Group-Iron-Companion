param(
    [string]$InstallDir
)

$project = Split-Path -Parent $MyInvocation.MyCommand.Definition
Write-Host "Building plugin in $project"

# Build with local mvn if available
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvn) {
    Write-Host "Using local Maven"
    Push-Location $project
    mvn -DskipTests package
    Pop-Location
} else {
    # Fallback to Docker if available
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        Write-Host "Maven not found; building with Docker (requires Docker Desktop)"
        # Convert Windows path (C:\...) to Docker-friendly /c/... form
        $projPath = $project -replace '\\','/'
        if ($projPath -match '^[A-Za-z]:') {
            $drive = $projPath.Substring(0,1).ToLower()
            $rest = $projPath.Substring(2)
            if ($rest.StartsWith('/')) { $rest = $rest.Substring(1) }
            $projPathUnix = "/$drive/$rest"
        } else {
            $projPathUnix = $projPath
        }
        docker run --rm -v "$($projPathUnix):/work" -w /work maven:3.9.4-openjdk-8 mvn -DskipTests package
    } else {
        Write-Error "Neither Maven nor Docker found. Install Maven or Docker to build the plugin."
        exit 1
    }
}

# Find produced jar
$target = Join-Path $project 'target'
if (-Not (Test-Path $target)) { Write-Error "target directory not found"; exit 1 }
$jar = Get-ChildItem -Path $target -Filter '*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-Not $jar) { Write-Error "Built jar not found in target"; exit 1 }

Write-Host "Built: $($jar.FullName)"

if (-not $InstallDir) {
    $InstallDir = Read-Host -Prompt 'Enter RuneLite plugins folder path to copy jar to (or leave empty to skip)'
}

if ($InstallDir -and (Test-Path $InstallDir)) {
    $dest = Join-Path $InstallDir $jar.Name
    Copy-Item -Path $jar.FullName -Destination $dest -Force
    Write-Host "Copied jar to $dest"
} elseif ($InstallDir) {
    Write-Error "Install directory not found: $InstallDir"
    exit 1
} else {
    Write-Host "No install directory provided; build complete. Jar is at: $($jar.FullName)"
}
