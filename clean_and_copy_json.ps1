$jsonPath = "e:\works\users_parsed.json"
$destDir = "e:\works\src\main\resources"
$destPath = Join-Path $destDir "users_parsed.json"

if (-not (Test-Path $destDir)) {
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
}

Copy-Item $jsonPath $destPath -Force
Write-Output "Copied users JSON to $destPath"
