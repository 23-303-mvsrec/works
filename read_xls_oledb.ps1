try {
    $connStr = "Provider=Microsoft.ACE.OLEDB.12.0;Data Source=E:\works\ESCALATION_NEW_ZONE_CIRCLE.xls;Extended Properties='Excel 8.0;HDR=Yes;IMEX=1';"
    $conn = New-Object System.Data.OleDb.OleDbConnection($connStr)
    $conn.Open()
    Write-Output "OLEDB 12.0 Connection Successful!"
    $cmd = New-Object System.Data.OleDb.OleDbCommand("SELECT * FROM [Sheet1$]", $conn)
    $da = New-Object System.Data.OleDb.OleDbDataAdapter($cmd)
    $dt = New-Object System.Data.DataTable
    $da.Fill($dt) | Out-Null
    foreach ($row in $dt.Rows) {
        Write-Output "$($row[0])`t$($row[1])`t$($row[2])`t$($row[3])"
    }
    $conn.Close()
} catch {
    Write-Output "OLEDB 12.0 failed: $_"
    try {
        $connStr = "Provider=Microsoft.Jet.OLEDB.4.0;Data Source=E:\works\ESCALATION_NEW_ZONE_CIRCLE.xls;Extended Properties='Excel 8.0;HDR=Yes;IMEX=1';"
        $conn = New-Object System.Data.OleDb.OleDbConnection($connStr)
        $conn.Open()
        Write-Output "OLEDB 4.0 Connection Successful!"
        $cmd = New-Object System.Data.OleDb.OleDbCommand("SELECT * FROM [Sheet1$]", $conn)
        $da = New-Object System.Data.OleDb.OleDbDataAdapter($cmd)
        $dt = New-Object System.Data.DataTable
        $da.Fill($dt) | Out-Null
        foreach ($row in $dt.Rows) {
            Write-Output "$($row[0])`t$($row[1])`t$($row[2])`t$($row[3])"
        }
        $conn.Close()
    } catch {
        Write-Output "OLEDB 4.0 failed: $_"
    }
}
