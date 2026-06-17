$path = "e:\works\.m2"
$item = Get-Item $path
Write-Output "Path: $path"
Write-Output "Exists: $($item.Exists)"
Write-Output "Target: $($item.Target)"
Write-Output "Target Type: $($item.Target.GetType().FullName)"
Write-Output "Target count: $($item.Target.Count)"
foreach ($t in $item.Target) {
    Write-Output "  Sub-target: $t"
}
