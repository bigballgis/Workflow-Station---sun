<#
.SYNOPSIS
  Apply manifests under deploy\k8s\config_map\<Environment>\ with delete-before-apply.
.DESCRIPTION
  Config files live per environment, e.g. config_map\preprod\*.yml.
  - If -Select is omitted/empty: apply ALL config_map manifests in that folder.
  - If -Select is provided: apply ONLY the matched config_map manifests.
  Before apply, the script renders each manifest by:
    - replacing every `namespace:` value with `-Namespace`
    - replacing plain namespace token occurrences with `-NamespaceToken`
  For each manifest file, the script will:
    1) kubectl delete -n <ns> -f <file> --ignore-not-found
    2) kubectl apply  -n <ns> -f <file>
.PARAMETER Namespace
  Target Kubernetes namespace.
.PARAMETER NamespaceToken
  Literal namespace token to replace inside rendered config_map files.
  Default: ame-hase-bisp-poc
.PARAMETER Select
  Which config_map manifests to apply.
  Each item can be:
    - exact filename: configmap-workflow-platform-config.yml
    - basename (no extension): configmap-workflow-platform-config
    - wildcard pattern: configmap-*.yml
.PARAMETER BaseDomain
  Optional domain suffix used to replace `__BASE_DOMAIN__`.
.PARAMETER IngressHost
  Optional ingress host used to replace `__INGRESS_HOST__`.
.PARAMETER Environment
  Subfolder under config_map (e.g. preprod, sit). Default: preprod.
.PARAMETER RenderOnly
  Only render manifests to -OutputDir; do not call kubectl.
.PARAMETER OutputDir
  Required when -RenderOnly is set. Rendered files are written here (flat filenames).
.NOTES
  The folder may contain YAML or JSON manifests.
#>
[CmdletBinding()]
param(
  [string]$Namespace = 'ame-hase-bisp-poc',
  [string]$NamespaceToken = 'ame-hase-bisp-poc',
  [string]$BaseDomain = '',
  [string]$IngressHost = '',
  [string]$Environment = 'preprod',
  [switch]$RenderOnly = $false,
  [string]$OutputDir = '',
  [string[]]$Select = @(),
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$SelectRemainder = @()
)
$ErrorActionPreference = 'Stop'
$Select = @($Select) + @($SelectRemainder)
function Assert-Kubectl {
  if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw 'kubectl not found in PATH. Please install kubectl or add it to PATH.'
  }
}
function Test-UnresolvedPlaceholder {
  param(
    [Parameter(Mandatory = $true)][string]$Content,
    [Parameter(Mandatory = $true)][string]$Placeholder
  )
  $escapedPlaceholder = [regex]::Escape($Placeholder)
  return $Content -match "(?m)^(?!\s*#).*${escapedPlaceholder}"
}
function Get-ManifestFiles {
  param(
    [Parameter(Mandatory = $true)][string]$Dir,
    [Parameter(Mandatory = $true)][string[]]$Extensions
  )
  if (-not (Test-Path -LiteralPath $Dir)) {
    return @()
  }
  $extSet = @{}
  foreach ($e in $Extensions) {
    if (-not [string]::IsNullOrWhiteSpace($e)) {
      $extSet[$e.ToLowerInvariant()] = $true
    }
  }
  Get-ChildItem -LiteralPath $Dir -File |
    Where-Object { $extSet.ContainsKey($_.Extension.ToLowerInvariant()) } |
    Sort-Object Name
}
function New-RenderedManifestFile {
  param(
    [Parameter(Mandatory = $true)][string]$SourcePath,
    [Parameter(Mandatory = $true)][string]$Namespace,
    [string]$NamespaceToken = '',
    [string]$BaseDomain = '',
    [string]$IngressHost = ''
  )
  $content = [System.IO.File]::ReadAllText($SourcePath)
  $rendered = [regex]::Replace(
    $content,
    '(?m)^(\s*namespace:\s*).+$',
    {
      param($match)
      return $match.Groups[1].Value + $Namespace
    }
  )

   $rendered = $rendered.Replace('__NAMESPACE__', $Namespace)
  if (-not [string]::IsNullOrWhiteSpace($NamespaceToken)) {
    $rendered = $rendered.Replace($NamespaceToken, $Namespace)
  }
  if (-not [string]::IsNullOrWhiteSpace($BaseDomain)) {
    $rendered = $rendered.Replace('__BASE_DOMAIN__', $BaseDomain)
  }
  if (-not [string]::IsNullOrWhiteSpace($IngressHost)) {
    $rendered = $rendered.Replace('__INGRESS_HOST__', $IngressHost)
  }
  if (Test-UnresolvedPlaceholder -Content $rendered -Placeholder '__BASE_DOMAIN__') {
    throw "Rendered config map still contains __BASE_DOMAIN__. Pass -BaseDomain before applying '$([System.IO.Path]::GetFileName($SourcePath))'."
  }
  if (Test-UnresolvedPlaceholder -Content $rendered -Placeholder '__INGRESS_HOST__') {
    throw "Rendered config map still contains __INGRESS_HOST__. Pass -IngressHost before applying '$([System.IO.Path]::GetFileName($SourcePath))'."
  }
  # HERMES: CHANGE_ME_* 未替换即拒绝 apply。
  #
  # 2026-08 UAT 事故的一半就是这么滑过去的:ACTIVEPIECES_MANAGED_SIGNING_KEY_ID /
  # ACTIVEPIECES_MANAGED_PRIVATE_KEY 保持占位符被 apply 上去,Pod 正常起、健康检查通过,
  # 直到有人点开 Automation 页面才以 ACTIVEPIECES_API_ERROR 暴露。
  # __BASE_DOMAIN__ 那类占位符本来就有 Test-UnresolvedPlaceholder 拦着,CHANGE_ME_* 却没有
  # —— 两个 apply 脚本里 "CHANGE_ME" 的命中数曾经是 0。
  #
  # 只看非注释行(Test-UnresolvedPlaceholder 已内建该规则),所以文档性提示不会误伤。
  $changeMe = [regex]::Matches($rendered, '(?m)^(?!\s*#).*?([A-Z0-9_]*CHANGE_ME[A-Z0-9_]*)')
  if ($changeMe.Count -gt 0) {
    $names = ($changeMe | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique) -join ', '
    throw @"
Refusing to apply '$([System.IO.Path]::GetFileName($SourcePath))': $($changeMe.Count) unreplaced CHANGE_ME placeholder(s) remain -> $names

These apply cleanly and the pods start healthy, so the gap only surfaces when someone
opens the feature that needs them. Fill them in for this environment first.
Signing-key values: see docs/ap-integration/PROD_WIRING_RUNBOOK.md sections 1-3.
"@
  }
  $tempPath = Join-Path ([System.IO.Path]::GetTempPath()) ([System.IO.Path]::GetRandomFileName() + [System.IO.Path]::GetExtension($SourcePath))
  [System.IO.File]::WriteAllText($tempPath, $rendered, [System.Text.UTF8Encoding]::new($false))
  return $tempPath
}
function Apply-ManifestFile {
  param(
    [Parameter(Mandatory = $true)][string]$FilePath
  )
  $name = [System.IO.Path]::GetFileName($FilePath)
  Write-Host ''
  Write-Host "=== $name" -ForegroundColor Cyan
  $renderedFilePath = New-RenderedManifestFile -SourcePath $FilePath -Namespace $Namespace -NamespaceToken $NamespaceToken -BaseDomain $BaseDomain -IngressHost $IngressHost
  try {
    if ($RenderOnly) {
      $destPath = Join-Path $OutputDir $name
      [System.IO.File]::Copy($renderedFilePath, $destPath, $true)
      Write-Host "  Rendered -> $destPath" -ForegroundColor Green
    }
    else {
      & kubectl delete -n $Namespace -f $renderedFilePath --ignore-not-found=true
      & kubectl apply  -n $Namespace -f $renderedFilePath
    }
  }
  finally {
    if (Test-Path -LiteralPath $renderedFilePath) {
      Remove-Item -LiteralPath $renderedFilePath -Force -ErrorAction SilentlyContinue
    }
  }
}
function Resolve-SelectedFiles {
  param(
    [Parameter(Mandatory = $true)][System.IO.FileInfo[]]$AllFiles,
    [string[]]$Selection
  )
  if (-not $Selection -or $Selection.Count -eq 0) {
    return $AllFiles
  }
  $byPath = @{}
  $ordered = @()
  foreach ($item in $Selection) {
    $s = ''
    if ($null -ne $item) {
      $s = [string]$item
    }
    $s = $s.Trim()
    if ([string]::IsNullOrWhiteSpace($s)) { continue }
    $matches = @()
    $leaf = Split-Path -Leaf $s
    $looksLikePattern = ($leaf -like '*[*?]*')
    if ($looksLikePattern) {
      # Wildcard pattern: match against filename
      $matches = $AllFiles | Where-Object { $_.Name -like $leaf }
    }
    elseif ($leaf -match '\.[^\\/]+$') {
      # User provided an extension (e.g. .yml), but folder may contain .json/.yaml.
      # Match by basename across all allowed extensions.
      $base = [System.IO.Path]::GetFileNameWithoutExtension($leaf)
      $matches = $AllFiles | Where-Object { $_.BaseName -eq $base }
    }
    else {
      # Basename or prefix
      $matches = $AllFiles | Where-Object { $_.BaseName -eq $leaf -or $_.Name -like ("$leaf*.*") }
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

if (-not $RenderOnly) {
  Assert-Kubectl
}
elseif ([string]::IsNullOrWhiteSpace($OutputDir)) {
  throw '-RenderOnly requires -OutputDir.'
}
$baseDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$configDir = Join-Path (Join-Path $baseDir 'config_map') $Environment
Write-Host "Namespace: $Namespace"
Write-Host "NamespaceToken: $NamespaceToken"
Write-Host "BaseDomain: $BaseDomain"
Write-Host "IngressHost: $IngressHost"
Write-Host "Environment: $Environment"
Write-Host "RenderOnly: $RenderOnly"
Write-Host "ConfigDir:  $configDir"
if ($RenderOnly) {
  Write-Host "OutputDir:  $OutputDir"
  [void][System.IO.Directory]::CreateDirectory($OutputDir)
}
$allFiles = @(Get-ManifestFiles -Dir $configDir -Extensions @('.yml', '.yaml', '.json'))
if ($allFiles.Count -eq 0) {
  throw "No config_map manifests found at: $configDir"
}
if (-not $Select -or $Select.Count -eq 0) {
  $toApply = @($allFiles)
}
else {
  $toApply = @(Resolve-SelectedFiles -AllFiles $allFiles -Selection $Select)
}
Write-Host ''
$verb = if ($RenderOnly) { 'Rendering' } else { 'Applying' }
Write-Host "$verb config_map ($($toApply.Count) files)" -ForegroundColor Yellow
foreach ($f in $toApply) { Apply-ManifestFile -FilePath $f.FullName }
Write-Host ''
Write-Host "Done." -ForegroundColor Green
