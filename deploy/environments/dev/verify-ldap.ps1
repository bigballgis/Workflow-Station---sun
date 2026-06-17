$ErrorActionPreference = 'Stop'
$baseUrl = 'http://localhost:8090/api/v1/admin'
$outputDir = $PSScriptRoot
$loginBody = @{
    username = 'admin'
    password = 'admin123'
} | ConvertTo-Json
$loginResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/auth/login" `
    -ContentType 'application/json' `
    -Body $loginBody
$loginResponse | ConvertTo-Json -Depth 10 | Set-Content -Path (Join-Path $outputDir 'verify-ldap-login.json')
if (-not $loginResponse.accessToken) {
    throw 'Login did not return an access token.'
}
$headers = @{ Authorization = "Bearer $($loginResponse.accessToken)" }
$triggerResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/ldap-sync/full" `
    -Headers $headers
$triggerResponse | ConvertTo-Json -Depth 10 | Set-Content -Path (Join-Path $outputDir 'verify-ldap-trigger.json')
$statusResponse = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/ldap-sync/status" `
    -Headers $headers
$statusResponse | ConvertTo-Json -Depth 10 | Set-Content -Path (Join-Path $outputDir 'verify-ldap-status.json')