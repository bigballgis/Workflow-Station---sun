<#
.SYNOPSIS
  Apply manifests under deploy\k8s-istio-generated\secret\<Environment>\ with delete-before-apply.
.DESCRIPTION
  Secret files live per environment, e.g. secret\preprod\*.yml.
  - If -Select is omitted/empty: apply ALL secret manifests in that folder.
  - If -Select is provided: apply ONLY the matched secret manifests.
  For each manifest file, the script will:
    1) kubectl delete -n <ns> -f <file> --ignore-not-found
    2) kubectl apply  -n <ns> -f <file>
.PARAMETER Namespace
  Target Kubernetes namespace.
.PARAMETER Select
  Which secret manifests to apply.
  Each item can be:
    - exact filename: secret-workflow-paltform.yml
    - basename (no extension): secret-workflow-paltform
    - wildcard pattern: secret-*.yml
.PARAMETER Environment
  Subfolder under secret (e.g. preprod, sit). Default: preprod.
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
  [string]$Environment = 'preprod',
  [switch]$RenderOnly = $false,
  [string]$OutputDir = '',
  [string[]]$Select = @(),
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$SelectRemainder = @()
)
$ErrorActionPreference = 'Stop'
$Select = @($Select) + @($SelectRemainder)
function Normalize-KubernetesNamespace {
  param(
    [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value
  )
  $normalized = $Value.Trim()
  $normalized = $normalized.Trim([char]34, [char]39, [char]96)
  if ([string]::IsNullOrWhiteSpace($normalized)) {
    throw "-Namespace cannot be empty."
  }
  if ($normalized -ne $Value) {
    Write-Warning "-Namespace contained surrounding quotes/backticks and was normalized from '$Value' to '$normalized'."
  }
  if ($normalized -notmatch '^[a-z0-9]([-a-z0-9]*[a-z0-9])?$') {
    throw "Invalid -Namespace '$Value'. Expected a Kubernetes namespace like 'ame-hase-bisp-poc'. If you copied from Markdown, remove any trailing backtick (`)."
  }
  return $normalized
}
function Assert-Kubectl {
  if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw 'kubectl not found in PATH. Please install kubectl or add it to PATH.'
  }
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
    [Parameter(Mandatory = $true)][string]$Namespace
  )
  $content = [System.IO.File]::ReadAllText($SourcePath)
  $rendered = $content.Replace('__NAMESPACE__', $Namespace)
  $tempPath = Join-Path ([System.IO.Path]::GetTempPath()) ([System.IO.Path]::GetRandomFileName() + [System.IO.Path]::GetExtension($SourcePath))
  [System.IO.File]::WriteAllText($tempPath, $rendered, [System.Text.UTF8Encoding]::new($false))
  return $tempPath
}
function Apply-ManifestFile {
  param(
    [Parameter(Mandatory = $true)][string]$FilePath
  )
  $name = [System.IO.Path]::GetFileName($FilePath)
  Write-Host "`n=== $name" -ForegroundColor Cyan
  $renderedFilePath = New-RenderedManifestFile -SourcePath $FilePath -Namespace $Namespace
  try {
    if ($RenderOnly) {
      $destPath = Join-Path $OutputDir $name
      [System.IO.File]::Copy($renderedFilePath, $destPath, $true)
      Write-Host "  Rendered -> $destPath" -ForegroundColor Green
    }
    else {
      & kubectl delete -n $Namespace -f $renderedFilePath --ignore-not-found=true | Out-Host
      & kubectl apply  -n $Namespace -f $renderedFilePath | Out-Host
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
      $matches = $AllFiles | Where-Object { $_.Name -like $leaf }
    }
    elseif ($leaf -match '\.[^\\/]+$') {
      $base = [System.IO.Path]::GetFileNameWithoutExtension($leaf)
      $matches = $AllFiles | Where-Object { $_.BaseName -eq $base }
    }
    else {
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
$Namespace = Normalize-KubernetesNamespace -Value $Namespace
if (-not $RenderOnly) {
  Assert-Kubectl
}
elseif ([string]::IsNullOrWhiteSpace($OutputDir)) {
  throw '-RenderOnly requires -OutputDir.'
}
$baseDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$secretDir = Join-Path (Join-Path $baseDir 'secret') $Environment
Write-Host "Namespace: $Namespace"
Write-Host "Environment: $Environment"
Write-Host "RenderOnly: $RenderOnly"
Write-Host "SecretDir:  $secretDir"
if ($RenderOnly) {
  Write-Host "OutputDir:  $OutputDir"
  [void][System.IO.Directory]::CreateDirectory($OutputDir)
}
$allFiles = @(Get-ManifestFiles -Dir $secretDir -Extensions @('.yml', '.yaml', '.json'))
if ($allFiles.Count -eq 0) {
  throw "No secret manifests found at: $secretDir"
}
if (-not $Select -or $Select.Count -eq 0) {
  $toApply = @($allFiles)
}
else {
  $toApply = @(Resolve-SelectedFiles -AllFiles $allFiles -Selection $Select)
}
$verb = if ($RenderOnly) { 'Rendering' } else { 'Applying' }
Write-Host "`n$verb secret ($($toApply.Count) files)" -ForegroundColor Yellow
foreach ($f in $toApply) { Apply-ManifestFile -FilePath $f.FullName }
Write-Host "`nDone." -ForegroundColor Green