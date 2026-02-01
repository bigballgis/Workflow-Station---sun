# ========================================
# PowerShell UTF-8 编码配置脚本
# 解决 Windows PowerShell 中文乱码问题
# ========================================

Write-Host "正在配置 PowerShell UTF-8 编码..." -ForegroundColor Green

# 1. 检查 PowerShell 配置文件是否存在
$profilePath = $PROFILE.CurrentUserAllHosts
Write-Host "PowerShell 配置文件路径: $profilePath"

# 2. 创建配置文件目录（如果不存在）
$profileDir = Split-Path -Parent $profilePath
if (-not (Test-Path $profileDir)) {
    New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
    Write-Host "✓ 已创建配置文件目录" -ForegroundColor Green
}

# 3. 准备 UTF-8 配置内容
$utf8Config = @'
# ========================================
# UTF-8 编码配置（自动生成）
# ========================================

# 设置控制台输出编码为 UTF-8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# 设置 PowerShell 默认编码为 UTF-8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'

# 设置 chcp 为 65001 (UTF-8)
chcp 65001 | Out-Null

Write-Host "✓ UTF-8 编码已加载" -ForegroundColor Green

'@

# 4. 检查配置文件是否已包含 UTF-8 配置
if (Test-Path $profilePath) {
    $existingContent = Get-Content $profilePath -Raw -Encoding UTF8
    if ($existingContent -match "UTF-8 编码配置") {
        Write-Host "✓ UTF-8 配置已存在，跳过" -ForegroundColor Yellow
    } else {
        # 追加配置
        Add-Content -Path $profilePath -Value "`n$utf8Config" -Encoding UTF8
        Write-Host "✓ 已追加 UTF-8 配置到现有配置文件" -ForegroundColor Green
    }
} else {
    # 创建新配置文件
    Set-Content -Path $profilePath -Value $utf8Config -Encoding UTF8
    Write-Host "✓ 已创建新的配置文件并添加 UTF-8 配置" -ForegroundColor Green
}

# 5. 显示配置文件内容
Write-Host "`n配置文件内容预览:" -ForegroundColor Cyan
Write-Host "----------------------------------------"
Get-Content $profilePath -Encoding UTF8 | Select-Object -First 20
Write-Host "----------------------------------------"

# 6. 提示用户
Write-Host "`n✓ 配置完成！" -ForegroundColor Green
Write-Host "`n请执行以下操作之一：" -ForegroundColor Yellow
Write-Host "1. 关闭并重新打开 PowerShell 窗口"
Write-Host "2. 或执行: . `$PROFILE"
Write-Host "3. 或执行: & '$profilePath'"

Write-Host "`n配置文件位置: $profilePath" -ForegroundColor Cyan

# 7. 询问是否立即加载配置
$response = Read-Host "`n是否立即加载配置？(Y/N)"
if ($response -eq 'Y' -or $response -eq 'y') {
    . $profilePath
    Write-Host "✓ 配置已加载！" -ForegroundColor Green
    Write-Host "`n测试中文输出: 你好，世界！" -ForegroundColor Magenta
}
