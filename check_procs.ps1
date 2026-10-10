Get-Process | Sort-Object WS -Descending | Select-Object -First 12 ProcessName, Id, WS, PM | Format-Table -AutoSize
