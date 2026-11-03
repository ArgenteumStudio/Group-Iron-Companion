param(
    [string]$ClientJar
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Definition
$arguments = @('--console=plain', 'jar', 'verifyFeatures')
if ($ClientJar) {
    $resolvedJar = (Resolve-Path -LiteralPath $ClientJar).Path
    $arguments += "-PclientJar=$resolvedJar"
}
Push-Location $project
try {
    & (Join-Path $project 'gradlew.bat') @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
    Write-Host "Built and checked: $project\build\libs\bowlsofmayo-0.2.0.jar"
    Write-Host 'This does not install or launch the plugin. See CONTRIBUTING.md for the development launcher.'
}
finally {
    Pop-Location
}
