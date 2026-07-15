param(
    [string]$Recipient = $env:SMTP_RECIPIENT,
    [string]$UseAuth = $env:SMTP_USE_AUTH
)

if ([string]::IsNullOrWhiteSpace($Recipient)) {
    $Recipient = Read-Host "Recipient email"
}

if ([string]::IsNullOrWhiteSpace($UseAuth)) {
    $UseAuth = Read-Host "Use SMTP auth? true/false (default true)"
    if ([string]::IsNullOrWhiteSpace($UseAuth)) { $UseAuth = "true" }
}

$env:SMTP_RECIPIENT = $Recipient
$env:SMTP_USE_AUTH = $UseAuth

if ([string]::IsNullOrWhiteSpace($env:SMTP_USE_TLS)) {
    if ($UseAuth -eq "true") {
        $env:SMTP_USE_TLS = "true"
        Write-Host "SMTP_USE_TLS not set; defaulting to true (authenticated relay expects STARTTLS on port 25)."
    }
}

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
