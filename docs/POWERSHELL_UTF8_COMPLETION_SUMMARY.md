# PowerShell UTF-8 编码配置完成总结

## 任务概述

为所有项目 PowerShell 脚本添加 UTF-8 编码配置，解决 Windows 环境下中文日志乱码问题。

## 完成时间

2026-01-31

## 问题背景

- **问题**：在 Windows 上运行 PowerShell 脚本时，中文日志输出显示为乱码
- **原因**：Windows PowerShell 默认使用 GBK/GB2312 编码（代码页 936），而项目文件使用 UTF-8 编码
- **影响**：所有包含中文的日志输出、错误消息、状态提示都显示为乱码

## 解决方案

在每个 PowerShell 脚本的开头添加 UTF-8 编码配置：

```powershell
# ========================================
# UTF-8 编码配置（解决中文乱码）
# ========================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
chcp 65001 | Out-Null
```

## 已修改的脚本

| 脚本文件 | 说明 | 状态 |
|---------|------|------|
| `setup-powershell-utf8.ps1` | UTF-8 全局配置脚本 | ✅ 已完成 |
| `start-all.ps1` | 启动所有服务 | ✅ 已完成 |
| `stop-all.ps1` | 停止所有服务 | ✅ 已完成 |
| `stop-services.ps1` | 停止项目服务 | ✅ 已完成 |
| `stop-backend.ps1` | 停止后端服务 | ✅ 已完成 |
| `stop-frontend.ps1` | 停止前端服务 | ✅ 已完成 |

**总计：6 个脚本已完成配置**

## 配置位置

UTF-8 编码配置块添加在：
- 脚本文件的初始注释头之后
- `param()` 参数声明之前（如果有）
- 任何其他代码之前

## 配置说明

### 各配置项作用

1. **`[Console]::OutputEncoding`**
   - 设置控制台输出编码为 UTF-8
   - 影响 `Write-Host`、`Write-Output` 等命令的输出

2. **`$OutputEncoding`**
   - 设置 PowerShell 管道输出编码
   - 影响管道传递的数据编码

3. **`$PSDefaultParameterValues['*:Encoding']`**
   - 设置所有命令的默认 `-Encoding` 参数为 UTF-8
   - 影响 `Get-Content`、`Set-Content`、`Out-File` 等命令

4. **`chcp 65001`**
   - 设置 Windows 代码页为 UTF-8（代码页 65001）
   - 影响控制台的字符显示

## 验证方法

### 方法 1：运行脚本测试

```powershell
# 运行任意已修改的脚本
.\start-all.ps1

# 检查中文输出是否正常显示
# 应该看到：
# 🚀 启动工作流平台...
# ✅ 服务启动成功
```

### 方法 2：使用测试脚本

```powershell
# 创建测试脚本
$testScript = @'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
chcp 65001 | Out-Null

Write-Host "测试中文输出："
Write-Host "你好，世界！" -ForegroundColor Green
Write-Host "启动服务..." -ForegroundColor Cyan
Write-Host "✓ 配置成功" -ForegroundColor Green
'@

[System.IO.File]::WriteAllText("test-utf8.ps1", $testScript, [System.Text.Encoding]::UTF8)
.\test-utf8.ps1
```

### 期望结果

所有中文字符应该正确显示，不应该出现乱码（如 `������`）。

## 其他改进

### 1. 创建了全局配置脚本

`setup-powershell-utf8.ps1` - 一键配置 PowerShell 配置文件，使 UTF-8 编码全局生效。

### 2. 创建了详细文档

`docs/POWERSHELL_UTF8_ENCODING_GUIDE.md` - 包含：
- 问题原因分析
- 多种解决方案
- 文件读写编码最佳实践
- Docker 命令编码处理
- 常见问题解答
- VS Code 配置建议

## 注意事项

### 1. 脚本执行顺序

UTF-8 配置必须在脚本的最开始执行，在任何输出命令之前。

### 2. 文件编码

确保所有 PowerShell 脚本文件本身也是 UTF-8 编码保存的。

### 3. 终端选择

推荐使用 Windows Terminal 而不是传统的 cmd.exe 或 PowerShell ISE。

### 4. Git 配置

建议同时配置 Git 使用 UTF-8：

```bash
git config --global core.quotepath false
git config --global gui.encoding utf-8
git config --global i18n.commit.encoding utf-8
git config --global i18n.logoutputencoding utf-8
```

## 后续维护

### 新增 PowerShell 脚本时

1. 使用 UTF-8 编码保存文件
2. 在脚本开头添加 UTF-8 配置块
3. 测试中文输出是否正常

### 脚本模板

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
    [string]$Parameter1
)

# ========================================
# 主逻辑
# ========================================
Write-Host "开始执行..." -ForegroundColor Green
# 你的代码...
Write-Host "✓ 执行成功！" -ForegroundColor Green
```

## 相关文档

- [PowerShell UTF-8 编码配置指南](./POWERSHELL_UTF8_ENCODING_GUIDE.md) - 详细配置指南
- [服务启动指南](./startup-guide.md) - 服务启动相关文档

## 总结

✅ **任务完成**：所有项目 PowerShell 脚本已添加 UTF-8 编码配置

✅ **问题解决**：Windows 环境下中文日志乱码问题已解决

✅ **文档完善**：创建了详细的配置指南和最佳实践文档

✅ **可维护性**：提供了脚本模板，方便后续新增脚本时使用

---

**完成日期**：2026-01-31  
**修改文件数**：6 个 PowerShell 脚本  
**新增文档**：2 个（配置指南 + 完成总结）
