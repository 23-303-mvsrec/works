try {
    $connStr = "Provider=Microsoft.ACE.OLEDB.12.0;Data Source=E:\works\ESCALATION_NEW_ZONE_CIRCLE.xls;Extended Properties='Excel 8.0;HDR=No;IMEX=1';"
    $conn = New-Object System.Data.OleDb.OleDbConnection($connStr)
    $conn.Open()
    Write-Output "--- Sheets (Tables) ---"
    $schemaTable = $conn.GetSchema("Tables")
    foreach ($row in $schemaTable.Rows) {
        Write-Output "Table Name: $($row['TABLE_NAME'])"
    }
    Write-Output ""

    foreach ($tblRow in $schemaTable.Rows) {
        $tableName = $tblRow['TABLE_NAME']
        Write-Output "=== Content of Table: $tableName ==="
        $cmd = New-Object System.Data.OleDb.OleDbCommand("SELECT * FROM [$tableName]", $conn)
        $da = New-Object System.Data.OleDb.OleDbDataAdapter($cmd)
        $dt = New-Object System.Data.DataTable
        $da.Fill($dt) | Out-Null
        
        for ($r = 0; $r -lt $dt.Rows.Count; $r++) {
            $rowVals = @()
            for ($c = 0; $c -lt $dt.Columns.Count; $c++) {
                $rowVals += $dt.Rows[$r][$c].ToString()
            }
            Write-Output ($rowVals -join "`t")
        }
        Write-Output "========================================"
    }
    $conn.Close()
} catch {
    Write-Output "Error: $_"
}
