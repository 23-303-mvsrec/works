try {
    $connStr = "Provider=Microsoft.ACE.OLEDB.12.0;Data Source=E:\works\ESCALATION_NEW_ZONE_CIRCLE.xls;Extended Properties='Excel 8.0;HDR=Yes;IMEX=1';"
    $conn = New-Object System.Data.OleDb.OleDbConnection($connStr)
    $conn.Open()
} catch {
    $connStr = "Provider=Microsoft.Jet.OLEDB.4.0;Data Source=E:\works\ESCALATION_NEW_ZONE_CIRCLE.xls;Extended Properties='Excel 8.0;HDR=Yes;IMEX=1';"
    $conn = New-Object System.Data.OleDb.OleDbConnection($connStr)
    $conn.Open()
}

$cmd = New-Object System.Data.OleDb.OleDbCommand("SELECT * FROM [Table 1$]", $conn)
$da = New-Object System.Data.OleDb.OleDbDataAdapter($cmd)
$dt = New-Object System.Data.DataTable
$da.Fill($dt) | Out-Null
$conn.Close()

$jurisdictions = @()

foreach ($row in $dt.Rows) {
    # Column names are based on headers: CORP, Zone Name, DIVISION, Circle No. & Name, Ward No. & Name
    # We retrieve them by index or header name. Let's retrieve by column names.
    $corp = $row["CORP"].ToString().Trim()
    $zone = $row["Zone Name"].ToString().Trim()
    $division = $row["DIVISION"].ToString().Trim()
    $circle = $row["Circle No# & Name"].ToString().Trim()
    if (!$circle) {
        $circle = $row["Circle No. & Name"].ToString().Trim()
    }
    $ward = $row["Ward No# & Name"].ToString().Trim()
    if (!$ward) {
        $ward = $row["Ward No. & Name"].ToString().Trim()
    }

    if ($corp -and $corp -ne "+") {
        $item = @{
            corp = $corp
            zoneName = $zone
            division = $division
            circleName = $circle
            wardName = $ward
        }
        $jurisdictions += $item
    }
}

$json = ConvertTo-Json $jurisdictions -Depth 5
$json | Out-File "E:\works\src\main\resources\jurisdictions.json" -Encoding utf8
Write-Output "Successfully extracted $($jurisdictions.Count) jurisdictions to src/main/resources/jurisdictions.json"
