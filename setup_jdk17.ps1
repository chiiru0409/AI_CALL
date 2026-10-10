$targetDir = "C:\Android\jdk-17"
$zipPath = "C:\Android\jdk17.zip"

if (-not (Test-Path "$targetDir\bin\javac.exe")) {
    Write-Host "Downloading Temurin OpenJDK 17 LTS..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $url = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip"
    (New-Object Net.WebClient).DownloadFile($url, $zipPath)

    Write-Host "Extracting JDK 17..."
    $tempExtract = "C:\Android\jdk17_temp"
    Expand-Archive -Path $zipPath -DestinationPath $tempExtract -Force
    $innerDir = Get-ChildItem $tempExtract | Select-Object -First 1 -ExpandProperty FullName
    
    if (Test-Path $targetDir) {
        Remove-Item -Recurse -Force $targetDir
    }
    Move-Item -Path $innerDir -Destination $targetDir -Force
    
    Remove-Item -Force $zipPath -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $tempExtract -ErrorAction SilentlyContinue
    Write-Host "JDK 17 installed successfully at $targetDir"
} else {
    Write-Host "JDK 17 already exists at $targetDir"
}
