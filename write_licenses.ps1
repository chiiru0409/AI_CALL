$licPaths = @(
    "C:\Android\Sdk\licenses",
    "$env:LOCALAPPDATA\Android\Sdk\licenses",
    "$env:USERPROFILE\.android\licenses"
)

$androidSdkLicense = @"
8933bad161af4178b1185d1a37fbf41ea5269c55
d56f518747942eab0fddf0a7b4f3b7d686a85d21
24333f8a63b1d97323810ab2dd9139642221b179
84831b9409646a918e30573bab4c9c91346d8abd
601085b94cd77f0b54ff86406955dce825227d63
33b6a2b649202164f1a4f4d3f540632ea8d2f458
e9acab5b5f1556e4ed510f080e442400a45e337b
e6b7c2ab33f948c6a56e174ee14ed7f1cb343763
"@

$androidPreviewLicense = @"
84831b9409646a918e30573bab4c9c91346d8abd
"@

$androidGoogletvLicense = @"
601085b94cd77f0b54ff86406955dce825227d63
"@

$googleGdkLicense = @"
33b6a2b649202164f1a4f4d3f540632ea8d2f458
"@

$mipsLicense = @"
e9acab5b5f1556e4ed510f080e442400a45e337b
"@

foreach ($dir in $licPaths) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    Set-Content -Path (Join-Path $dir "android-sdk-license") -Value $androidSdkLicense -NoNewline
    Set-Content -Path (Join-Path $dir "android-sdk-preview-license") -Value $androidPreviewLicense -NoNewline
    Set-Content -Path (Join-Path $dir "android-googletv-license") -Value $androidGoogletvLicense -NoNewline
    Set-Content -Path (Join-Path $dir "google-gdk-license") -Value $googleGdkLicense -NoNewline
    Set-Content -Path (Join-Path $dir "mips-android-sysimage-license") -Value $mipsLicense -NoNewline
}

Write-Host "All Android SDK licenses written successfully."
