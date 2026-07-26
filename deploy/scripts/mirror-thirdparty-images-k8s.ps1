# =============================================================================
# 把第三方镜像同步(拉 → 改 tag → 推)到私有 K8S registry。
#
# 背景:build-and-push-k8s.ps1 只 build+push 平台**自研**的 8 个服务;Activepieces / redis /
# kafka / kong 这些**第三方镜像不是 build 出来的**,而是从上游拉下来、改 tag 后推进私有
# registry(k8s manifest 全部引用 <Registry>/<name>:<tag>)。这套同步原先没有脚本——本脚本补上。
#
# 用法:
#   # 全部第三方镜像
#   .\mirror-thirdparty-images-k8s.ps1 -Registry nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2
#   # 只同步 activepieces
#   .\mirror-thirdparty-images-k8s.ps1 -Registry <...> -Images activepieces
#   # 只拉+改 tag 不推(本地验证)
#   .\mirror-thirdparty-images-k8s.ps1 -Registry <...> -NoPush
#
# 前置:已 docker login 上游(若需要)与目标私有 registry;本机能拉到上游镜像(或经代理镜像源)。
#
# ⚠️ 上游确认:下表 Upstream 是**对照 dev compose 推断**的。activepieces 已确认一致;
#    其余(尤其 kafka:k8s 要 3.6.2、dev 用 confluentinc/cp-kafka:7.5.3,是不同发行版)**请按你们实际确认后再用**。
# =============================================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$Registry,                 # 目标 registry 基址(到 .../workflow-station2 为止)
    [string]$Images = "all",           # 逗号分隔:activepieces,redis,kafka,kong  或 all
    [switch]$NoPush = $false            # 只拉+改 tag,不 push
)

# 目标名:tag 必须与 deploy/k8s/*.yaml 里的引用一致;Upstream 为对应上游镜像。
$ThirdParty = @(
    @{ Name = "activepieces"; Tag = "0.84.0";  Upstream = "activepieces/activepieces:0.84.0"; Confirmed = $true  },  # ✅ 已确认
    @{ Name = "redis";        Tag = "7.2";     Upstream = "redis:7.2";                        Confirmed = $false },  # dev 用 7.2-alpine,确认要不要 alpine
    @{ Name = "kong";         Tag = "3.7";     Upstream = "kong:3.7";                         Confirmed = $false },
    @{ Name = "kafka";        Tag = "3.6.2";   Upstream = "apache/kafka:3.6.2";               Confirmed = $false }   # ⚠️ dev 是 cp-kafka:7.5.3,发行版不同,务必确认
    # superset 不在此列:它是 deploy/superset/Dockerfile **自建**,走 build 而非 mirror。
)

function Write-Step { param([string]$m) Write-Host "`n>> $m" -ForegroundColor Cyan }
function Write-Ok   { param([string]$m) Write-Host "   OK: $m"   -ForegroundColor Green }
function Write-Fail { param([string]$m) Write-Host "   FAIL: $m" -ForegroundColor Red; exit 1 }

$wanted = if ($Images -eq "all") { $ThirdParty } else {
    $set = $Images.Split(",") | ForEach-Object { $_.Trim() }
    $ThirdParty | Where-Object { $set -contains $_.Name }
}
if (-not $wanted) { Write-Fail "没有匹配的镜像:$Images(可选 activepieces,redis,kafka,kong 或 all)" }

Write-Host "Registry: $Registry   NoPush: $NoPush" -ForegroundColor Yellow

foreach ($img in $wanted) {
    $target = "$Registry/$($img.Name):$($img.Tag)"
    Write-Step "镜像 $($img.Name): $($img.Upstream)  ->  $target"
    if (-not $img.Confirmed) {
        Write-Host "   ⚠ 上游为推断值,确认后再用:$($img.Upstream)" -ForegroundColor Yellow
    }

    docker pull $img.Upstream
    if ($LASTEXITCODE -ne 0) { Write-Fail "拉取上游失败:$($img.Upstream)" }

    docker tag $img.Upstream $target
    if ($LASTEXITCODE -ne 0) { Write-Fail "改 tag 失败:$target" }

    if ($NoPush) { Write-Ok "已拉取并改 tag(NoPush,未推送):$target"; continue }

    docker push $target
    if ($LASTEXITCODE -ne 0) { Write-Fail "推送失败:$target(先 docker login $($Registry.Split('/')[0]) ?)" }
    Write-Ok "已推送:$target"
}

Write-Host "`n完成。k8s manifest 引用的第三方镜像已在私有 registry 就位。" -ForegroundColor Green
