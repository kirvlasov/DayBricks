param([switch]$Connected)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $localJdk = Get-ChildItem -LiteralPath "$projectRoot/.tools" -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
    if (Test-Path -LiteralPath "$projectRoot/.tools/gradle-home") { $env:GRADLE_USER_HOME = "$projectRoot/.tools/gradle-home" }
    $tasks = @(':app:verifyDependencies', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', ':app:assembleDebugAndroidTest', '--dependency-verification=strict', '--console=plain')
    if ($Connected) { $tasks += ':app:connectedDebugAndroidTest' }
    & ./android/gradlew.bat -p android @tasks
    if ($LASTEXITCODE -ne 0) { throw 'Android checks failed' }
    $goCommand = if (Test-Path -LiteralPath "$projectRoot/.tools/go127/go/bin/go.exe") { "$projectRoot/.tools/go127/go/bin/go.exe" } else { (Get-Command go).Source }
    $gofmtCommand = Join-Path (Split-Path $goCommand) 'gofmt.exe'
    $env:GOCACHE = "$projectRoot/.tools/go-cache"
    Push-Location server
    try {
        $unformatted = & $gofmtCommand -l .
        if ($unformatted) { throw "Run gofmt on: $unformatted" }
        & $goCommand vet ./...
        if ($LASTEXITCODE -ne 0) { throw 'go vet failed' }
        & $goCommand test ./...
        if ($LASTEXITCODE -ne 0) { throw 'Go tests failed' }
    } finally { Pop-Location }
} finally { Pop-Location }
