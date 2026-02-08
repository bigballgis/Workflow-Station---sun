# =====================================================
# K8S Deployment Script
# =====================================================
# Deploys all K8S resources for the specified environment.
#
# Usage:
#   .\deploy.ps1 -Environment sit
#   .\deploy.ps1 -Environment uat -Namespace workflow-platform-uat
#   .\deploy.ps1 -Environment prod -Namespace workflow-platform-prod -DryRun
# =====================================================

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("sit", "uat", "prod")]
    [string]$Environment,

    [string]$Namespace = "workflow-platform-$Environment",
    [string]$Registry = "harbor.company.com/workflow",
    [string]$Tag = "latest",
    [switch]$DryRun = $false
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Step { param([string]$Msg) Write-Host "`n>> $Msg" -ForegroundColor Cyan }
function Write-Ok { param([string]$Msg) Write-Host "   OK: $Msg" -ForegroundColor Green }
function Write-Fail { param([string]$Msg) Write-Host "   FAIL: $Msg" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  K8S Deployment - $Environment" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Namespace: $Namespace"
Write-Host "  Registry:  $Registry"
Write-Host "  Tag:       $Tag"
Write-Host "  DryRun:    $DryRun"
Write-Host "=========================================" -ForegroundColor Yellow

# Check kubectl
try { $null = Get-Command kubectl -ErrorAction Stop }
catch { Write-Fail "kubectl not found. Install Kubernetes CLI." }

$dryRunFlag = if ($DryRun) { "--dry-run=client" } else { "" }

# Step 1: Create namespace if not exists
Write-Step "Ensuring namespace $Namespace exists..."
$nsExists = kubectl get namespace $Namespace 2>&1
if ($LASTEXITCODE -ne 0) {
    kubectl create namespace $Namespace $dryRunFlag
    Write-Ok "Namespace created: $Namespace"
} else {
    Write-Ok "Namespace already exists: $Namespace"
}

# Step 2: Apply ConfigMap
Write-Step "Applying ConfigMap for $Environment..."
$configmapFile = Join-Path $ScriptDir "configmap-$Environment.yaml"
if (-not (Test-Path $configmapFile)) { Write-Fail "ConfigMap not found: $configmapFile" }
kubectl apply -f $configmapFile -n $Namespace $dryRunFlag
if ($LASTEXITCODE -ne 0) { Write-Fail "Failed to apply ConfigMap" }
Write-Ok "ConfigMap applied"

# Step 3: Apply Secrets
Write-Step "Applying Secrets for $Environment..."
$secretFile = Join-Path $ScriptDir "secret-$Environment.yaml"
if (-not (Test-Path $secretFile)) { Write-Fail "Secret not found: $secretFile" }
kubectl apply -f $secretFile -n $Namespace $dryRunFlag
if ($LASTEXITCODE -ne 0) { Write-Fail "Failed to apply Secrets" }
Write-Ok "Secrets applied"

# Step 4: Update image tags in deployments and apply
Write-Step "Applying Deployments..."
$deploymentFiles = @(
    "deployment-workflow-engine.yaml",
    "deployment-admin-center.yaml",
    "deployment-user-portal.yaml",
    "deployment-developer-workstation.yaml",
    "deployment-api-gateway.yaml",
    "deployment-frontend.yaml"
)

foreach ($file in $deploymentFiles) {
    $filePath = Join-Path $ScriptDir $file
    if (-not (Test-Path $filePath)) {
        Write-Host "   SKIP: $file (not found)" -ForegroundColor DarkYellow
        continue
    }

    # Read, replace namespace and image registry/tag, then apply
    $content = Get-Content $filePath -Raw
    $content = $content -replace "namespace: workflow-platform-\w+", "namespace: $Namespace"
    $content = $content -replace "harbor\.company\.com/workflow", $Registry
    $content = $content -replace ":latest", ":$Tag"

    $tempFile = Join-Path $env:TEMP "k8s-deploy-$file"
    $content | Set-Content $tempFile -Encoding UTF8

    kubectl apply -f $tempFile -n $Namespace $dryRunFlag
    if ($LASTEXITCODE -ne 0) { Write-Fail "Failed to apply $file" }
    Remove-Item $tempFile -Force
    Write-Ok $file
}

# Step 5: Apply Ingress
Write-Step "Applying Ingress..."
$ingressFile = Join-Path $ScriptDir "ingress.yaml"
if (Test-Path $ingressFile) {
    $content = Get-Content $ingressFile -Raw
    $content = $content -replace "namespace: workflow-platform-\w+", "namespace: $Namespace"
    $content = $content -replace "-sit\.", "-$Environment."

    $tempFile = Join-Path $env:TEMP "k8s-deploy-ingress.yaml"
    $content | Set-Content $tempFile -Encoding UTF8

    kubectl apply -f $tempFile -n $Namespace $dryRunFlag
    if ($LASTEXITCODE -ne 0) { Write-Fail "Failed to apply Ingress" }
    Remove-Item $tempFile -Force
    Write-Ok "Ingress applied"
} else {
    Write-Host "   SKIP: ingress.yaml (not found)" -ForegroundColor DarkYellow
}

# Done
Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Deployment Complete! ($Environment)" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Verify with:" -ForegroundColor Yellow
Write-Host "  kubectl get pods -n $Namespace" -ForegroundColor Gray
Write-Host "  kubectl get svc -n $Namespace" -ForegroundColor Gray
Write-Host "  kubectl get ingress -n $Namespace" -ForegroundColor Gray
Write-Host ""
