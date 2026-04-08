#!/usr/bin/env pwsh
# =====================================================
# K8S Deployment Script
# =====================================================
# Usage:
#   .\deploy.ps1 -Environment sit
#   .\deploy.ps1 -Environment uat -Namespace workflow-platform-uat
#   .\deploy.ps1 -Environment prod -Namespace workflow-platform-prod -DryRun
#
# Ingress host: set INGRESS_HOST in configmap-<env>.yaml, or override with -IngressHost.
# =====================================================

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("sit", "uat", "prod")]
    [string]$Environment,

    [string]$Namespace = "workflow-platform-$Environment",
    [string]$Registry = "harbor.company.com/workflow",
    [string]$Tag = "latest",
    # Ingress host override for single-domain multi-path deployments.
    # Example: -IngressHost workflow.company.com
    [string]$IngressHost = "",
    [switch]$DryRun = $false
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$runId = [guid]::NewGuid().ToString("N")

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
Write-Host "  IngressHost: $IngressHost"
Write-Host "  DryRun:    $DryRun"
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Note: developer-workstation is DEV-only and is NOT deployed for SIT/UAT/PROD." -ForegroundColor DarkYellow

# Check kubectl
try { $null = Get-Command kubectl -ErrorAction Stop }
catch { Write-Fail "kubectl not found. Install Kubernetes CLI." }

$dryRunFlag = if ($DryRun) { "--dry-run=client" } else { "" }

# Step 1: Create namespace
Write-Step "Ensuring namespace $Namespace exists..."
$null = kubectl get namespace $Namespace 2>&1
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

# Try to read INGRESS_HOST from configmap-<env>.yaml if -IngressHost not provided.
if (-not $IngressHost) {
    try {
        $cfg = Get-Content $configmapFile -Raw
        $m = [regex]::Match($cfg, '^\s*INGRESS_HOST:\s*"?([^"\r\n]+)"?\s*$', [System.Text.RegularExpressions.RegexOptions]::Multiline)
        if ($m.Success) {
            $IngressHost = $m.Groups[1].Value.Trim()
            Write-Ok "Ingress host from ConfigMap: $IngressHost"
        } else {
            Write-Host "   WARN: INGRESS_HOST not found in configmap-$Environment.yaml; using default template host." -ForegroundColor DarkYellow
        }
    } catch {
        Write-Host "   WARN: Failed to read INGRESS_HOST from configmap-$Environment.yaml; using default template host." -ForegroundColor DarkYellow
    }
}

# Step 3: Apply Secrets
Write-Step "Applying Secrets for $Environment..."
$secretFile = Join-Path $ScriptDir "secret-$Environment.yaml"
if (-not (Test-Path $secretFile)) { Write-Fail "Secret not found: $secretFile" }
kubectl apply -f $secretFile -n $Namespace $dryRunFlag
if ($LASTEXITCODE -ne 0) { Write-Fail "Failed to apply Secrets" }
Write-Ok "Secrets applied"

# Step 3.5: Create Kong declarative config ConfigMap (from files)
Write-Step "Creating Kong declarative config ConfigMap..."
$kongDir = Join-Path (Split-Path $ScriptDir -Parent) "kong"
$kongTemplate = Join-Path $kongDir "kong.yml.template"
$kongEntrypoint = Join-Path $kongDir "docker-entrypoint-kong.sh"
if (-not (Test-Path $kongTemplate)) { Write-Fail "Kong template not found: $kongTemplate" }
if (-not (Test-Path $kongEntrypoint)) { Write-Fail "Kong entrypoint not found: $kongEntrypoint" }
kubectl create configmap kong-declarative-config `
    --from-file=kong.yml.template=$kongTemplate `
    --from-file=docker-entrypoint-kong.sh=$kongEntrypoint `
    -n $Namespace --dry-run=client -o yaml | kubectl apply -f - -n $Namespace $dryRunFlag
if ($LASTEXITCODE -ne 0) { Write-Fail "Failed to create Kong ConfigMap" }
Write-Ok "Kong declarative config ConfigMap created"

# Step 4: Apply Deployments
Write-Step "Applying Deployments..."
$deploymentFiles = @(
    "deployment-redis.yaml",
    "deployment-kafka.yaml",
    "deployment-n8n.yaml",
    "deployment-workflow-engine.yaml",
    "deployment-admin-center.yaml",
    "deployment-user-portal.yaml",
    "deployment-kong.yaml",
    "deployment-frontend.yaml",
    "deployment-platform-login-frontend.yaml",
    "pdb.yaml"
)

foreach ($file in $deploymentFiles) {
    $filePath = Join-Path $ScriptDir $file
    if (-not (Test-Path $filePath)) {
        Write-Host "   SKIP: $file (not found)" -ForegroundColor DarkYellow
        continue
    }

    # Replace namespace and image registry/tag
    $content = Get-Content $filePath -Raw
    $content = $content -replace "namespace: workflow-platform-\w+", "namespace: $Namespace"
    $content = $content -replace "harbor\.company\.com/workflow", $Registry
    $content = $content -replace ":latest", ":$Tag"

    $tempFile = Join-Path $env:TEMP "k8s-deploy-$runId-$file"
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
    if ($IngressHost) {
        $content = $content -replace "__INGRESS_HOST__", $IngressHost
    } else {
        # Backward-compatible default: derive from the template's workflow-sit.* host.
        $content = $content -replace "workflow-sit\.", "workflow-$Environment."
        $content = $content -replace "__INGRESS_HOST__", "workflow-$Environment.your-domain.com"
    }

    $tempFile = Join-Path $env:TEMP "k8s-deploy-$runId-ingress.yaml"
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
if ($IngressHost) {
    Write-Host "Ingress host:" -ForegroundColor Yellow
    Write-Host "  $IngressHost" -ForegroundColor Gray
    Write-Host ""
}
Write-Host "Verify:" -ForegroundColor Yellow
Write-Host "  kubectl get pods -n $Namespace" -ForegroundColor Gray
Write-Host "  kubectl get svc -n $Namespace" -ForegroundColor Gray
Write-Host "  kubectl get ingress -n $Namespace" -ForegroundColor Gray
