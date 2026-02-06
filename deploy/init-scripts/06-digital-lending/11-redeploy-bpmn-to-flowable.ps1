# Redeploy Digital Lending BPMN to Flowable
# This creates a new deployment (version 8) with updated actionIds

Write-Host "=== Redeploying Digital Lending BPMN to Flowable ===" -ForegroundColor Cyan

# Read the BPMN file (use absolute path from script location)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$bpmnPath = Join-Path $scriptDir "digital-lending-process.bpmn"
$bpmnContent = Get-Content $bpmnPath -Raw

# Escape single quotes for SQL
$escapedContent = $bpmnContent -replace "'", "''"

# Generate new deployment ID (UUID format)
$deploymentId = [guid]::NewGuid().ToString()

Write-Host "New Deployment ID: $deploymentId" -ForegroundColor Yellow

# SQL to create new deployment
$sql = @"
-- Create new deployment
INSERT INTO act_re_deployment (id_, name_, deploy_time_, category_, tenant_id_, derived_from_, derived_from_root_)
VALUES ('$deploymentId', 'Digital Lending Process v8', CURRENT_TIMESTAMP, NULL, '', NULL, NULL);

-- Insert BPMN resource into bytearray
INSERT INTO act_ge_bytearray (id_, rev_, name_, deployment_id_, bytes_, generated_)
VALUES (
    '${deploymentId}_bpmn',
    1,
    'DIGITAL_LENDING.bpmn',
    '$deploymentId',
    '$escapedContent'::bytea,
    false
);

-- Flowable will automatically create the process definition when the engine restarts
-- or when the deployment is processed
"@

# Execute SQL
Write-Host "Inserting new deployment into Flowable tables..." -ForegroundColor Yellow
$sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Deployment created successfully" -ForegroundColor Green
    
    # Restart Workflow Engine to process the new deployment
    Write-Host "`nRestarting Workflow Engine to process new deployment..." -ForegroundColor Yellow
    docker restart platform-workflow-engine-dev
    
    Write-Host "`nWaiting for Workflow Engine to start..." -ForegroundColor Yellow
    Start-Sleep -Seconds 10
    
    # Verify new version
    Write-Host "`nVerifying new process definition version..." -ForegroundColor Yellow
    docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT id_, key_, version_, deployment_id_ FROM act_re_procdef WHERE key_ = 'DigitalLendingProcess' ORDER BY version_ DESC LIMIT 3;"
    
    Write-Host "`n=== Deployment Complete ===" -ForegroundColor Green
    Write-Host "New process instances will use version 8 with updated actionIds" -ForegroundColor Green
    Write-Host "Existing process instances will continue using their original version" -ForegroundColor Yellow
} else {
    Write-Host "✗ Deployment failed" -ForegroundColor Red
    exit 1
}
