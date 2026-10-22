param([switch]$SkipRegressions)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
try {
    if (-not $SkipRegressions) {
        & ./gradlew.bat paintRegression textEditRegression installerRegression storageRegression browserRegression browserNavigationRegression browserFormsRegression
        if ($LASTEXITCODE -ne 0) { throw 'Guest screenshot regressions failed.' }
    }
    $classes = (Resolve-Path 'build/classes/storageRegression').Path
    & java --class-path $classes tools/wiki/WikiScreenshots.java
    if ($LASTEXITCODE -ne 0) { throw 'Guest screenshot capture failed.' }
    $names = @(
        'dos-shell', 'dos-help', 'dos-directory', 'dos-editor', 'dos-document',
        'dos-image', 'dos-music', 'dos-jvs-run', 'dos-com-run', 'desktop-files',
        'jctrl-dos', 'jctrl-source', 'jctrl-image', 'jctrl-paint', 'textedit-jvs',
        'jctrl-setup', 'web-browser', 'web-links', 'web-form', 'jctrl-pong'
    )
    foreach ($name in $names) {
        if (-not (Test-Path "build/$name.png")) { throw "Missing capture: $name.png" }
    }
    foreach ($name in $names) {
        Copy-Item -LiteralPath "build/$name.png" -Destination "docs/wiki/images/$name.png"
    }
    Write-Output "Refreshed $($names.Count) guest captures. Inspect images before syncing the wiki."
} finally {
    Pop-Location
}
