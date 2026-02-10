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
        $mvnArgs = if ($SkipTests) { "package", "-Dmaven.test.skip=true" } else { "package" }
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

# 构建Docker镜像（后端）
function Build-DockerImage {
    param(
        [string]$ServiceName,
        [string]$Environment,
        [string]$Timestamp,
        [bool]$NoCache = $false
    )
    
    Write-Info "🐳 构建后端Docker镜像: $ServiceName"
    
    $imageTag = "$Environment-$ServiceName`:$Timestamp"
    
    $originalLocation = Get-Location
    try {
        Set-Location "deploy/environments/$Environment"
        
        $dockerArgs = @("build")
        if ($NoCache) {
            $dockerArgs += "--no-cache"
        }
        # 只创建时间戳版本，不创建latest
        $dockerArgs += @("-t", $imageTag, "../../../backend/$ServiceName")
        
        Write-Info "执行Docker构建: docker $($dockerArgs -join ' ')"
        & docker @dockerArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Docker镜像构建失败"
        }
        
        Write-Success "✅ $ServiceName Docker镜像构建成功"
        Write-Info "   📦 镜像标签: $imageTag"
        
        return $imageTag
        
    } catch {
        Write-Error "❌ $ServiceName Docker镜像构建失败: $($_.Exception.Message)"
        throw
    } finally {
        Set-Location $originalLocation
    }
}

# 构建前端Docker镜像
function Build-FrontendImage {
    param(
        [string]$ServiceName,
        [string]$Environment,
        [string]$Timestamp,
        [bool]$NoCache = $false
    )
    
    Write-Info "🎨 构建前端Docker镜像: $ServiceName"
    
    $imageTag = "$Environment-$ServiceName-frontend`:$Timestamp"
    
    $originalLocation = Get-Location
    try {
        $frontendPath = "frontend/$ServiceName"
        if (-not (Test-Path $frontendPath)) {
            throw "前端项目路径不存在: $frontendPath"
        }
        
        Set-Location $frontendPath
        
        $dockerArgs = @("build")
        if ($NoCache) {
            $dockerArgs += "--no-cache"
        }
        # 只创建时间戳版本，不创建latest
        $dockerArgs += @("-t", $imageTag, ".")
        
        Write-Info "执行Docker构建: docker $($dockerArgs -join ' ')"
        & docker @dockerArgs
        if ($LASTEXITCODE -ne 0) {
            throw "前端Docker镜像构建失败"
        }
        
        Write-Success "✅ $ServiceName 前端镜像构建成功"
        Write-Info "   📦 镜像标签: $imageTag"
        
        return $imageTag
        
    } catch {
        Write-Error "❌ $ServiceName 前端镜像构建失败: $($_.Exception.Message)"
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
    
    # 生成统一的时间戳版本号
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    Write-Info "构建版本号: $timestamp"
    
    # 检查Docker
    if (-not (Test-DockerRunning)) {
        Write-Error "❌ Docker未运行，请启动Docker Desktop"
        exit 1
    }
    
    # 定义所有后端服务
    $allBackendServices = @(
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
    
    # 定义所有前端服务
    $allFrontendServices = @(
        "admin-center",
        "user-portal",
        "developer-workstation"
    )
    
    # 确定要构建的服务
    if ($Services -eq "all") {
        $backendToBuild = $allBackendServices
        $frontendToBuild = $allFrontendServices
    } elseif ($Services -eq "backend") {
        $backendToBuild = $allBackendServices
        $frontendToBuild = @()
    } elseif ($Services -eq "frontend") {
        $backendToBuild = @()
        $frontendToBuild = $allFrontendServices
    } else {
        $serviceList = $Services -split "," | ForEach-Object { $_.Trim() }
        $backendToBuild = $serviceList | Where-Object { $allBackendServices -contains $_ }
        $frontendToBuild = $serviceList | Where-Object { $allFrontendServices -contains $_ }
    }
    
    Write-Info "将构建后端服务: $($backendToBuild -join ', ')"
    Write-Info "将构建前端服务: $($frontendToBuild -join ', ')"
    
    # 停止相关容器（只停止需要Docker镜像的服务）
    $servicesToStop = $backendToBuild | Where-Object { $_ -notin @("platform-common", "platform-security", "platform-cache", "platform-messaging") }
    $servicesToStop += $frontendToBuild | ForEach-Object { "$_-frontend" }
    
    if ($servicesToStop.Count -gt 0) {
        Stop-DockerContainers -ServiceNames $servicesToStop -Environment $Environment
    }
    
    # 删除现有镜像（如果指定）
    if ($CleanImages) {
        $imageNames = @()
        $imageNames += $backendToBuild | Where-Object { $_ -notin @("platform-common", "platform-security", "platform-cache", "platform-messaging") } | ForEach-Object { "$Environment-$_" }
        $imageNames += $frontendToBuild | ForEach-Object { "$Environment-$_-frontend" }
        
        if ($imageNames.Count -gt 0) {
            Remove-DockerImages -ImageNames $imageNames
        }
    }
    
    $buildErrors = @()
    $builtImages = @()
    
    # 构建后端服务
    foreach ($service in $backendToBuild) {
        try {
            Write-Info "📦 处理后端服务: $service"
            
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
                $imageTag = Build-DockerImage -ServiceName $service -Environment $Environment -Timestamp $timestamp -NoCache $NoCache
                $builtImages += $imageTag
            } else {
                Write-Info "📚 $service 是库项目，跳过Docker镜像构建"
            }
            
        } catch {
            $errorMsg = "后端服务 $service 构建失败: $($_.Exception.Message)"
            Write-Error "❌ $errorMsg"
            $buildErrors += $errorMsg
        }
    }
    
    # 构建前端服务
    foreach ($service in $frontendToBuild) {
        try {
            Write-Info "📦 处理前端服务: $service"
            
            $imageTag = Build-FrontendImage -ServiceName $service -Environment $Environment -Timestamp $timestamp -NoCache $NoCache
            $builtImages += $imageTag
            
        } catch {
            $errorMsg = "前端服务 $service 构建失败: $($_.Exception.Message)"
            Write-Error "❌ $errorMsg"
            $buildErrors += $errorMsg
        }
    }
    
    # 保存版本信息到文件
    if ($builtImages.Count -gt 0) {
        $versionFile = "deploy/environments/$Environment/.image-versions"
        $versionContent = "# 构建时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`n"
        $versionContent += "# 版本号: $timestamp`n`n"
        
        foreach ($image in $builtImages) {
            # 只保存镜像标签，不包含其他信息
            if ($image -match '^dev-[^:]+:[0-9]+-[0-9]+$') {
                $versionContent += "$image`n"
            }
        }
        
        Set-Content -Path $versionFile -Value $versionContent -Encoding UTF8
        Write-Success "✅ 版本信息已保存到: $versionFile"
    }
    
    # 构建总结
    Write-Info "`n📊 构建总结:"
    if ($buildErrors.Count -eq 0) {
        Write-Success "✅ 所有服务构建成功!"
        
        # 显示构建的镜像
        Write-Info "`n🐳 构建的Docker镜像:"
        foreach ($imageTag in $builtImages) {
            $imageInfo = docker images $imageTag --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}" | Select-Object -Skip 1
            if ($imageInfo) {
                Write-Success "  ✅ $imageInfo"
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