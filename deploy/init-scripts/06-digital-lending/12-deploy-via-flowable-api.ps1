# Deploy BPMN via Flowable REST API
# This properly deploys the BPMN and creates a new process definition version

Write-Host "=== Deploying BPMN via Flowable REST API ===" -ForegroundColor Cyan

# Read the BPMN file
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$bpmnPath = Join-Path $scriptDir "digital-lending-process.bpmn"

if (-not (Test-Path $bpmnPath)) {
    Write-Host "✗ BPMN file not found: $bpmnPath" -ForegroundColor Red
    exit 1
}

Write-Host "Reading BPMN file: $bpmnPath" -ForegroundColor Yellow

# Flowable REST API endpoint
$flowableUrl = "http://localhost:8081/flowable-rest/service/repository/deployments"

# Create multipart form data
$boundary = [System.Guid]::NewGuid().ToString()
$LF = "`r`n"

$bodyLines = (
    "--$boundary",
    "Content-Disposition: form-data; name=`"deployment`"; filename=`"digital-lending-process.bpmn`"",
    "Content-Type: application/xml$LF",
    (Get-Content $bpmnPath -Raw),
    "--$boundary--$LF"
) -join $LF

try {
    Write-Host "Deploying to Flowable REST API..." -ForegroundColor Yellow
    
    $response = Invoke-RestMethod -Uri $flowableUrl `
        -Method Post `
        -ContentType "multipart/form-data; boundary=$boundary" `
        -Body $bodyLines
    
    Write-Host "✓ Deployment successful!" -ForegroundColor Green
    Write-Host "Deployment ID: $($response.id)" -ForegroundColor Green
    Write-Host "Deployment Name: $($response.name)" -ForegroundColor Green
    Write-Host "Deployment Time: $($response.deploymentTime)" -ForegroundColor Green
    
    # Wait a moment for Flowable to process
    Start-Sleep -Seconds 3
    
    # Verify new version
    Write-Host "`nVerifying new process definition version..." -ForegroundColor Yellow
    docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT id_, key_, version_, deployment_id_ FROM act_re_procdef WHERE key_ = 'DigitalLendingProcess' ORDER BY version_ DESC LIMIT 3;"
    
    Write-Host "`n=== Deployment Complete ===" -ForegroundColor Green
    Write-Host "New process instances will use the latest version with updated actionIds" -ForegroundColor Green
    
} catch {
    Write-Host "✗ Deployment failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Error details: $($_.ErrorDetails.Message)" -ForegroundColor Red
    exit 1
}
