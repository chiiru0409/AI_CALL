$ErrorActionPreference = 'Stop'
$sdkDir = "C:\Android\Sdk"
$toolsDir = Join-Path $sdkDir "cmdline-tools"
$zipFile = Join-Path $sdkDir "cmdline-tools.zip"

if (-not (Test-Path $sdkDir)) {
    New-Item -ItemType Directory -Force -Path $sdkDir | Out-Null
}

if (-not (Test-Path "$toolsDir\latest\bin\sdkmanager.bat")) {
    Write-Host "Downloading Android command-line tools..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $wc = New-Object Net.WebClient
    $wc.DownloadFile("https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip", $zipFile)
    
    Write-Host "Extracting command-line tools..."
    $tempDir = Join-Path $toolsDir "temp"
    Expand-Archive -Path $zipFile -DestinationPath $tempDir -Force
    
    if (-not (Test-Path $toolsDir)) {
        New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    }
    
    $extracted = Join-Path $tempDir "cmdline-tools"
    $target = Join-Path $toolsDir "latest"
    if (Test-Path $target) {
        Remove-Item -Recurse -Force $target
    }
    Move-Item -Path $extracted -Destination $target -Force
    
    Remove-Item -Force $zipFile -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
    Write-Host "Command-line tools installed at $target"
} else {
    Write-Host "Command-line tools already installed."
}
