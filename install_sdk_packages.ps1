$sdkDir = "C:\Android\Sdk"
$sdkManager = "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat"

Write-Host "Accepting licenses..."
$processInfo = New-Object System.Diagnostics.ProcessStartInfo
$processInfo.FileName = $sdkManager
$processInfo.Arguments = "--sdk_root=$sdkDir --licenses"
$processInfo.RedirectStandardInput = $true
$processInfo.RedirectStandardOutput = $true
$processInfo.UseShellExecute = $false
$process = [System.Diagnostics.Process]::Start($processInfo)

$sw = $process.StandardInput
for ($i = 0; $i -lt 20; $i++) {
    $sw.WriteLine("y")
}
$sw.Close()
$process.WaitForExit()

Write-Host "Installing platform-tools, platforms;android-34, build-tools;34.0.0..."
& $sdkManager "--sdk_root=$sdkDir" "platforms;android-34" "build-tools;34.0.0" "platform-tools"

Write-Host "SDK Packages installation completed!"
