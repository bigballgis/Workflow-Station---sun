<#
.SYNOPSIS
  One-shot: apply ConfigMap, Secret, then Istio manifests for deploy/k8s.
.DESCRIPTION
  Reads from:
    deploy/k8s/config_map/<Environment>/
    deploy/k8s/secret/<Environment>/
    deploy/k8s/*.yaml (via apply-workflow-station-istio-generated.ps1)

  Order: config_map -> secret -> istio-generated.

  Use -RenderOnly -OutputDir <root> to render all three groups without kubectl:
    <root>/config_map/, <root>/secret/, <root>/istio/
.PARAMETER Environment
  Subfolder name under config_map and secret (e.g. preprod, sit). Default: preprod.
.PARAMETER RenderOnly
  Render only; writes under -OutputDir as described above. Does not require kubectl for the render steps.
.PARAMETER OutputDir
  Required when -RenderOnly is set (root folder for the three subfolders).
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$Namespace,
  [Parameter(Mandatory = $true)]
  [string]$ImageTag,
  [string]$Environment = 'preprod',
  [string]$NamespaceToken = 'ame-hase-bisp-poc',
  [string]$BaseDomain = '',
  [string]$IngressHost = '',
  [string]$IngressTlsSecret = '',
  [string]$ImageRepositoryPrefix = '',
  [string[]]$Select = @(),
  [switch]$IncludeDeveloperWorkstation = $false,
  [switch]$DryRun = $false,
  [switch]$RenderOnly = $false,
  [string]$OutputDir = '',
  [switch]$InitializeDatabase = $false,
  [string]$DbHost = '',
  [int]$DbPort = 0,
  [string]$DbName = '',
  [string]$DbUser = '',
  [string]$DbPassword = '',
  [string]$DbSchema = '',
  [switch]$IncludeDemoData = $false,
  [switch]$ForceDatabaseInitialization = $false,
  [string]$IngressGatewayNamespace = 'istio-system',
  [switch]$SkipIngressTlsSecretCheck = $false,
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$SelectRemainder = @()
)
$ErrorActionPreference = 'Stop'
$Select = @($Select) + @($SelectRemainder)
$here = $PSScriptRoot

if ($RenderOnly) {
  if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    throw '-RenderOnly requires -OutputDir (root directory for config_map, secret, istio subfolders).'
  }
  $rootOut = $OutputDir.Trim()
  if (-not [System.IO.Path]::IsPathRooted($rootOut)) {
    $rootOut = Join-Path (Get-Location).Path $rootOut
  }
  [void][System.IO.Directory]::CreateDirectory($rootOut)
  $rootOut = (Resolve-Path -LiteralPath $rootOut).Path
  $cmOut = Join-Path $rootOut 'config_map'
  $secOut = Join-Path $rootOut 'secret'
  $istioOut = Join-Path $rootOut 'istio'
  [void][System.IO.Directory]::CreateDirectory($cmOut)
  [void][System.IO.Directory]::CreateDirectory($secOut)
  [void][System.IO.Directory]::CreateDirectory($istioOut)

  Write-Host "`n=== [1/3] ConfigMap (render) ===" -ForegroundColor Cyan
  & (Join-Path $here 'apply-workflow-station-configmap.ps1') `
    -Namespace $Namespace -NamespaceToken $NamespaceToken -BaseDomain $BaseDomain -IngressHost $IngressHost `
    -Environment $Environment -RenderOnly -OutputDir $cmOut

  Write-Host "`n=== [2/3] Secret (render) ===" -ForegroundColor Cyan
  & (Join-Path $here 'apply-workflow-station-secret.ps1') `
    -Namespace $Namespace -Environment $Environment -RenderOnly -OutputDir $secOut

  Write-Host "`n=== [3/3] Istio manifests (render) ===" -ForegroundColor Cyan
  $istioArgs = @{
    Namespace             = $Namespace
    ImageTag              = $ImageTag
    Environment           = $Environment
    BaseDomain            = $BaseDomain
    IngressHost           = $IngressHost
    IngressTlsSecret      = $IngressTlsSecret
    ImageRepositoryPrefix = $ImageRepositoryPrefix
    RenderOnly            = $true
    OutputDir             = $istioOut
  }
  if ($IncludeDeveloperWorkstation) {
    $istioArgs['IncludeDeveloperWorkstation'] = $true
  }
  if ($SkipIngressTlsSecretCheck) {
    $istioArgs['SkipIngressTlsSecretCheck'] = $true
  }
  if ($Select.Count -gt 0) {
    $istioArgs['Select'] = $Select
  }
  & (Join-Path $here 'apply-workflow-station-istio-generated.ps1') @istioArgs

  Write-Host "`nRenderOnly completed. Output root: $rootOut" -ForegroundColor Green
  Write-Host "  config_map -> $cmOut"
  Write-Host "  secret     -> $secOut"
  Write-Host "  istio      -> $istioOut"
  return
}

Write-Host "`n=== [1/3] ConfigMap (apply) ===" -ForegroundColor Cyan
& (Join-Path $here 'apply-workflow-station-configmap.ps1') `
  -Namespace $Namespace -NamespaceToken $NamespaceToken -BaseDomain $BaseDomain -IngressHost $IngressHost `
  -Environment $Environment

Write-Host "`n=== [2/3] Secret (apply) ===" -ForegroundColor Cyan
& (Join-Path $here 'apply-workflow-station-secret.ps1') `
  -Namespace $Namespace -Environment $Environment

Write-Host "`n=== [3/3] Istio manifests (apply) ===" -ForegroundColor Cyan
$istioArgs = @{
  Namespace               = $Namespace
  ImageTag                = $ImageTag
  Environment             = $Environment
  BaseDomain              = $BaseDomain
  IngressHost             = $IngressHost
  IngressTlsSecret        = $IngressTlsSecret
  ImageRepositoryPrefix   = $ImageRepositoryPrefix
  DbHost                  = $DbHost
  DbPort                  = $DbPort
  DbName                  = $DbName
  DbUser                  = $DbUser
  DbPassword              = $DbPassword
  DbSchema                = $DbSchema
  IngressGatewayNamespace = $IngressGatewayNamespace
}
if ($DryRun) {
  $istioArgs['DryRun'] = $true
}
if ($InitializeDatabase) {
  $istioArgs['InitializeDatabase'] = $true
}
if ($IncludeDeveloperWorkstation) {
  $istioArgs['IncludeDeveloperWorkstation'] = $true
}
if ($IncludeDemoData) {
  $istioArgs['IncludeDemoData'] = $true
}
if ($ForceDatabaseInitialization) {
  $istioArgs['ForceDatabaseInitialization'] = $true
}
if ($SkipIngressTlsSecretCheck) {
  $istioArgs['SkipIngressTlsSecretCheck'] = $true
}
if ($Select.Count -gt 0) {
  $istioArgs['Select'] = $Select
}
& (Join-Path $here 'apply-workflow-station-istio-generated.ps1') @istioArgs

Write-Host "`napply-workflow-station-all.ps1 finished." -ForegroundColor Green
