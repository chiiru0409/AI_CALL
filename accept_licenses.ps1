$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = "/c C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=C:\Android\Sdk --licenses"
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.UseShellExecute = $false

$p = [System.Diagnostics.Process]::Start($psi)
$sw = $p.StandardInput

for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Milliseconds 200
    $sw.WriteLine("y")
}
$sw.Close()
$out = $p.StandardOutput.ReadToEnd()
$p.WaitForExit()
Write-Host $out
