param(
    [string]$Recipient = $env:SMTP_RECIPIENT,
    [string]$UseAuth = $env:SMTP_USE_AUTH
)

if ([string]::IsNullOrWhiteSpace($Recipient)) {
    $Recipient = Read-Host "Recipient email"
}

if ([string]::IsNullOrWhiteSpace($UseAuth)) {
    $UseAuth = Read-Host "Use SMTP auth? true/false"
}

$env:SMTP_RECIPIENT = $Recipient
$env:SMTP_USE_AUTH = $UseAuth

if ($UseAuth -eq "true" -and [string]::IsNullOrWhiteSpace($env:SMTP_PASSWORD)) {
    $securePassword = Read-Host "SMTP password" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $env:SMTP_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

mvn clean compile exec:java
