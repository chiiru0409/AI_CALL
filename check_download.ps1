$f = "C:\Android\jdk17.zip"
if (Test-Path $f) {
    $s1 = (Get-Item $f).Length
    Start-Sleep -Seconds 3
    $s2 = (Get-Item $f).Length
    $growth = $s2 - $s1
    Write-Host "Initial: $s1 bytes | After 3s: $s2 bytes | Growth: $growth bytes"
    if ($s2 -ge 170000000) {
        Write-Host "Download is practically complete ($([math]::Round($s2/1MB, 2)) MB)"
    } else {
        Write-Host "Current size: $([math]::Round($s2/1MB, 2)) MB"
    }
} else {
    Write-Host "File does not exist"
}
