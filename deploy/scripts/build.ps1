# 构建脚本 - 支持多环境构建
# 使用方法: .\build.ps1 -Environment dev -Services "admin-center,user-portal" -CleanImages

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev", "sit", "uat", "prod")]
    [string]$Environment,
    
    [Parameter(Mandatory=$false)]
    [string]$Services = "all",
    
    [Parameter(Mandatory=$false)]
    [switch]$CleanImages = $false,
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipTests = $true,
    
    [Parameter(Mandatory=$false)]
    [switch]$NoCache = $false
)

# 颜色输出函数
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    } else {
        $input | Write-Output
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

function Write-Success { Write-ColorOutput Green $args }
function Write-Info { Write-ColorOutput Cyan $args }
function Write-Warning { Write-ColorOutput Yellow $args }
function Write-Error { Write-ColorOutput Red $args }

# 检查Docker是否运行
function Test-DockerRunning {
    try {
        docker version | Out-Null
        return $true
    } catch {
        return $false
    }
}

# 停止Docker容器
function Stop-DockerContainers {
    param([string[]]$ServiceNames, [string]$Environment)
    
    Write-Info "🛑 停止相关Docker容器..."
    
    foreach ($serviceName in $ServiceNames) {
        try {
            $containerName = "platform-$serviceName-$Environment"
            
            # 检查容器是否存在且正在运行
            $containerStatus = docker ps -q --filter "name=$containerName" 2>$null
            if ($containerStatus) {
                Write-Info "停止容器: $containerName"
                docker stop $containerName 2>$null
                if ($LASTEXITCODE -eq 0) {
                    Write-Success "✅ 成功停止容器: $containerName"
                } else {
                    Write-Warning "⚠️  停止容器失败: $containerName"
                }
            } else {
                Write-Info "容器未运行或不存在: $containerName"
            }
        } catch {
            Write-Warning "⚠️  停止容器时出错: $serviceName - $($_.Exception.Message)"
        }
    }
}
function Remove-DockerImages {
    param([string[]]$ImageNames)
    
    Write-Info "🗑️  删除现有Docker镜像以防止缓存..."
    
    foreach ($imageName in $ImageNames) {
        try {
            # 删除所有相关的镜像（包括时间戳版本和latest版本）
            $images = docker images --format "table {{.Repository}}:{{.Tag}}" | Select-String $imageName
            if ($images) {
                # 删除所有匹配的镜像
                $imageList = docker images --format "{{.Repository}}:{{.Tag}}" | Select-String $imageName
                foreach ($image in $imageList) {
                    Write-Info "删除镜像: $image"
                    docker rmi $image --force 2>$null
                    if ($LASTEXITCODE -eq 0) {
                        Write-Success "✅ 成功删除镜像: $image"
                    } else {
                        Write-Warning "⚠️  镜像不存在或已删除: $image"
                    }
                }
            } else {
                Write-Info "没有找到匹配的镜像: $imageName"
            }
        } catch {
            Write-Warning "⚠️  删除镜像时出错: $imageName - $($_.Exception.Message)"
        }
    }
}

# 构建Maven项目
function Build-MavenProject {
    param(
        [string]$ProjectPath,
        [string]$ProjectName,
        [bool]$SkipTests = $true
    )
    
    Write-Info "🔨 构建Maven项目: $ProjectName"
    
    $originalLocation = Get-Location
    try {
        Set-Location $ProjectPath
        
        # 清理项目
        Write-Info "清理项目..."
        mvn clean
        if ($LASTEXITCODE -ne 0) {
            throw "Maven clean失败"
        }
        
        # 构建项目
        $mvnArgs = if ($SkipTests) { "package", "-DskipTests" } else { "package" }
        Write-Info "执行Maven构建..."
        & mvn @mvnArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Maven构建失败"
        }
        
        Write-Success "✅ $ProjectName 构建成功"
        
    } catch {
        Write-Error "❌ $ProjectName 构建失败: $($_.Exception.Message)"
        throw
    } finally {
        Set-Location $originalLocation
    }
}

# 构建Docker镜像
function Build-DockerImage {
    param(
        [string]$ServiceName,
        [string]$Environment,
        [bool]$NoCache = $false
    )
    
    Write-Info "🐳 构建Docker镜像: $ServiceName"
    
    # 生成版本号：日期时间戳格式 YYYYMMDD-HHMMSS
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $imageTag = "$Environment-$ServiceName`:$timestamp"
    $latestTag = "$Environment-$ServiceName`:latest"
    
    $originalLocation = Get-Location
    try {
        Set-Location "deploy/environments/$Environment"
        
        $dockerArgs = @("build")
        if ($NoCache) {
            $dockerArgs += "--no-cache"
        }
        # 同时创建时间戳版本和latest版本
        $dockerArgs += @("-t", $imageTag, "-t", $latestTag, "../../../backend/$ServiceName")
        
        Write-Info "执行Docker构建: docker $($dockerArgs -join ' ')"
        & docker @dockerArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Docker镜像构建失败"
        }
        
        Write-Success "✅ $ServiceName Docker镜像构建成功"
        Write-Info "   📦 镜像标签: $imageTag"
        Write-Info "   📦 最新标签: $latestTag"
        
    } catch {
        Write-Error "❌ $ServiceName Docker镜像构建失败: $($_.Exception.Message)"
        throw
    } finally {
        Set-Location $originalLocation
    }
}

# 主要构建逻辑
function Start-Build {
    Write-Info "🚀 开始构建流程..."
    Write-Info "环境: $Environment"
    Write-Info "服务: $Services"
    Write-Info "跳过测试: $SkipTests"
    Write-Info "清理镜像: $CleanImages"
    Write-Info "无缓存构建: $NoCache"
    
    # 检查Docker
    if (-not (Test-DockerRunning)) {
        Write-Error "❌ Docker未运行，请启动Docker Desktop"
        exit 1
    }
    
    # 定义所有服务
    $allServices = @(
        "platform-common",
        "platform-security", 
        "platform-cache",
        "platform-messaging",
        "admin-center",
        "user-portal", 
        "developer-workstation",
        "workflow-engine-core",
        "api-gateway"
    )
    
    # 确定要构建的服务
    $servicesToBuild = if ($Services -eq "all") { 
        $allServices 
    } else { 
        $Services -split "," | ForEach-Object { $_.Trim() }
    }
    
    Write-Info "将构建以下服务: $($servicesToBuild -join ', ')"
    
    # 停止相关容器（只停止需要Docker镜像的服务）
    $servicesToStop = $servicesToBuild | Where-Object { $_ -notin @("platform-common", "platform-security", "platform-cache", "platform-messaging") }
    if ($servicesToStop.Count -gt 0) {
        Stop-DockerContainers -ServiceNames $servicesToStop -Environment $Environment
    }
    
    # 删除现有镜像（如果指定）
    if ($CleanImages) {
        $imageNames = $servicesToStop | ForEach-Object { "$Environment-$_" }
        if ($imageNames.Count -gt 0) {
            Remove-DockerImages -ImageNames $imageNames
        }
    }
    
    $buildErrors = @()
    
    # 构建每个服务
    foreach ($service in $servicesToBuild) {
        try {
            Write-Info "📦 处理服务: $service"
            
            # 构建Maven项目
            $projectPath = "backend/$service"
            if (Test-Path $projectPath) {
                Build-MavenProject -ProjectPath $projectPath -ProjectName $service -SkipTests $SkipTests
            } else {
                Write-Warning "⚠️  项目路径不存在: $projectPath"
                continue
            }
            
            # 构建Docker镜像（跳过library项目）
            if ($service -notin @("platform-common", "platform-security", "platform-cache", "platform-messaging")) {
                Build-DockerImage -ServiceName $service -Environment $Environment -NoCache $NoCache
            } else {
                Write-Info "📚 $service 是库项目，跳过Docker镜像构建"
            }
            
        } catch {
            $errorMsg = "服务 $service 构建失败: $($_.Exception.Message)"
            Write-Error "❌ $errorMsg"
            $buildErrors += $errorMsg
        }
    }
    
    # 构建总结
    Write-Info "`n📊 构建总结:"
    if ($buildErrors.Count -eq 0) {
        Write-Success "✅ 所有服务构建成功!"
        
        # 显示构建的镜像
        Write-Info "`n🐳 构建的Docker镜像:"
        $imageNames = $servicesToBuild | Where-Object { $_ -notin @("platform-common", "platform-security", "platform-cache", "platform-messaging") } | ForEach-Object { "$Environment-$_" }
        foreach ($imageName in $imageNames) {
            # 显示最新的时间戳版本和latest版本
            $allImages = docker images $imageName --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}" | Select-Object -Skip 1
            foreach ($imageInfo in $allImages) {
                if ($imageInfo) {
                    Write-Success "  ✅ $imageInfo"
                }
            }
        }
        
    } else {
        Write-Error "`n❌ 构建过程中出现 $($buildErrors.Count) 个错误:"
        foreach ($error in $buildErrors) {
            Write-Error "  • $error"
        }
        exit 1
    }
}

# 脚本入口点
try {
    Start-Build
} catch {
    Write-Error "❌ 构建脚本执行失败: $($_.Exception.Message)"
    exit 1
}