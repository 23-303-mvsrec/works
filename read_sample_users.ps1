$xlsxPath = "e:\works\sample users.xlsx"
$tempDir = "e:\works\xlsx_extracted_json"

if (Test-Path $tempDir) {
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}

Copy-Item $xlsxPath "$tempDir.zip"
Expand-Archive -Path "$tempDir.zip" -DestinationPath $tempDir -Force
Remove-Item "$tempDir.zip" -ErrorAction SilentlyContinue

$sharedStrings = @()
$sharedStringsFile = Join-Path $tempDir "xl\sharedStrings.xml"
if (Test-Path $sharedStringsFile) {
    $xml = [xml](Get-Content $sharedStringsFile -Raw)
    $nodes = $xml.SelectNodes("//*[local-name()='t']")
    foreach ($n in $nodes) {
        $sharedStrings += $n.InnerText
    }
}

$sheetFile = Join-Path $tempDir "xl\worksheets\sheet1.xml"
if (-not (Test-Path $sheetFile)) {
    Write-Error "sheet1.xml not found"
    exit 1
}

$xml = [xml](Get-Content $sheetFile -Raw)
$ns = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
$ns.AddNamespace("x", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
$rows = $xml.SelectNodes("//x:sheetData/x:row", $ns)
if ($rows -eq $null -or $rows.Count -eq 0) {
    $rows = $xml.SelectNodes("//*[local-name()='row']")
}

function Get-ColLetter($cellRef) {
    if ($cellRef -match "^([A-Z]+)") {
        return $Matches[1]
    }
    return ""
}

$users = @{}

foreach ($row in $rows) {
    $rowNum = [int]$row.r
    if ($rowNum -eq 1) { continue }
    
    $cols = $row.SelectNodes("x:c", $ns)
    if ($cols -eq $null -or $cols.Count -eq 0) {
        $cols = $row.SelectNodes("*[local-name()='c']")
    }
    
    $rowData = @{}
    foreach ($col in $cols) {
        $colLetter = Get-ColLetter $col.r
        $val = ""
        $vNode = $col.SelectSingleNode("x:v", $ns)
        if ($vNode -eq $null) {
            $vNode = $col.SelectSingleNode("*[local-name()='v']")
        }
        if ($vNode -ne $null) {
            $val = $vNode.InnerText
            if ($col.t -eq "s") {
                $idx = [int]$val
                if ($idx -lt $sharedStrings.Count) {
                    $val = $sharedStrings[$idx]
                }
            }
        }
        $rowData[$colLetter] = $val.Trim()
    }
    
    $dopName = $rowData["B"]
    $dopPhone = $rowData["C"]
    $zoneName = $rowData["D"]
    $cgmName = $rowData["E"]
    $cgmPhone = $rowData["F"]
    $division = $rowData["G"]
    $gmName = $rowData["H"]
    $gmPhone = $rowData["I"]
    $circleName = $rowData["J"]
    $dgmName = $rowData["K"]
    $dgmPhone = $rowData["L"]
    $managerName = $rowData["M"]
    $managerPhone = $rowData["N"]
    $wardName = $rowData["O"]
    
    # 1. DOP
    if ($dopPhone) {
        if (-not $users.ContainsKey($dopPhone)) {
            $users[$dopPhone] = @{
                phoneNumber = $dopPhone
                name = $dopName
                designation = "Director of Project (DOP)"
                role = "DOP"
                locations = @()
            }
        }
        $loc = @{ corp = "Corporate"; zoneName = $null; division = $null; circleName = $null; wardName = $null; role = "DOP" }
        $exists = $false
        foreach ($l in $users[$dopPhone].locations) {
            if ($l.corp -eq "Corporate") { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$dopPhone].locations += $loc
        }
    }
    
    # 2. CGM
    if ($cgmPhone) {
        if (-not $users.ContainsKey($cgmPhone)) {
            $users[$cgmPhone] = @{
                phoneNumber = $cgmPhone
                name = $cgmName
                designation = "Chief General Manager (CGM)"
                role = "CGM"
                locations = @()
            }
        }
        $loc = @{ corp = "MMC"; zoneName = $zoneName; division = $null; circleName = $null; wardName = $null; role = "CGM" }
        $exists = $false
        foreach ($l in $users[$cgmPhone].locations) {
            if ($l.corp -eq "MMC" -and $l.zoneName -eq $zoneName) { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$cgmPhone].locations += $loc
        }
    }
    
    # 3. GM
    if ($gmPhone) {
        if (-not $users.ContainsKey($gmPhone)) {
            $users[$gmPhone] = @{
                phoneNumber = $gmPhone
                name = $gmName
                designation = "General Manager (GM)"
                role = "GM"
                locations = @()
            }
        }
        $loc = @{ corp = "MMC"; zoneName = $zoneName; division = $division; circleName = $null; wardName = $null; role = "GM" }
        $exists = $false
        foreach ($l in $users[$gmPhone].locations) {
            if ($l.corp -eq "MMC" -and $l.zoneName -eq $zoneName -and $l.division -eq $division) { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$gmPhone].locations += $loc
        }
    }
    
    # 4. DGM
    if ($dgmPhone) {
        if (-not $users.ContainsKey($dgmPhone)) {
            $users[$dgmPhone] = @{
                phoneNumber = $dgmPhone
                name = $dgmName
                designation = "Deputy General Manager (DGM)"
                role = "DGM"
                locations = @()
            }
        }
        $loc = @{ corp = "MMC"; zoneName = $zoneName; division = $division; circleName = $circleName; wardName = $null; role = "DGM" }
        $exists = $false
        foreach ($l in $users[$dgmPhone].locations) {
            if ($l.corp -eq "MMC" -and $l.zoneName -eq $zoneName -and $l.division -eq $division -and $l.circleName -eq $circleName) { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$dgmPhone].locations += $loc
        }
    }
    
    # 5. MANAGER
    if ($managerPhone) {
        if (-not $users.ContainsKey($managerPhone)) {
            $users[$managerPhone] = @{
                phoneNumber = $managerPhone
                name = $managerName
                designation = "Manager (AE)"
                role = "MANAGER"
                locations = @()
            }
        }
        $loc = @{ corp = "MMC"; zoneName = $zoneName; division = $division; circleName = $circleName; wardName = $wardName; role = "MANAGER" }
        $exists = $false
        foreach ($l in $users[$managerPhone].locations) {
            if ($l.corp -eq "MMC" -and $l.zoneName -eq $zoneName -and $l.division -eq $division -and $l.circleName -eq $circleName -and $l.wardName -eq $wardName) { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$managerPhone].locations += $loc
        }
    }
}

$usersList = @()
foreach ($k in $users.Keys) {
    $usersList += $users[$k]
}

$json = ConvertTo-Json $usersList -Depth 5
$json | Out-File "e:\works\users_parsed.json" -Encoding utf8

Write-Output "Successfully parsed and saved $($users.Count) unique users to e:\works\users_parsed.json"

foreach ($k in $users.Keys) {
    $u = $users[$k]
    Write-Output "User: $($u.name) ($($u.phoneNumber)) - Role: $($u.role) - Locs: $($u.locations.Count)"
}

Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
