<#
.SYNOPSIS
	Render and delete manifests under .. with the same runtime overrides as apply-workflow-station-istio-generated.ps1.
.DESCRIPTION
	This script first renders the manifests from deploy\k8s-istio-generated using the same parameters
	as apply-workflow-station-istio-generated.ps1, then deletes the rendered resources with kubectl.
.PARAMETER Namespace
	Target Kubernetes namespace.
.PARAMETER ImageTag
	Image tag used when rendering manifests.
.PARAMETER BaseDomain
	Optional base domain used to replace `__BASE_DOMAIN__`.
.PARAMETER IngressHost
	Optional single ingress host used to replace `__INGRESS_HOST__`.
.PARAMETER IngressTlsSecret
	Optional TLS secret used to replace `__INGRESS_TLS_SECRET__`.
.PARAMETER ImageRepositoryPrefix
	Optional image repository prefix used during render.
.PARAMETER Select
	Optional subset of manifest files to delete. Supports exact filenames, basenames, and wildcards.
.PARAMETER IncludeDeveloperWorkstation
	Include developer workstation manifests in the default deletion set.
.PARAMETER DryRun
	Render and run `kubectl delete --dry-run=client`.
.PARAMETER RenderOnly
	Only render the processed YAML files and do not call kubectl delete.
.PARAMETER OutputDir
	Optional output directory for rendered manifests. If omitted, a temp directory is used.
.PARAMETER KeepRenderedFiles
	Keep rendered files after delete completes when a temp output directory is used.
#>
[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)]
	[string]$Namespace,
	[Parameter(Mandatory = $true)]
	[string]$ImageTag,
	[string]$BaseDomain = '',
	[string]$IngressHost = '',
	[string]$IngressTlsSecret = '',
	[string]$ImageRepositoryPrefix = '',
	[string[]]$Select = @(),
	[switch]$DryRun = $false,
	[switch]$IncludeDeveloperWorkstation = $false,
	[switch]$RenderOnly = $false,
	[string]$OutputDir = '',
	[switch]$KeepRenderedFiles = $false,
	[Parameter(ValueFromRemainingArguments = $true)]
	[string[]]$SelectRemainder = @()
)
$ErrorActionPreference = 'Stop'
$Select = @($Select) + @($SelectRemainder)
function Assert-Kubectl {
	if ($RenderOnly) {
		return
	}
	if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
		throw 'kubectl not found in PATH. Please install kubectl or add it to PATH.'
	}
}
function Ensure-OutputDirectory {
	param([string]$Dir)
	if ([string]::IsNullOrWhiteSpace($Dir)) {
		$timestamp = Get-Date -Format 'yyyyMMddHHmmss'
		$Dir = Join-Path ([System.IO.Path]::GetTempPath()) "workflow-station-istio-delete-$timestamp"
	}
	if (-not (Test-Path -LiteralPath $Dir)) {
		$null = New-Item -ItemType Directory -Path $Dir -Force
	}
	return (Resolve-Path -LiteralPath $Dir).Path
}
Assert-Kubectl
$applyScript = Join-Path $PSScriptRoot 'apply-workflow-station-istio-generated.ps1'
if (-not (Test-Path -LiteralPath $applyScript)) {
	throw "Required script not found: $applyScript"
}
$outputDirProvided = -not [string]::IsNullOrWhiteSpace($OutputDir)
$renderDir = Ensure-OutputDirectory -Dir $OutputDir
Write-Host "Namespace:             $Namespace"
Write-Host "ImageTag:              $ImageTag"
Write-Host "ImageRepositoryPrefix: $ImageRepositoryPrefix"
Write-Host "BaseDomain:            $BaseDomain"
Write-Host "IngressHost:           $IngressHost"
Write-Host "IngressTlsSecret:      $IngressTlsSecret"
Write-Host "IncludeDeveloperWs:    $IncludeDeveloperWorkstation"
Write-Host "DryRun:                $DryRun"
Write-Host "RenderOnly:            $RenderOnly"
Write-Host "RenderedOutput:        $renderDir"
$renderParams = @{
	Namespace = $Namespace
	ImageTag = $ImageTag
	RenderOnly = $true
	OutputDir = $renderDir
}
if (-not [string]::IsNullOrWhiteSpace($BaseDomain)) {
	$renderParams['BaseDomain'] = $BaseDomain
}
if (-not [string]::IsNullOrWhiteSpace($IngressHost)) {
	$renderParams['IngressHost'] = $IngressHost
}
if (-not [string]::IsNullOrWhiteSpace($IngressTlsSecret)) {
	$renderParams['IngressTlsSecret'] = $IngressTlsSecret
}
if (-not [string]::IsNullOrWhiteSpace($ImageRepositoryPrefix)) {
	$renderParams['ImageRepositoryPrefix'] = $ImageRepositoryPrefix
}
if ($IncludeDeveloperWorkstation) {
	$renderParams['IncludeDeveloperWorkstation'] = $true
}
if ($Select.Count -gt 0) {
	$renderParams['Select'] = $Select
}
Write-Host "`nRendering manifests for deletion..." -ForegroundColor Yellow
& $applyScript @renderParams | Out-Host
if ($RenderOnly) {
	Write-Host "`nRenderOnly completed. Rendered files are in: $renderDir" -ForegroundColor Green
	return
}
Write-Host "`nDeleting rendered manifests..." -ForegroundColor Yellow
if ($DryRun) {
	& kubectl delete --dry-run=client -f $renderDir --ignore-not-found=true | Out-Host
}
else {
	& kubectl delete -f $renderDir --ignore-not-found=true | Out-Host
}
if ((-not $outputDirProvided) -and (-not $KeepRenderedFiles) -and (Test-Path -LiteralPath $renderDir)) {
	Remove-Item -LiteralPath $renderDir -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host "`nDone." -ForegroundColor Green