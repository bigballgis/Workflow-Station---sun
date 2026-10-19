$ErrorActionPreference = 'Stop'
$deployScript = Join-Path $PSScriptRoot '../../environments/dev/build-and-deploy.ps1'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    (Resolve-Path $deployScript), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
foreach ($functionName in @('Pull-ImageWithRetry', 'Resolve-BaseImage')) {
    $definition = $ast.Find({ param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -eq $functionName
    }, $true)
    if (-not $definition) { throw "Missing function: $functionName" }
    Invoke-Expression $definition.Extent.Text
}

# Exercise deployment helpers without network access or restarting containers.
$script:cachedImages = @('kong:3.9')
$script:pulls = @()
$script:pullExit = 0
$SkipImagePull = $false
function docker {
    if ($args[0] -eq 'image' -and $args[1] -eq 'inspect') {
        $global:LASTEXITCODE = if ($script:cachedImages -contains $args[2]) { 0 } else { 1 }
    } elseif ($args[0] -eq 'pull') {
        $script:pulls += $args[1]
        $global:LASTEXITCODE = $script:pullExit
    } else { throw "Unexpected Docker command: $args" }
}
function Start-Sleep {}
function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

Assert-True (Pull-ImageWithRetry -Image 'kong:3.9' -MaxAttempts 1) 'Cached Kong unavailable'
Assert-True ($script:pulls.Count -eq 0) 'Cached Kong triggered a network pull'
Assert-True ((Resolve-BaseImage -Candidates @('kong:3.9')) -eq 'kong:3.9') 'Cached base unresolved'
Assert-True ($script:pulls.Count -eq 0) 'Cached base triggered a network pull'

Assert-True (Pull-ImageWithRetry -Image 'redis:7.2-alpine' -MaxAttempts 1) 'Missing image not pulled'
Assert-True ($script:pulls.Count -eq 1 -and $script:pulls[0] -eq 'redis:7.2-alpine') 'Wrong pull target'

$SkipImagePull = $true
$script:pulls = @()
Assert-True (-not (Pull-ImageWithRetry -Image 'missing:1' -MaxAttempts 1)) 'Missing offline image accepted'
Assert-True ((Resolve-BaseImage -Candidates @('missing:1', 'kong:3.9')) -eq 'kong:3.9') 'Offline cached candidate unresolved'
$rejectedMissing = $false
try { $null = Resolve-BaseImage -Candidates @('missing:1') }
catch { $rejectedMissing = $true }
Assert-True $rejectedMissing 'Missing offline base accepted'
Assert-True ($script:pulls.Count -eq 0) 'SkipImagePull triggered a network pull'

$SkipImagePull = $false
$script:pullExit = 1
Assert-True (-not (Pull-ImageWithRetry -Image 'missing:1' -MaxAttempts 1)) 'Failed pull accepted'
Write-Host 'PASS: cached images, missing-image pulls, offline mode, and pull failures.' -ForegroundColor Green
