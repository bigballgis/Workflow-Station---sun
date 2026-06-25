<#
.SYNOPSIS
	Render and deploy manifests under .. with runtime namespace and image overrides.
.DESCRIPTION
	This script reads YAML manifests from deploy\k8s, replaces:
		- every `namespace:` value with `-Namespace`
		- optional `__NAMESPACE__` placeholders with `-Namespace`
		- every container image tag with `-ImageTag`
		- optional `__IMAGE_TAG__` placeholders with `-ImageTag`
		- optional `__BASE_DOMAIN__` placeholders with `-BaseDomain`
		- optional `__INGRESS_HOST__` placeholders with `-IngressHost`
		- optional `__INGRESS_TLS_SECRET__` placeholders with `-IngressTlsSecret`
		- optional image repository prefix with `-ImageRepositoryPrefix`
	The rendered files are written to a temporary folder, then applied with kubectl.
.PARAMETER Namespace
	Target Kubernetes namespace.
.PARAMETER ImageTag
	Image tag to use for every `image:` line in the selected manifests.
.PARAMETER BaseDomain
	Optional base domain used to replace `__BASE_DOMAIN__`.
.PARAMETER IngressHost
	Optional single ingress host used to replace `__INGRESS_HOST__`.
.PARAMETER IngressTlsSecret
	Optional TLS secret on the Istio ingressgateway used to replace `__INGRESS_TLS_SECRET__`.
.PARAMETER ImageRepositoryPrefix
	Optional image repository prefix. Example:
		nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2
	When provided, the script rebuilds each image as:
		<ImageRepositoryPrefix>/<image-name>:<ImageTag>
.PARAMETER Select
	Optional subset of manifest files to apply. Supports exact filenames, basenames, and wildcards.
.PARAMETER IncludeDeveloperWorkstation
	Include developer workstation manifests in the default deployment set.
.PARAMETER DryRun
	Render and run `kubectl apply --dry-run=client`.
.PARAMETER RenderOnly
	Only render the processed YAML files and do not call kubectl.
.PARAMETER OutputDir
	Optional output directory for rendered manifests. If omitted, a temp directory is used.
.PARAMETER Environment
	Subfolder under config_map and secret used when resolving paths for -InitializeDatabase (default: preprod).
.PARAMETER InitializeDatabase
	Before applying Kubernetes manifests, initialize the external PostgreSQL database once.
	When explicit DB parameters are omitted, the script derives them from
	deploy/k8s/config_map/<Environment>/configmap-workflow-platform-config.yml and
	deploy/k8s/secret/<Environment>/secret-workflow-paltform.yml (default Environment: preprod).
.PARAMETER DbSchema
	Target PostgreSQL schema for init-scripts. When omitted, the script tries to parse
	`currentSchema` from `SPRING_DATASOURCE_URL`; otherwise defaults to `public`.
.PARAMETER IncludeDemoData
	When used together with -InitializeDatabase, also run the destructive demo seed steps
	from init-database.ps1. By default IKP initialization skips demo data.
.PARAMETER ForceDatabaseInitialization
	Run init-database.ps1 even if the marker table already exists in the target schema.
.EXAMPLE
	.\apply-workflow-station-istio-generated.ps1 `
		-Namespace workflow-platform-sit `
		-ImageTag sit-20260320 `
		-IngressHost hermes-sit.hk.hsbc `
		-IngressTlsSecret workflow-platform-tls `
		-BaseDomain ikp402xsm.cloud.hk.hsbc
.EXAMPLE
	.\apply-workflow-station-istio-generated.ps1 `
		-Namespace workflow-platform-uat `
		-ImageTag uat-20260401 `
		-IngressHost workflow-uat.your-domain.com `
		-IngressTlsSecret workflow-platform-tls `
		-BaseDomain ikp402xsm.cloud.hk.hsbc `
		-Select admin-center,admin-center-frontend
.EXAMPLE
	.\apply-workflow-station-istio-generated.ps1 `
		-Namespace workflow-platform-sit `
		-ImageTag sit-20260320 `
		-IngressHost hermes-sit.hk.hsbc `
		-IngressTlsSecret workflow-platform-tls `
		-RenderOnly `
		-OutputDir ..\rendered\sit
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
	[string]$IngressGatewayNamespace = 'istio-system',
	[switch]$SkipIngressTlsSecretCheck = $false,
	[string]$ImageRepositoryPrefix = '',
	[string[]]$Select = @(),
	[switch]$DryRun = $false,
	[switch]$IncludeDeveloperWorkstation = $false,
	# Non-prod only: include the Activepieces login-bridge UI gateway (ap-gateway.yaml).
	# PROD is runtime-only (no AP UI) — leave this off there.
	[switch]$IncludeApBridgeGateway = $false,
	[switch]$InitializeDatabase = $false,
	[string]$DbHost = '',
	[int]$DbPort = 0,
	[string]$DbName = '',
	[string]$DbUser = '',
	[string]$DbPassword = '',
	[string]$DbSchema = '',
	[switch]$IncludeDemoData = $false,
	[switch]$ForceDatabaseInitialization = $false,
	[switch]$RenderOnly = $false,
	[string]$OutputDir = '',
	[string]$Environment = 'preprod',
	[Parameter(ValueFromRemainingArguments = $true)]
	[string[]]$SelectRemainder = @()
)
$ErrorActionPreference = 'Stop'
$Select = @($Select) + @($SelectRemainder)
$defaultExcludedManifestNames = @(
	'developer-workstation.yaml',
	'developer-workstation-frontend.yaml',
	# AP login-bridge UI gateway + shared-account bootstrap Job — non-prod only;
	# opt in with -IncludeApBridgeGateway.
	'ap-gateway.yaml',
	'ap-bootstrap-job.yaml'
)
function Normalize-ParameterValue {
	param(
		[AllowNull()][string]$Value,
		[Parameter(Mandatory = $true)][string]$ParameterName
	)
	if ($null -eq $Value) {
		return ''
	}
	$normalized = $Value.Trim()
	$sanitized = $normalized.Trim([char[]]@([char]96, [char]34, [char]39))
	if ($sanitized -ne $normalized) {
		Write-Warning "$ParameterName contains surrounding markdown/quote characters. Using sanitized value: $sanitized"
	}
	return $sanitized.Trim()
}
function Assert-ValidK8sTokenValue {
	param(
		[AllowNull()][string]$Value,
		[Parameter(Mandatory = $true)][string]$ParameterName,
		[string]$ExpectedHint = 'a plain value without markdown backticks, quotes, or spaces'
	)
	if ([string]::IsNullOrWhiteSpace($Value)) {
		return
	}
	if ($Value -match '[`"'']') {
		throw "$ParameterName contains invalid characters: '$Value'. Please pass $ExpectedHint."
	}
	if ($Value -match '\s') {
		throw "$ParameterName contains whitespace: '$Value'. Please pass $ExpectedHint."
	}
}
$BaseDomain = Normalize-ParameterValue -Value $BaseDomain -ParameterName 'BaseDomain'
$IngressHost = Normalize-ParameterValue -Value $IngressHost -ParameterName 'IngressHost'
$IngressTlsSecret = Normalize-ParameterValue -Value $IngressTlsSecret -ParameterName 'IngressTlsSecret'
Assert-ValidK8sTokenValue -Value $BaseDomain -ParameterName 'BaseDomain' -ExpectedHint 'a plain domain suffix such as ikp402xsm.cloud.hk.hsbc'
Assert-ValidK8sTokenValue -Value $IngressHost -ParameterName 'IngressHost' -ExpectedHint 'a plain host name such as hermes-sit.hk.hsbc'
Assert-ValidK8sTokenValue -Value $IngressTlsSecret -ParameterName 'IngressTlsSecret' -ExpectedHint 'a plain Kubernetes secret name such as workflow-platform-tls'
function Assert-Kubectl {
	if ($RenderOnly) {
		return
	}
	if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
		throw 'kubectl not found in PATH. Please install kubectl or add it to PATH.'
	}
}
function Assert-Psql {
	if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
		throw 'psql not found in PATH. Please install PostgreSQL client tools or add psql to PATH.'
	}
}
function Assert-IngressTlsSecret {
	param(
		[string]$SecretName,
		[string]$GatewayNamespace,
		[string]$ApplicationNamespace,
		[switch]$SkipCheck
	)
	if ($SkipCheck -or $RenderOnly -or [string]::IsNullOrWhiteSpace($SecretName)) {
		return
	}
	$canGetSecret = & kubectl auth can-i get "secret/$SecretName" -n $GatewayNamespace 2>$null
	if ($LASTEXITCODE -ne 0) {
		Write-Warning "Unable to verify IngressTlsSecret '$SecretName' in namespace '$GatewayNamespace'."
		return
	}
	if ($canGetSecret.Trim().ToLowerInvariant() -eq 'yes') {
		$null = & kubectl -n $GatewayNamespace get secret $SecretName -o name 2>$null
		if ($LASTEXITCODE -ne 0) {
			throw "IngressTlsSecret '$SecretName' was not found in namespace '$GatewayNamespace'. HTTPS on '$IngressHost' will fail until the secret exists on the Istio ingressgateway namespace."
		}
		Write-Host "IngressTlsSecret verified: $GatewayNamespace/$SecretName" -ForegroundColor Green
		return
	}
	$null = & kubectl -n $ApplicationNamespace get secret $SecretName -o name 2>$null
		if ($LASTEXITCODE -eq 0) {
			Write-Warning "IngressTlsSecret '$SecretName' exists in application namespace '$ApplicationNamespace', but the ingress gateway expects it in '$GatewayNamespace'."
			return
		}
	Write-Warning "RBAC does not allow checking secret '$SecretName' in namespace '$GatewayNamespace'. If '$IngressHost' resets the TLS handshake, ask the platform team to confirm the secret exists there."
}
function Get-ManifestFiles {
	param(
		[Parameter(Mandatory = $true)][string]$Dir
	)
	if (-not (Test-Path -LiteralPath $Dir)) {
		return @()
	}
	Get-ChildItem -LiteralPath $Dir -File |
		Where-Object {
			@('.yml', '.yaml').Contains($_.Extension.ToLowerInvariant()) -and
			$_.Name -ne 'kustomization.yaml'
		} |
		Sort-Object Name
}
function Resolve-SelectedFiles {
	param(
		[Parameter(Mandatory = $true)][System.IO.FileInfo[]]$AllFiles,
		[string[]]$Selection,
		[switch]$IncludeDeveloperWorkstation,
		[switch]$IncludeApBridgeGateway
	)
	if (-not $Selection -or $Selection.Count -eq 0) {
		# Start from the default exclusions and selectively un-exclude per opt-in switch,
		# so each optional component is gated independently (dev-workstation vs AP gateway).
		$excluded = @($defaultExcludedManifestNames)
		if ($IncludeDeveloperWorkstation) {
			$excluded = @($excluded | Where-Object { $_ -notlike 'developer-workstation*' })
		}
		if ($IncludeApBridgeGateway) {
			$excluded = @($excluded | Where-Object { $_ -ne 'ap-gateway.yaml' -and $_ -ne 'ap-bootstrap-job.yaml' })
		}
		return @(
			$AllFiles | Where-Object { $excluded -notcontains $_.Name }
		)
	}
	$byPath = @{}
	$ordered = @()
	foreach ($item in $Selection) {
		$s = ''
		if ($null -ne $item) {
			$s = [string]$item
		}
			$s = $s.Trim().Trim([char[]]@([char]96, [char]34, [char]39))
		if ([string]::IsNullOrWhiteSpace($s)) { continue }
		$matches = @()
		if ($s -match '\.(ya?ml)$') {
			$matches = $AllFiles | Where-Object { $_.Name -like $s }
		}
		else {
			$matches = $AllFiles | Where-Object { $_.BaseName -eq $s -or $_.Name -like ("$s*.y*ml") }
		}
		if (-not $matches -or $matches.Count -eq 0) {
			$available = ($AllFiles | ForEach-Object { $_.Name }) -join ', '
			throw "No manifest matched '$s'. Available: $available"
		}
		foreach ($m in $matches) {
			if (-not $byPath.ContainsKey($m.FullName)) {
				$byPath[$m.FullName] = $true
				$ordered += $m
			}
		}
	}
	return $ordered
}
function Set-ImageValue {
	param(
		[Parameter(Mandatory = $true)][string]$Image,
		[string]$Tag = '',
		[switch]$PreserveCurrentTag,
		[string]$RepositoryPrefix = ''
	)
	$normalizedImage = $Image.Trim()
	$lastSlash = $normalizedImage.LastIndexOf('/')
	$lastColon = $normalizedImage.LastIndexOf(':')
	$imageName = if ($lastSlash -ge 0) {
		$normalizedImage.Substring($lastSlash + 1)
	}
	else {
		$normalizedImage
	}
	$currentTag = ''
	if ($lastColon -gt $lastSlash) {
		$currentTag = $normalizedImage.Substring($lastColon + 1)
		$imageName = $imageName.Substring(0, $imageName.LastIndexOf(':'))
	}
	$effectiveTag = if ($PreserveCurrentTag) {
		$currentTag
	}
	else {
		$Tag
	}
	if (-not [string]::IsNullOrWhiteSpace($RepositoryPrefix)) {
		$prefix = $RepositoryPrefix.TrimEnd('/')
		if ([string]::IsNullOrWhiteSpace($effectiveTag)) {
			return "${prefix}/${imageName}"
		}
		return "${prefix}/${imageName}:$effectiveTag"
	}
	$repository = if ($lastColon -gt $lastSlash) {
		$normalizedImage.Substring(0, $lastColon)
	}
	else {
		$normalizedImage
	}
	if ([string]::IsNullOrWhiteSpace($effectiveTag)) {
		return $repository
	}
	return "${repository}:$effectiveTag"
}
function Test-UnresolvedPlaceholder {
	param(
		[Parameter(Mandatory = $true)][string]$Content,
		[Parameter(Mandatory = $true)][string]$Placeholder
	)
	$escapedPlaceholder = [regex]::Escape($Placeholder)
	return $Content -match "(?m)^(?!\s*#).*${escapedPlaceholder}"
}
function Render-ManifestContent {
	param(
		[Parameter(Mandatory = $true)][string]$Content
	)
	$rendered = [regex]::Replace(
		$Content,
		'(?m)^(\s*namespace:\s*).+$',
		{
			param($match)
			return $match.Groups[1].Value + $Namespace
		}
	)
	$rendered = $rendered.Replace('__NAMESPACE__', $Namespace)
	$rendered = [regex]::Replace(
		$rendered,
		'(?m)^(\s*image:\s*)(\S+)\s*$',
		{
			param($match)
			$prefix = $match.Groups[1].Value
			$currentImage = $match.Groups[2].Value
			if ($currentImage.Contains('__IMAGE_TAG__')) {
				$currentImage = $currentImage.Replace('__IMAGE_TAG__', $ImageTag)
				$newImage = Set-ImageValue -Image $currentImage -Tag $ImageTag -RepositoryPrefix $ImageRepositoryPrefix
			}
			elseif (-not [string]::IsNullOrWhiteSpace($ImageRepositoryPrefix)) {
				$newImage = Set-ImageValue -Image $currentImage -PreserveCurrentTag -RepositoryPrefix $ImageRepositoryPrefix
			}
			else {
				$newImage = $currentImage
			}
			return $prefix + $newImage
		}
	)
	if (-not [string]::IsNullOrWhiteSpace($BaseDomain)) {
		$rendered = $rendered.Replace('__BASE_DOMAIN__', $BaseDomain)
	}
	if (-not [string]::IsNullOrWhiteSpace($IngressHost)) {
		$rendered = $rendered.Replace('__INGRESS_HOST__', $IngressHost)
	}
	if (-not [string]::IsNullOrWhiteSpace($IngressTlsSecret)) {
		$rendered = $rendered.Replace('__INGRESS_TLS_SECRET__', $IngressTlsSecret)
	}
	return $rendered
}
function Ensure-OutputDirectory {
	param([string]$Dir)
	if ([string]::IsNullOrWhiteSpace($Dir)) {
		$timestamp = Get-Date -Format 'yyyyMMddHHmmss'
		$Dir = Join-Path ([System.IO.Path]::GetTempPath()) "workflow-station-istio-$timestamp"
	}
	if (-not (Test-Path -LiteralPath $Dir)) {
		$null = New-Item -ItemType Directory -Path $Dir -Force
	}
	return (Resolve-Path -LiteralPath $Dir).Path
}
function Write-Utf8File {
	param(
		[Parameter(Mandatory = $true)][string]$Path,
		[Parameter(Mandatory = $true)][string]$Content
	)
	$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
	[System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}
function Get-ConfigValueFromYaml {
	param(
		[Parameter(Mandatory = $true)][string]$Path,
		[Parameter(Mandatory = $true)][string]$Key,
		[string]$Section = 'data'
	)
	if (-not (Test-Path -LiteralPath $Path)) {
		return ''
	}
	$content = Get-Content -LiteralPath $Path -Raw
	$escapedKey = [regex]::Escape($Key)
	$pattern = if ($Section -eq 'stringData') {
		'(?ms)^\s*stringData:\s*$.*?^\s*{0}: "([^"]*)"' -f $escapedKey
	}
	else {
		'(?ms)^\s*data:\s*$.*?^\s*{0}: "([^"]*)"' -f $escapedKey
	}
	$match = [regex]::Match($content, $pattern)
	if ($match.Success) {
		return $match.Groups[1].Value
	}
	return ''
}
function Get-DatabaseSettings {
	$settings = [ordered]@{
		DbHost = $DbHost
		DbPort = if ($DbPort -gt 0) { $DbPort } else { 0 }
		DbName = $DbName
		DbUser = $DbUser
		DbPassword = $DbPassword
		DbSchema = $DbSchema
	}
	$configMapPath = Join-Path $baseDir (Join-Path 'k8s' (Join-Path 'config_map' (Join-Path $Environment 'configmap-workflow-platform-config.yml')))
	$secretPath = Join-Path $baseDir (Join-Path 'k8s' (Join-Path 'secret' (Join-Path $Environment 'secret-workflow-paltform.yml')))
	$jdbcUrl = Get-ConfigValueFromYaml -Path $configMapPath -Key 'SPRING_DATASOURCE_URL'
	if (-not [string]::IsNullOrWhiteSpace($jdbcUrl)) {
		$match = [regex]::Match($jdbcUrl, '^jdbc:postgresql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<db>[^?]+)(\?(?<query>.*))?$')
		if ($match.Success) {
			if ([string]::IsNullOrWhiteSpace($settings.DbHost)) {
				$settings.DbHost = $match.Groups['host'].Value
			}
			if ($settings.DbPort -le 0) {
				$portValue = $match.Groups['port'].Value
				$settings.DbPort = if ([string]::IsNullOrWhiteSpace($portValue)) { 5432 } else { [int]$portValue }
			}
			if ([string]::IsNullOrWhiteSpace($settings.DbName)) {
				$settings.DbName = $match.Groups['db'].Value
			}
			if ([string]::IsNullOrWhiteSpace($settings.DbSchema)) {
				$query = $match.Groups['query'].Value
				if (-not [string]::IsNullOrWhiteSpace($query)) {
					foreach ($pair in ($query -split '&')) {
						$kv = $pair -split '=', 2
						if ($kv.Count -eq 2 -and $kv[0] -eq 'currentSchema' -and -not [string]::IsNullOrWhiteSpace($kv[1])) {
							$settings.DbSchema = $kv[1]
							break
						}
					}
				}
			}
		}
	}
	if ([string]::IsNullOrWhiteSpace($settings.DbUser)) {
		$settings.DbUser = Get-ConfigValueFromYaml -Path $configMapPath -Key 'SPRING_DATASOURCE_USERNAME'
	}
	if ([string]::IsNullOrWhiteSpace($settings.DbPassword)) {
		$settings.DbPassword = Get-ConfigValueFromYaml -Path $secretPath -Key 'SPRING_DATASOURCE_PASSWORD' -Section 'stringData'
	}
	if ($settings.DbPort -le 0) {
		$settings.DbPort = 5432
	}
	if ([string]::IsNullOrWhiteSpace($settings.DbSchema)) {
		$settings.DbSchema = 'public'
	}
	return [pscustomobject]$settings
}
function Invoke-DatabaseInitializationIfRequested {
	if (-not $InitializeDatabase) {
		return
	}
	if ($RenderOnly -or $DryRun) {
		Write-Warning 'Skipping database initialization because -RenderOnly or -DryRun was requested.'
		return
	}
	Assert-Psql
	$settings = Get-DatabaseSettings
	$missing = @()
	foreach ($name in @('DbHost', 'DbName', 'DbUser', 'DbPassword')) {
		if ([string]::IsNullOrWhiteSpace($settings.$name)) {
			$missing += $name
		}
	}
	if ($missing.Count -gt 0) {
		throw "Database initialization is missing required values: $($missing -join ', '). Pass them explicitly or populate config_map/secret manifests first."
	}
	$schemaQualifiedMarker = "$($settings.DbSchema).wf_extended_task_info"
	$previousPassword = $env:PGPASSWORD
	$previousOptions = $env:PGOPTIONS
	$env:PGPASSWORD = $settings.DbPassword
	$env:PGOPTIONS = "-c search_path=$($settings.DbSchema),public"
	try {
		$markerExists = & psql -h $settings.DbHost -p $settings.DbPort -U $settings.DbUser -d $settings.DbName -t -A -c "SELECT CASE WHEN to_regclass('$schemaQualifiedMarker') IS NULL THEN '0' ELSE '1' END;"
		if ($LASTEXITCODE -ne 0) {
			throw 'Failed to probe the target database for existing schema markers.'
		}
		if (($markerExists | Out-String).Trim() -eq '1' -and -not $ForceDatabaseInitialization) {
			Write-Host "`nDatabase initialization skipped: marker table already exists in schema '$($settings.DbSchema)'. Use -ForceDatabaseInitialization to run anyway." -ForegroundColor Yellow
			return
		}
	}
	finally {
		$env:PGPASSWORD = $previousPassword
		$env:PGOPTIONS = $previousOptions
	}
	$initScriptPath = Join-Path $baseDir 'init-scripts\init-database.ps1'
	if (-not (Test-Path -LiteralPath $initScriptPath)) {
		throw "Database init script not found: $initScriptPath"
	}
	Write-Host "`nInitializing database before Kubernetes apply ..." -ForegroundColor Yellow
	Write-Host "  DbHost:          $($settings.DbHost)"
	Write-Host "  DbPort:          $($settings.DbPort)"
	Write-Host "  DbName:          $($settings.DbName)"
	Write-Host "  DbUser:          $($settings.DbUser)"
	Write-Host "  DbSchema:        $($settings.DbSchema)"
	Write-Host "  IncludeDemoData: $IncludeDemoData"
	$invokeArgs = @{
		DbHost = $settings.DbHost
		DbPort = $settings.DbPort
		DbName = $settings.DbName
		DbUser = $settings.DbUser
		DbPassword = $settings.DbPassword
		DbSchema = $settings.DbSchema
	}
	if (-not $IncludeDemoData) {
		$invokeArgs['SkipDemoData'] = $true
	}
	& $initScriptPath @invokeArgs
	if ($LASTEXITCODE -ne 0) {
		throw 'Database initialization failed.'
	}
}
if (-not $RenderOnly) {
	Assert-Kubectl
}
$baseDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestDir = Join-Path $baseDir 'k8s'
Write-Host "Namespace:             $Namespace"
Write-Host "ImageTag:              $ImageTag"
Write-Host "Environment:           $Environment (config_map/secret paths for DB init)"
Write-Host "ImageRepositoryPrefix: $ImageRepositoryPrefix"
Write-Host "BaseDomain:            $BaseDomain"
Write-Host "IngressHost:           $IngressHost"
Write-Host "IngressTlsSecret:      $IngressTlsSecret"
Write-Host "IngressGatewayNs:      $IngressGatewayNamespace"
Write-Host "IncludeDeveloperWs:    $IncludeDeveloperWorkstation"
Write-Host "IncludeApBridgeGw:     $IncludeApBridgeGateway"
Write-Host "ManifestDir:           $manifestDir"
Write-Host "DryRun:                $DryRun"
Write-Host "RenderOnly:            $RenderOnly"
Write-Host "InitializeDatabase:    $InitializeDatabase"
Write-Host "IncludeDemoData:       $IncludeDemoData"
Write-Host "ForceDbInit:           $ForceDatabaseInitialization"
$allFiles = @(Get-ManifestFiles -Dir $manifestDir)
if ($allFiles.Count -eq 0) {
	throw "No manifest files found at: $manifestDir"
}
$targetFiles = @(Resolve-SelectedFiles -AllFiles $allFiles -Selection $Select -IncludeDeveloperWorkstation:$IncludeDeveloperWorkstation -IncludeApBridgeGateway:$IncludeApBridgeGateway)
$renderDir = Ensure-OutputDirectory -Dir $OutputDir
Write-Host "RenderedOutput:        $renderDir"
Write-Host "`nRendering $($targetFiles.Count) manifest file(s)..." -ForegroundColor Yellow
foreach ($file in $targetFiles) {
	$content = Get-Content -LiteralPath $file.FullName -Raw
	$rendered = Render-ManifestContent -Content $content
	$targetPath = Join-Path $renderDir $file.Name
	Write-Utf8File -Path $targetPath -Content $rendered
	Write-Host "  OK  $($file.Name)"
}
$unresolvedNamespaceFiles = @()
$unresolvedImageTagFiles = @()
$unresolvedBaseDomainFiles = @()
$unresolvedIngressHostFiles = @()
$unresolvedIngressTlsSecretFiles = @()
foreach ($file in $targetFiles) {
	$targetPath = Join-Path $renderDir $file.Name
	$renderedContent = Get-Content -LiteralPath $targetPath -Raw
	if (Test-UnresolvedPlaceholder -Content $renderedContent -Placeholder '__NAMESPACE__') {
		$unresolvedNamespaceFiles += $file.Name
	}
	if (Test-UnresolvedPlaceholder -Content $renderedContent -Placeholder '__IMAGE_TAG__') {
		$unresolvedImageTagFiles += $file.Name
	}
	if (Test-UnresolvedPlaceholder -Content $renderedContent -Placeholder '__BASE_DOMAIN__') {
		$unresolvedBaseDomainFiles += $file.Name
	}
	if (Test-UnresolvedPlaceholder -Content $renderedContent -Placeholder '__INGRESS_HOST__') {
		$unresolvedIngressHostFiles += $file.Name
	}
	if (Test-UnresolvedPlaceholder -Content $renderedContent -Placeholder '__INGRESS_TLS_SECRET__') {
		$unresolvedIngressTlsSecretFiles += $file.Name
	}
}
if ($unresolvedNamespaceFiles.Count -gt 0) {
	Write-Warning "These rendered files still contain __NAMESPACE__: $($unresolvedNamespaceFiles -join ', ')"
}
if ($unresolvedImageTagFiles.Count -gt 0) {
	Write-Warning "These rendered files still contain __IMAGE_TAG__: $($unresolvedImageTagFiles -join ', ')"
}
if ($unresolvedBaseDomainFiles.Count -gt 0) {
	Write-Warning "These rendered files still contain __BASE_DOMAIN__: $($unresolvedBaseDomainFiles -join ', ')"
}
if ($unresolvedIngressHostFiles.Count -gt 0) {
	Write-Warning "These rendered files still contain __INGRESS_HOST__: $($unresolvedIngressHostFiles -join ', ')"
}
if ($unresolvedIngressTlsSecretFiles.Count -gt 0) {
	Write-Warning "These rendered files still contain __INGRESS_TLS_SECRET__: $($unresolvedIngressTlsSecretFiles -join ', ')"
}
if ($RenderOnly) {
	Write-Host "`nRenderOnly completed. Rendered files are in: $renderDir" -ForegroundColor Green
	return
}
Invoke-DatabaseInitializationIfRequested
Assert-IngressTlsSecret -SecretName $IngressTlsSecret -GatewayNamespace $IngressGatewayNamespace -ApplicationNamespace $Namespace -SkipCheck:$SkipIngressTlsSecretCheck
$null = kubectl get namespace $Namespace 2>$null
if ($LASTEXITCODE -ne 0) {
	if ($DryRun) {
		Write-Warning "Namespace '$Namespace' does not exist. DryRun mode will not create it."
	}
	else {
		Write-Host "`nCreating namespace $Namespace ..." -ForegroundColor Yellow
		& kubectl create namespace $Namespace | Out-Host
	}
}
else {
	Write-Host "`nNamespace already exists: $Namespace" -ForegroundColor Green
}
foreach ($file in $targetFiles) {
	$targetPath = Join-Path $renderDir $file.Name
	Write-Host "`nApplying $($file.Name) ..." -ForegroundColor Cyan
	if ($DryRun) {
		& kubectl apply --dry-run=client -f $targetPath | Out-Host
	}
	else {
		& kubectl apply -f $targetPath | Out-Host
	}
}
Write-Host "`nDone." -ForegroundColor Green
Write-Host "Check resources with: kubectl get all -n $Namespace"