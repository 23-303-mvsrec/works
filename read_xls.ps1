$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false
$filePath = "e:\works\ESCALATION_NEW_ZONE_CIRCLE.xls"
$workbook = $excel.Workbooks.Open($filePath)

foreach ($sheet in $workbook.Sheets) {
    Write-Output "=== SHEET: $($sheet.Name) ==="
    $usedRange = $sheet.UsedRange
    $rowCount = $usedRange.Rows.Count
    $colCount = $usedRange.Columns.Count
    
    for ($r = 1; $r -le $rowCount; $r++) {
        $rowVals = @()
        for ($c = 1; $c -le $colCount; $c++) {
            $val = $usedRange.Cells.Item($r, $c).Text
            $rowVals += $val
        }
        Write-Output ($rowVals -join "`t")
    }
}

$workbook.Close($false)
$excel.Quit()
[System.Runtime.Interopservices.Marshal]::ReleaseComObject($excel) | Out-Null
