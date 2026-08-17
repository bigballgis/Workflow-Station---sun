#!/usr/bin/env pwsh
# =============================================================================
# 把仓库所有 pnpm lockfile 里的 tarball 逐个对着一个 npm registry 探一遍,一次列出缺哪些。
#
# 背景:内网私服(Nexus 等)常常只是**部分**镜像了公共 npm——某个包的新版本干脆不存在。
# 而 `pnpm install` 是 fail-fast:碰到第一个缺失 tarball 就 ERR_PNPM_FETCH_404 退出。
# 于是排查退化成"装 → 看报错 → 提单/降级 → 再装"的多轮循环,一轮只能发现一个包
# (automation 一个 lockfile 就有 3000+ 个走 registry 的包)。本脚本一次走完全部,
# 让你拿到**完整缺口清单**再决定:批量申请入库,还是逐个 pnpm.overrides 降版本。
#
# 用法:
#   # 探全部 5 个 lockfile(automation + 4 个前端)
#   .\probe-npm-registry-coverage.ps1 -Registry https://nexus303.systems.uk.hsbc:8081/nexus/repository/public-npm-registry_iq
#   # 只探 automation
#   .\probe-npm-registry-coverage.ps1 -Registry <...> -Lockfile ..\..\automation\pnpm-lock.yaml
#   # 网关拒 HEAD(405/501)时改用 ranged GET
#   .\probe-npm-registry-coverage.ps1 -Registry <...> -UseGet
#
# 前置:pwsh 7+(用了 ForEach-Object -Parallel);本机能访问该 registry(必要时先配好代理/证书)。
#
# 产物:三个清单,**故意分开**——三种失败的处置流程完全不同,混在一起会把 A 类问题
#   按 B 类去处理(实测过的两个真实例子就分属前两类):
#     registry-missing.txt              404 私服没有这个版本 → 申请入库,或本仓库降到私服有的版本
#                                       (例:mdurl@2.1.0——私服只有 2.0.0,已用 pnpm.overrides 降级)
#     registry-missing.quarantined.txt  403 存在但被 Sonatype Firewall 扣在隔离区 → 走放行申请,
#                                       报文里带 foss-guard 链接(例:vitest@3.0.8)。**降版本不对症**
#     registry-missing.other.txt        401/5xx/超时/TLS → 环境问题,先修再谈覆盖缺口
#
# ⚠️ 只探 registry 包。lockfile 里的 workspace / git / file 依赖不走 registry,自然不在清单里。
# ⚠️ 也只探**从 importers 可达**的条目。锁文件会残留死条目(例:CLI 的内部依赖由 registry 版改成
#    workspace 链接后,那批 registry 版本连同 expr-eval / ai / @ai-sdk 的传递依赖就没人引用了),
#    pnpm 不会请求它们,把它们报成缺口只会催生一批与构建无关的放行申请。跳过数量会打印出来;
#    要诊断锁文件本身(而不是这次 install)时用 -ProbeAll 把它们探回来。
# =============================================================================

param(
    [Parameter(Mandatory = $true)]
    [string]$Registry,                      # registry 基址,到 .../public-npm-registry_iq 为止
    [string[]]$Lockfile = @(),              # 默认:仓库全部 5 个 lockfile
    [string]$OutFile = "registry-missing.txt",
    [int]$Throttle = 16,                    # 并发探测数
    [switch]$UseGet = $false,                # 用 Range: bytes=0-0 的 GET 代替 HEAD
    # 连 install 不会抓取的条目也一起探(诊断锁文件本身时才用)。默认只探可达包。
    [switch]$ProbeAll = $false
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Write-Step { param([string]$m) Write-Host "`n>> $m" -ForegroundColor Cyan }
function Write-Ok   { param([string]$m) Write-Host "   OK: $m"   -ForegroundColor Green }
function Write-Fail { param([string]$m) Write-Host "   FAIL: $m" -ForegroundColor Red; exit 1 }

# "<name>: <value>" 里的一条依赖边 → 规范化的 name@version,不可探的返回 $null。
# 形态(全部取自真实 lockfile v9):
#   1.15.2                      普通版本
#   0.95.1(bufferutil@4.1.0)    带 peer 后缀 → 后缀丢掉,peer 自己是另一条边
#   link:../shared / file:…     workspace 本地链接 → 不走 registry
#   '@rollup/wasm-node@4.62.2'  别名(resolutions rollup→wasm-node):值本身就是完整 spec
#   npm:@rollup/wasm-node@4.6   同上,带 npm: 前缀
function Resolve-LockSpec {
    param([string]$Name, [string]$Value)
    $v = $Value.Trim().Trim("'").Trim('"')
    if ($v -eq "" -or $v.StartsWith("link:") -or $v.StartsWith("file:")) { return $null }
    if ($v.StartsWith("npm:")) { $v = $v.Substring(4) }
    # 值自带包名(别名)时用值本身,否则拼上键名。
    $spec = if ($v.StartsWith("@") -or ($v -match '^[a-zA-Z][^@]*@[0-9]')) { $v } else { "$Name@$v" }
    $spec = $spec -replace '\(.*$', ''
    # git / http 依赖没有 name@semver 形态,探不了 tarball,直接丢弃。
    if ($spec -notmatch '@[0-9]') { return $null }
    return $spec
}

if ($Lockfile.Count -eq 0) {
    # 构建机真正会 install 的五个 workspace:AP(内嵌 builder 用)+ 四个前端。
    $Lockfile = @(
        "automation/pnpm-lock.yaml",
        "frontend/admin-center/pnpm-lock.yaml",
        "frontend/user-portal/pnpm-lock.yaml",
        "frontend/developer-workstation/pnpm-lock.yaml",
        "frontend/login/pnpm-lock.yaml"
    ) | ForEach-Object { Join-Path $ProjectRoot $_ }
}

$base = $Registry.TrimEnd('/')

# lockfile v9 的 `packages:` 段每个 registry tarball 一条,键形如 `  mdurl@2.1.0:` /
# `  '@activepieces/shared@0.96.2':`。`snapshots:` 段是同一批包带 peer 后缀的重复,
# `importers:` 是 workspace 链接——都不是要探的东西,故只取这两段之间。
Write-Step "Parsing lockfiles..."
# 原生数组而非 System.Collections.Generic.List:后者的 ::new() 在 Constrained Language Mode
# 下被禁,build-and-push-k8s.ps1 同样为此让步。
$specs = @()
$skippedTotal = 0
foreach ($lf in $Lockfile) {
    if (-not (Test-Path $lf)) { Write-Fail "Lockfile not found: $lf" }
    $lines = Get-Content $lf
    $start = ($lines | Select-String -Pattern '^packages:$' | Select-Object -First 1).LineNumber
    if (-not $start) { Write-Fail "No 'packages:' section in $lf (lockfileVersion too old?)" }
    $snapAt = ($lines | Select-String -Pattern '^snapshots:$' | Select-Object -First 1).LineNumber
    $end = if ($snapAt) { $snapAt } else { $lines.Count + 1 }

    # `packages:` 段 = 这个锁文件里有 tarball 的全部条目,即"可探集合"。
    $inFile = @{}
    for ($i = $start; $i -lt $end - 1; $i++) {
        if ($lines[$i] -match "^  '?(?<name>@?[^@'\s][^@']*)@(?<ver>[0-9][^'\s:(]*)'?:\s*$") {
            $inFile["$($Matches.name)@$($Matches.ver)"] = $true
        }
    }

    # 可达性:pnpm 只抓取从 importers 沿依赖边能走到的条目。锁文件里可以残留不可达条目——
    # 例如 CLI 的内部依赖从 registry 版改为 workspace 链接后,那批 registry 版本连同
    # 它们独占的传递依赖(expr-eval 等)就成了死条目,install 根本不请求它们。
    # 把它们当缺口上报,会让人对着 FOSS Guard 提一批与构建无关的放行申请。
    $roots = @()
    $edges = @{}
    $curKey = $null
    $lastName = $null
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $l = $lines[$i]
        if ($l -eq "importers:" -or $l -eq "packages:" -or $l -eq "snapshots:") { $curKey = $null; continue }
        # importers 段:包名在 6 空格缩进,resolved 版本在它下面的 `        version:`
        if ($l -match "^      '?(?<n>[^']+?)'?:\s*$") { $lastName = $Matches.n; continue }
        if ($l -match "^        version:\s*(?<v>.+)$") {
            $s = Resolve-LockSpec -Name $lastName -Value $Matches.v
            if ($s) { $roots += $s }
            continue
        }
        # snapshots 段:键在 2 空格缩进,依赖边在 6 空格缩进
        if ($l -match "^  '?(?<k>@?[^'\s][^']*?)'?:\s*$") {
            $curKey = ($Matches.k -replace '\(.*$', '')
            if (-not $edges.ContainsKey($curKey)) { $edges[$curKey] = @() }
            continue
        }
        # `      - name` 是 transitivePeerDependencies 的列表项,不是依赖边
        if ($curKey -and $l -match "^      '?(?<n>@?[^':\s][^':]*?)'?:\s*(?<v>\S.*)$") {
            $s = Resolve-LockSpec -Name $Matches.n -Value $Matches.v
            if ($s) { $edges[$curKey] += $s }
        }
    }

    $reachable = @{}
    $queue = @($roots | Sort-Object -Unique)
    $qi = 0
    while ($qi -lt $queue.Count) {
        $cur = $queue[$qi]; $qi++
        if ($reachable.ContainsKey($cur)) { continue }
        $reachable[$cur] = $true
        if ($edges.ContainsKey($cur)) { foreach ($d in $edges[$cur]) { if (-not $reachable.ContainsKey($d)) { $queue += $d } } }
    }

    $keep = @($inFile.Keys | Where-Object { $ProbeAll -or $reachable.ContainsKey($_) })
    $skipped = $inFile.Count - $keep.Count
    $skippedTotal += $skipped
    $specs += $keep
    $note = if ($skipped -gt 0 -and -not $ProbeAll) { "  ($skipped unreachable, skipped)" } else { "" }
    Write-Host ("   {0,-5} {1}{2}" -f $inFile.Count, (Resolve-Path -Relative $lf), $note) -ForegroundColor Gray
}
$specs = $specs | Sort-Object -Unique
Write-Ok "$($specs.Count) distinct registry packages to probe$(if ($skippedTotal -gt 0 -and -not $ProbeAll) { " ($skippedTotal lockfile entries skipped as unreachable — pnpm never fetches them; pass -ProbeAll to include them)" })"

Write-Step "Probing $base (throttle $Throttle, $(if ($UseGet) { 'ranged GET' } else { 'HEAD' }))..."
$results = $specs | ForEach-Object -ThrottleLimit $Throttle -Parallel {
    $spec = $_
    # 名字里也有 @(scoped),所以按**最后**一个 @ 切分。
    $at = $spec.LastIndexOf('@')
    $name = $spec.Substring(0, $at)
    $ver = $spec.Substring($at + 1)
    # npm tarball 布局:<registry>/<name>/-/<去 scope 的名字>-<version>.tgz
    $basename = if ($name.StartsWith('@')) { $name.Split('/')[1] } else { $name }
    $url = "$($using:base)/$name/-/$basename-$ver.tgz"
    try {
        if ($using:UseGet) {
            $r = Invoke-WebRequest -Uri $url -Method Get -Headers @{ Range = "bytes=0-0" } -MaximumRedirection 5 -SkipHttpErrorCheck -TimeoutSec 30
        } else {
            $r = Invoke-WebRequest -Uri $url -Method Head -MaximumRedirection 5 -SkipHttpErrorCheck -TimeoutSec 30
        }
        # 403 的理由(隔离原因 + Firewall 工单链接)只在**响应体**里,HEAD 拿不到——
        # 所以 -UseGet 时顺带截一段,让 quarantine 清单里直接带着可点的链接。
        $detail = ""
        if ([int]$r.StatusCode -ge 400 -and $r.Content) {
            $detail = ([string]$r.Content).Substring(0, [Math]::Min(300, ([string]$r.Content).Length)) -replace '\s+', ' '
        }
        [pscustomobject]@{ Spec = $spec; Status = [int]$r.StatusCode; Url = $url; Error = $detail }
    } catch {
        # 传输层失败(DNS/TLS/代理)——与 404 不是一回事,单独归档。
        [pscustomobject]@{ Spec = $spec; Status = -1; Url = $url; Error = $_.Exception.Message }
    }
}

$ok          = @($results | Where-Object { $_.Status -ge 200 -and $_.Status -lt 400 })
$missing     = @($results | Where-Object { $_.Status -eq 404 })
# 403 单独一档:Sonatype Firewall 把待评估/违规的版本扣在隔离区就是 403(报文里写
# "Requested item is quarantined" 并给 foss-guard 链接)。它与 404 的处置**完全不同**——
# 包在私服里存在,要走放行申请,而不是在本仓库把版本降到"私服有的那个"。
$quarantined = @($results | Where-Object { $_.Status -eq 403 })
$other       = @($results | Where-Object { $_.Status -ne 404 -and $_.Status -ne 403 -and ($_.Status -lt 200 -or $_.Status -ge 400) })

Write-Host ""
Write-Host "   available   : $($ok.Count)" -ForegroundColor Green
Write-Host "   missing     : $($missing.Count)  (404 — 私服没有这个版本)" -ForegroundColor $(if ($missing.Count) { "Red" } else { "Green" })
Write-Host "   quarantined : $($quarantined.Count)  (403 — 存在但被 Firewall 扣住,需申请放行)" -ForegroundColor $(if ($quarantined.Count) { "Red" } else { "Green" })
Write-Host "   other       : $($other.Count)  (401/5xx/超时/TLS — 环境问题,不是覆盖缺口)" -ForegroundColor $(if ($other.Count) { "Yellow" } else { "Green" })

# 空管道时 Set-Content **不会创建文件**,于是"全绿"的一次运行会打印一个不存在的路径,
# 而调用方要么报错,要么读到上一次运行残留的清单、把旧缺口当成本次结果。所以先建空文件再追加。
$missingLines = @($missing | Sort-Object Spec | ForEach-Object { $_.Spec })
Set-Content -Path $OutFile -Value "" -NoNewline
if ($missingLines.Count -gt 0) { Add-Content -Path $OutFile -Value $missingLines }
Write-Host ""
Write-Host "   -> $OutFile$(if ($missingLines.Count -eq 0) { '  (empty — no 404 gaps)' })" -ForegroundColor Cyan
if ($quarantined.Count -gt 0) {
    $qFile = [System.IO.Path]::ChangeExtension($OutFile, ".quarantined.txt")
    $quarantined | Sort-Object Spec | ForEach-Object { "$($_.Spec)`t$($_.Error)" } | Set-Content $qFile
    Write-Host "   -> $qFile  (每行带 Firewall 给的理由/链接,前提是用了 -UseGet)" -ForegroundColor Yellow
}
if ($other.Count -gt 0) {
    $otherFile = [System.IO.Path]::ChangeExtension($OutFile, ".other.txt")
    $other | Sort-Object Spec | ForEach-Object { "$($_.Spec)`t$($_.Status)`t$($_.Error)" } | Set-Content $otherFile
    Write-Host "   -> $otherFile  (先修这些:全量 401 会看起来像'什么都没镜像')" -ForegroundColor Yellow
}
# 退出码 0 也可能有缺口:这是一份报告,不是门禁。调用方自己决定怎么处置清单。
