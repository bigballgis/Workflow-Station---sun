# =====================================================
# 停止后端服务脚本（Windows PowerShell）
# =====================================================

# ========================================
# UTF-8 编码配置（解决中文乱码）
# ========================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
chcp 65001 | Out-Null

$BASE_DIR = $PSScriptRoot
$LOG_DIR = Join-Path $BASE_DIR "logs"

Write-Host "🛑 停止后端服务..." -ForegroundColor Yellow
Write-Host ""

# 停止 API Gateway
$apiGatewayPidFile = Join-Path $LOG_DIR "api-gateway.pid"
if (Test-Path $apiGatewayPidFile) {
    $pid = Get-Content $apiGatewayPidFile -Raw
    $pid = $pid.Trim()
    try {
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $pid -Force
            Write-Host "✅ 已停止 API Gateway (PID: $pid)" -ForegroundColor Green
        } else {
            # 尝试通过端口停止
            $netstat = netstat -ano | Select-String ":8090.*LISTENING"
            if ($netstat) {
                $processId = ($netstat -split '\s+')[-1]
                if ($processId -and $processId -match '^\d+$') {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    Write-Host "✅ 已停止 API Gateway (端口 8090)" -ForegroundColor Green
                }
            }
        }
    } catch {
        Write-Host "⚠️  无法停止 API Gateway: $_" -ForegroundColor Yellow
    }
    Remove-Item $apiGatewayPidFile -ErrorAction SilentlyContinue
}

# 停止 Workflow Engine
$workflowEnginePidFile = Join-Path $LOG_DIR "workflow-engine.pid"
if (Test-Path $workflowEnginePidFile) {
    $pid = Get-Content $workflowEnginePidFile -Raw
    $pid = $pid.Trim()
    try {
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $pid -Force
            Write-Host "✅ 已停止 Workflow Engine (PID: $pid)" -ForegroundColor Green
        } else {
            $netstat = netstat -ano | Select-String ":8091.*LISTENING"
            if ($netstat) {
                $processId = ($netstat -split '\s+')[-1]
                if ($processId -and $processId -match '^\d+$') {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    Write-Host "✅ 已停止 Workflow Engine (端口 8091)" -ForegroundColor Green
                }
            }
        }
    } catch {
        Write-Host "⚠️  无法停止 Workflow Engine: $_" -ForegroundColor Yellow
    }
    Remove-Item $workflowEnginePidFile -ErrorAction SilentlyContinue
}

# 停止 Admin Center
$adminCenterPidFile = Join-Path $LOG_DIR "admin-center.pid"
if (Test-Path $adminCenterPidFile) {
    $pid = Get-Content $adminCenterPidFile -Raw
    $pid = $pid.Trim()
    try {
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $pid -Force
            Write-Host "✅ 已停止 Admin Center (PID: $pid)" -ForegroundColor Green
        } else {
            $netstat = netstat -ano | Select-String ":8092.*LISTENING"
            if ($netstat) {
                $processId = ($netstat -split '\s+')[-1]
                if ($processId -and $processId -match '^\d+$') {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    Write-Host "✅ 已停止 Admin Center (端口 8092)" -ForegroundColor Green
                }
            }
        }
    } catch {
        Write-Host "⚠️  无法停止 Admin Center: $_" -ForegroundColor Yellow
    }
    Remove-Item $adminCenterPidFile -ErrorAction SilentlyContinue
}

# 停止 Developer Workstation
$devWorkstationPidFile = Join-Path $LOG_DIR "developer-workstation.pid"
if (Test-Path $devWorkstationPidFile) {
    $pid = Get-Content $devWorkstationPidFile -Raw
    $pid = $pid.Trim()
    try {
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $pid -Force
            Write-Host "✅ 已停止 Developer Workstation (PID: $pid)" -ForegroundColor Green
        } else {
            $netstat = netstat -ano | Select-String ":8094.*LISTENING"
            if ($netstat) {
                $processId = ($netstat -split '\s+')[-1]
                if ($processId -and $processId -match '^\d+$') {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    Write-Host "✅ 已停止 Developer Workstation (端口 8094)" -ForegroundColor Green
                }
            }
        }
    } catch {
        Write-Host "⚠️  无法停止 Developer Workstation: $_" -ForegroundColor Yellow
    }
    Remove-Item $devWorkstationPidFile -ErrorAction SilentlyContinue
}

# 停止 User Portal
$userPortalPidFile = Join-Path $LOG_DIR "user-portal.pid"
if (Test-Path $userPortalPidFile) {
    $pid = Get-Content $userPortalPidFile -Raw
    $pid = $pid.Trim()
    try {
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $pid -Force
            Write-Host "✅ 已停止 User Portal (PID: $pid)" -ForegroundColor Green
        } else {
            $netstat = netstat -ano | Select-String ":8093.*LISTENING"
            if ($netstat) {
                $processId = ($netstat -split '\s+')[-1]
                if ($processId -and $processId -match '^\d+$') {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    Write-Host "✅ 已停止 User Portal (端口 8093)" -ForegroundColor Green
                }
            }
        }
    } catch {
        Write-Host "⚠️  无法停止 User Portal: $_" -ForegroundColor Yellow
    }
    Remove-Item $userPortalPidFile -ErrorAction SilentlyContinue
}

# 清理所有相关的 Java 进程（作为后备方案，通过端口）
Write-Host ""
Write-Host "清理残留的 Java 进程..." -ForegroundColor Gray
$backendPorts = @(8090, 8091, 8092, 8093, 8094)
foreach ($port in $backendPorts) {
    $netstat = netstat -ano | Select-String ":$port.*LISTENING"
    if ($netstat) {
        $processId = ($netstat -split '\s+')[-1]
        if ($processId -and $processId -match '^\d+$') {
            try {
                $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
                if ($process -and $process.ProcessName -eq "java") {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                }
            } catch {
                # 忽略错误
            }
        }
    }
}

Write-Host ""
Write-Host "✅ 所有后端服务已停止" -ForegroundColor Green
Write-Host ""
