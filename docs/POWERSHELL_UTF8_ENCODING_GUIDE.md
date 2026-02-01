# PowerShell UTF-8 编码配置指南

## 问题描述

在 Windows 上运行 PowerShell 脚本时，中文日志输出显示为乱码。

### 原因

- **Windows PowerShell 默认编码**：GBK/GB2312 (代码页 936)
- **脚本和日志文件编码**：UTF-8
- **结果**：编码不匹配导致中文乱码

### 示例

```powershell
# 乱码示例
Write-Host "启动服务..."
# 输出: ����������...

# 日志文件乱码
Get-Content log.txt
# 输出: ������������...
```

## 解决方案

### 方案 1：在每个脚本开头添加 UTF-8 配置（推荐）

在每个 `.ps1` 脚本的**最开头**添加以下代码：

```powershell
# ========================================
# UTF-8 编码配置（解决中文乱码）
# ========================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
chcp 65001 | Out-Null
```

**说明**：
- `[Console]::OutputEncoding` - 设置控制台输出编码
- `$OutputEncoding` - 设置 PowerShell 输出编码
- `$PSDefaultParameterValues` - 设置所有命令的默认编码参数
- `chcp 65001` - 设置代码页为 UTF-8（65001）

### 方案 2：配置 PowerShell 配置文件（全局生效）

#### 步骤 1：运行配置脚本

```powershell
# 执行 UTF-8 配置脚本
.\setup-powershell-utf8.ps1
```

#### 步骤 2：重启 PowerShell

关闭并重新打开 PowerShell 窗口，或执行：

```powershell
. $PROFILE
```

#### 步骤 3：验证配置

```powershell
# 检查编码
[Console]::OutputEncoding
# 应该显示: utf-8

# 测试中文输出
Write-Host "测试中文：你好，世界！" -ForegroundColor Green
```

### 方案 3：使用 Windows Terminal（推荐）

Windows Terminal 默认支持 UTF-8，不需要额外配置。

#### 安装 Windows Terminal

```powershell
# 通过 Microsoft Store 安装
# 或使用 winget
winget install Microsoft.WindowsTerminal
```

#### 配置 Windows Terminal

1. 打开 Windows Terminal
2. 设置 → 配置文件 → PowerShell
3. 外观 → 字体：选择支持中文的字体（如 Cascadia Code, Consolas）
4. 高级 → 文本编码：UTF-8

## 已修改的脚本

以下脚本已添加 UTF-8 编码配置：

- ✅ `start-all.ps1` - 启动所有服务
- ✅ `stop-all.ps1` - 停止所有服务
- ✅ `stop-services.ps1` - 停止项目服务
- ✅ `stop-backend.ps1` - 停止后端服务
- ✅ `stop-frontend.ps1` - 停止前端服务
- ✅ `setup-powershell-utf8.ps1` - UTF-8 配置脚本

**所有项目 PowerShell 脚本已完成 UTF-8 编码配置！** (2026-01-31)

## 脚本模板

### PowerShell 脚本标准模板

```powershell
# ========================================
# 脚本名称和描述
# ========================================

# ========================================
# UTF-8 编码配置（解决中文乱码）
# ========================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
chcp 65001 | Out-Null

# ========================================
# 参数定义
# ========================================
param(
    [string]$Parameter1,
    [switch]$Flag1
)

# ========================================
# 错误处理
# ========================================
$ErrorActionPreference = "Stop"

# ========================================
# 主逻辑
# ========================================
try {
    Write-Host "开始执行..." -ForegroundColor Green
    
    # 你的代码...
    
    Write-Host "✓ 执行成功！" -ForegroundColor Green
}
catch {
    Write-Host "✗ 执行失败: $_" -ForegroundColor Red
    exit 1
}
```

## 文件读写编码

### 读取文件

```powershell
# 使用 UTF-8 编码读取文件
$content = Get-Content -Path "file.txt" -Encoding UTF8

# 读取完整文件（保留换行）
$content = Get-Content -Path "file.txt" -Raw -Encoding UTF8

# 读取 SQL 文件
$sql = Get-Content -Path "script.sql" -Raw -Encoding UTF8
```

### 写入文件

```powershell
# 使用 UTF-8 编码写入文件（推荐）
[System.IO.File]::WriteAllText("file.txt", $content, [System.Text.Encoding]::UTF8)

# 或使用 Set-Content
Set-Content -Path "file.txt" -Value $content -Encoding UTF8

# 追加内容
Add-Content -Path "file.txt" -Value $newContent -Encoding UTF8

# 输出到文件（使用 Out-File）
"内容" | Out-File -FilePath "file.txt" -Encoding UTF8
```

### 日志文件

```powershell
# 创建日志函数
function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "[$timestamp] [$Level] $Message"
    
    # 输出到控制台
    Write-Host $logMessage
    
    # 写入日志文件（UTF-8 编码）
    Add-Content -Path "app.log" -Value $logMessage -Encoding UTF8
}

# 使用日志函数
Write-Log "应用启动" "INFO"
Write-Log "处理数据" "DEBUG"
Write-Log "发生错误" "ERROR"
```

## Docker 相关命令

### 执行 SQL 文件到 Docker PostgreSQL

```powershell
# 方法 1：使用 Get-Content（推荐）
Get-Content -Path "script.sql" -Raw -Encoding UTF8 | docker exec -i platform-postgres psql -U platform -d workflow_platform

# 方法 2：使用 docker cp
docker cp script.sql platform-postgres:/tmp/script.sql
docker exec -i platform-postgres psql -U platform -d workflow_platform -f /tmp/script.sql
```

### 查看 Docker 日志

```powershell
# 查看日志（UTF-8 编码）
docker logs platform-postgres 2>&1 | Out-String -Stream | ForEach-Object { 
    [System.Text.Encoding]::UTF8.GetString([System.Text.Encoding]::Default.GetBytes($_))
}

# 或直接查看（如果已配置 UTF-8）
docker logs platform-postgres
```

## 常见问题

### 问题 1：配置后仍然乱码

**原因**：文件本身不是 UTF-8 编码

**解决**：
```powershell
# 检查文件编码
Get-Content -Path "file.txt" -Encoding Default | Out-Null
# 如果报错，说明编码不匹配

# 转换文件编码为 UTF-8
$content = Get-Content -Path "file.txt" -Encoding Default
[System.IO.File]::WriteAllText("file.txt", $content, [System.Text.Encoding]::UTF8)
```

### 问题 2：chcp 65001 导致某些命令失败

**原因**：某些旧的 Windows 命令不支持 UTF-8

**解决**：
```powershell
# 临时切换回 GBK
chcp 936 | Out-Null
# 执行命令
some-old-command
# 切换回 UTF-8
chcp 65001 | Out-Null
```

### 问题 3：PowerShell ISE 中文乱码

**原因**：PowerShell ISE 不完全支持 UTF-8

**解决**：
- 使用 Visual Studio Code + PowerShell 扩展
- 或使用 Windows Terminal + PowerShell

### 问题 4：重定向输出乱码

**原因**：重定向操作符 `>` 使用默认编码

**解决**：
```powershell
# 不要使用 >
command > output.txt  # ❌ 可能乱码

# 使用 Out-File 并指定编码
command | Out-File -FilePath output.txt -Encoding UTF8  # ✅ 正确

# 或使用 Tee-Object
command | Tee-Object -FilePath output.txt -Encoding UTF8
```

## VS Code 配置

如果使用 VS Code 编辑 PowerShell 脚本：

### settings.json

```json
{
    "files.encoding": "utf8",
    "files.autoGuessEncoding": false,
    "[powershell]": {
        "files.encoding": "utf8"
    },
    "powershell.integratedConsole.suppressStartupBanner": false,
    "terminal.integrated.defaultProfile.windows": "PowerShell",
    "terminal.integrated.profiles.windows": {
        "PowerShell": {
            "source": "PowerShell",
            "args": [
                "-NoExit",
                "-Command",
                "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; $OutputEncoding = [System.Text.Encoding]::UTF8"
            ]
        }
    }
}
```

## 验证编码配置

### 检查脚本

```powershell
# 创建测试脚本
$testScript = @'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "当前编码配置："
Write-Host "Console.OutputEncoding: $([Console]::OutputEncoding.EncodingName)"
Write-Host "OutputEncoding: $($OutputEncoding.EncodingName)"
Write-Host ""
Write-Host "测试中文输出："
Write-Host "你好，世界！" -ForegroundColor Green
Write-Host "启动服务..." -ForegroundColor Cyan
Write-Host "✓ 配置成功" -ForegroundColor Green
Write-Host "✗ 配置失败" -ForegroundColor Red
'@

# 保存并执行
[System.IO.File]::WriteAllText("test-encoding.ps1", $testScript, [System.Text.Encoding]::UTF8)
.\test-encoding.ps1
```

### 期望输出

```
当前编码配置：
Console.OutputEncoding: Unicode (UTF-8)
OutputEncoding: Unicode (UTF-8)

测试中文输出：
你好，世界！
启动服务...
✓ 配置成功
✗ 配置失败
```

## 最佳实践

1. **所有 PowerShell 脚本开头添加 UTF-8 配置**
2. **使用 Windows Terminal 而不是 cmd.exe**
3. **文件读写始终指定 `-Encoding UTF8`**
4. **使用 VS Code 编辑脚本，设置默认编码为 UTF-8**
5. **避免使用 PowerShell ISE**
6. **日志文件使用 UTF-8 编码**
7. **Git 配置使用 UTF-8**：
   ```bash
   git config --global core.quotepath false
   git config --global gui.encoding utf-8
   git config --global i18n.commit.encoding utf-8
   git config --global i18n.logoutputencoding utf-8
   ```

## 参考资料

- [PowerShell 编码文档](https://docs.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_character_encoding)
- [Windows Terminal 文档](https://docs.microsoft.com/en-us/windows/terminal/)
- [UTF-8 代码页](https://docs.microsoft.com/en-us/windows/win32/intl/code-page-identifiers)

---

**文档生成时间**：2026-01-31  
**适用系统**：Windows 10/11  
**PowerShell 版本**：5.1+ / PowerShell Core 7+
