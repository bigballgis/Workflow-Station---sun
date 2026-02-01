# =====================================================
# 停止前端服务脚本（Windows PowerShell）
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

Write-Host "🛑 停止前端服务..." -ForegroundColor Yellow
Write-Host ""

# 停止 Frontend Admin
$frontendAdminPidFile = Join-Path $LOG_DIR "frontend-admin.pid"
if (Test-Path $frontendAdminPidFile) {
    $pid = Get-Content $frontendAdminPidFile -Raw
    $pid = $pid.Trim()
    try {
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $pid -Force
            Write-Host "✅ 已停止 Frontend Admin (PID: $pid)" -ForegroundColor Green
        } else {
            # 尝试通过端口停止（Node.js 进程）
            $netstat = netstat -ano | Select-String ":3000.*LISTENING"
            if ($netstat) {
                $processId = ($netstat -split '\s+')[-1]
                if ($processId -and $processId -match '^\d+$') {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    Write-Host "✅ 已停止 Frontend Admin (端口 3000)" -ForegroundColor Green
                }
            }
        }
    } catch {
        Write-Host "⚠️  无法停止 Frontend Admin: $_" -ForegroundColor Yellow
    }
    Remove-Item $frontendAdminPidFile -ErrorAction SilentlyContinue
}

# 停止 Frontend Portal
$frontendPortalPidFile = Join-Path $LOG_DIR "frontend-portal.pid"
if (Test-Path $frontendPortalPidFile) {
    $pid = Get-Content $frontendPortalPidFile -Raw
    $pid = $pid.Trim()
    try {
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $pid -Force
            Write-Host "✅ 已停止 Frontend Portal (PID: $pid)" -ForegroundColor Green
        } else {
            $netstat = netstat -ano | Select-String ":3001.*LISTENING"
            if ($netstat) {
                $processId = ($netstat -split '\s+')[-1]
                if ($processId -and $processId -match '^\d+$') {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    Write-Host "✅ 已停止 Frontend Portal (端口 3001)" -ForegroundColor Green
                }
            }
        }
    } catch {
        Write-Host "⚠️  无法停止 Frontend Portal: $_" -ForegroundColor Yellow
    }
    Remove-Item $frontendPortalPidFile -ErrorAction SilentlyContinue
}

# 停止 Frontend Developer
$frontendDeveloperPidFile = Join-Path $LOG_DIR "frontend-developer.pid"
if (Test-Path $frontendDeveloperPidFile) {
    $pid = Get-Content $frontendDeveloperPidFile -Raw
    $pid = $pid.Trim()
    try {
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $pid -Force
            Write-Host "✅ 已停止 Frontend Developer (PID: $pid)" -ForegroundColor Green
        } else {
            $netstat = netstat -ano | Select-String ":3002.*LISTENING"
            if ($netstat) {
                $processId = ($netstat -split '\s+')[-1]
                if ($processId -and $processId -match '^\d+$') {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    Write-Host "✅ 已停止 Frontend Developer (端口 3002)" -ForegroundColor Green
                }
            }
        }
    } catch {
        Write-Host "⚠️  无法停止 Frontend Developer: $_" -ForegroundColor Yellow
    }
    Remove-Item $frontendDeveloperPidFile -ErrorAction SilentlyContinue
}

# 通过端口清理残留的前端进程（更可靠的方法）
Write-Host ""
Write-Host "清理残留的前端进程..." -ForegroundColor Gray
$frontendPorts = @(3000, 3001, 3002)
foreach ($port in $frontendPorts) {
    $netstat = netstat -ano | Select-String ":$port.*LISTENING"
    if ($netstat) {
        $processId = ($netstat -split '\s+')[-1]
        if ($processId -and $processId -match '^\d+$') {
            try {
                $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
                if ($process -and ($process.ProcessName -eq "node" -or $process.ProcessName -eq "npm" -or $process.ProcessName -eq "pnpm")) {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                }
            } catch {
                # 忽略错误
            }
        }
    }
}

Write-Host ""
Write-Host "✅ 所有前端服务已停止" -ForegroundColor Green
Write-Host ""
