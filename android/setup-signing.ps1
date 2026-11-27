# Run once from Windows PowerShell after installing GitHub CLI and signing in with `gh auth login`.
# This creates a persistent signing key locally and stores it as private GitHub Actions secrets.
$ErrorActionPreference = "Stop"
$repo = "hennawy55/talker"
$keyPath = Join-Path $PSScriptRoot "talker-release.jks"
$keyAlias = "talker"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool was not found. Install JDK 17 and ensure its bin folder is on PATH."
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) was not found. Install it from https://cli.github.com/."
}
gh auth status | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Sign in first with: gh auth login" }
if (Test-Path $keyPath) {
    throw "A keystore already exists at $keyPath. Do not overwrite it; losing the original key breaks app updates."
}

$storeSecure = Read-Host "Create keystore password" -AsSecureString
$keySecure = Read-Host "Create key password (can be the same)" -AsSecureString
$storePtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storeSecure)
$keyPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keySecure)
try {
    $storePass = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($storePtr)
    $keyPass = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPtr)
    & keytool -genkeypair -v -keystore $keyPath -storetype JKS -alias $keyAlias -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Talker Offline, OU=Android, O=Talker, L=Cairo, ST=Cairo, C=EG" -storepass $storePass -keypass $keyPass
    if ($LASTEXITCODE -ne 0) { throw "keytool failed." }

    $bytes = [IO.File]::ReadAllBytes($keyPath)
    $base64 = [Convert]::ToBase64String($bytes)
    gh secret set TALKER_KEYSTORE_BASE64 --repo $repo --body $base64
    gh secret set TALKER_KEYSTORE_PASSWORD --repo $repo --body $storePass
    gh secret set TALKER_KEY_ALIAS --repo $repo --body $keyAlias
    gh secret set TALKER_KEY_PASSWORD --repo $repo --body $keyPass
    if ($LASTEXITCODE -ne 0) { throw "GitHub secret upload failed. Keep the keystore safe and retry." }
    Write-Host "Persistent signing key configured. Back up this file securely: $keyPath"
    Write-Host "Do not delete or replace the keystore. Future APKs will use it for in-place updates."
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($storePtr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPtr)
}
