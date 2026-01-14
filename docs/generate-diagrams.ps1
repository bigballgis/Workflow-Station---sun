# 架构图生成脚本
# 用于将 Mermaid 和 PlantUML 图表转换为图片格式

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "工作流平台架构图生成工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查是否安装了必要的工具
function Test-Command {
    param($Command)
    try {
        if (Get-Command $Command -ErrorAction Stop) {
            return $true
        }
    }
    catch {
        return $false
    }
}

# 方案 1: 使用 Mermaid CLI (推荐)
Write-Host "方案 1: 使用 Mermaid CLI 生成图片" -ForegroundColor Yellow
Write-Host "----------------------------------------" -ForegroundColor Yellow

if (Test-Command "mmdc") {
    Write-Host "✓ Mermaid CLI 已安装" -ForegroundColor Green
    Write-Host "正在生成 Mermaid 图表..." -ForegroundColor Cyan
    
    # 这里需要手动提取每个 mermaid 代码块到单独的文件
    Write-Host "请手动提取 architecture-diagrams.md 中的每个 mermaid 代码块到单独的 .mmd 文件" -ForegroundColor Yellow
    Write-Host "然后运行: mmdc -i diagram.mmd -o diagram.png" -ForegroundColor Yellow
} else {
    Write-Host "✗ Mermaid CLI 未安装" -ForegroundColor Red
    Write-Host "安装方法: npm install -g @mermaid-js/mermaid-cli" -ForegroundColor Yellow
}

Write-Host ""

# 方案 2: 使用 PlantUML
Write-Host "方案 2: 使用 PlantUML 生成图片" -ForegroundColor Yellow
Write-Host "----------------------------------------" -ForegroundColor Yellow

if (Test-Command "plantuml") {
    Write-Host "✓ PlantUML 已安装" -ForegroundColor Green
    Write-Host "正在生成 PlantUML 图表..." -ForegroundColor Cyan
    
    $pumlFile = "architecture-plantuml.puml"
    if (Test-Path $pumlFile) {
        plantuml -tpng $pumlFile
        Write-Host "✓ PlantUML 图表已生成" -ForegroundColor Green
    } else {
        Write-Host "✗ 找不到 $pumlFile 文件" -ForegroundColor Red
    }
} else {
    Write-Host "✗ PlantUML 未安装" -ForegroundColor Red
    Write-Host "安装方法:" -ForegroundColor Yellow
    Write-Host "1. 下载 plantuml.jar: https://plantuml.com/download" -ForegroundColor Yellow
    Write-Host "2. 创建 plantuml.bat 包装脚本" -ForegroundColor Yellow
    Write-Host "3. 或使用 Chocolatey: choco install plantuml" -ForegroundColor Yellow
}

Write-Host ""

# 方案 3: 在线转换 (最简单)
Write-Host "方案 3: 使用在线工具转换 (推荐)" -ForegroundColor Yellow
Write-Host "----------------------------------------" -ForegroundColor Yellow
Write-Host "Mermaid 在线编辑器:" -ForegroundColor Cyan
Write-Host "  https://mermaid.live/" -ForegroundColor White
Write-Host "  - 复制 architecture-diagrams.md 中的 mermaid 代码" -ForegroundColor Gray
Write-Host "  - 粘贴到编辑器" -ForegroundColor Gray
Write-Host "  - 点击 'Actions' > 'Export as PNG/SVG'" -ForegroundColor Gray
Write-Host ""
Write-Host "PlantUML 在线编辑器:" -ForegroundColor Cyan
Write-Host "  https://www.plantuml.com/plantuml/uml/" -ForegroundColor White
Write-Host "  - 复制 architecture-plantuml.puml 中的代码" -ForegroundColor Gray
Write-Host "  - 粘贴到编辑器" -ForegroundColor Gray
Write-Host "  - 下载生成的图片" -ForegroundColor Gray
Write-Host ""

# 方案 4: Draw.io
Write-Host "方案 4: 使用 Draw.io (最灵活)" -ForegroundColor Yellow
Write-Host "----------------------------------------" -ForegroundColor Yellow
Write-Host "在线版本:" -ForegroundColor Cyan
Write-Host "  https://app.diagrams.net/" -ForegroundColor White
Write-Host "  - 打开 architecture-drawio.xml 文件" -ForegroundColor Gray
Write-Host "  - 编辑和导出为 PNG/SVG" -ForegroundColor Gray
Write-Host ""
Write-Host "桌面版本:" -ForegroundColor Cyan
Write-Host "  下载: https://github.com/jgraph/drawio-desktop/releases" -ForegroundColor White
Write-Host ""

# 总结
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "推荐方案" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. 快速预览: 使用在线工具 (方案 3)" -ForegroundColor Green
Write-Host "   - Mermaid Live: https://mermaid.live/" -ForegroundColor White
Write-Host "   - PlantUML Online: https://www.plantuml.com/plantuml/uml/" -ForegroundColor White
Write-Host ""
Write-Host "2. Confluence 导入: 使用 Draw.io (方案 4)" -ForegroundColor Green
Write-Host "   - 在 Confluence 中安装 Draw.io 插件" -ForegroundColor White
Write-Host "   - 直接导入 architecture-drawio.xml" -ForegroundColor White
Write-Host ""
Write-Host "3. 批量生成: 安装 CLI 工具 (方案 1 或 2)" -ForegroundColor Green
Write-Host "   - Mermaid CLI: npm install -g @mermaid-js/mermaid-cli" -ForegroundColor White
Write-Host "   - PlantUML: choco install plantuml" -ForegroundColor White
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "文件清单" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ architecture-diagrams.md    - Mermaid 格式 (10个图表)" -ForegroundColor Green
Write-Host "✓ architecture-plantuml.puml  - PlantUML 格式 (7个图表)" -ForegroundColor Green
Write-Host "✓ architecture-drawio.xml     - Draw.io 格式 (系统总体架构)" -ForegroundColor Green
Write-Host "✓ confluence-import-guide.md  - Confluence 导入指南" -ForegroundColor Green
Write-Host ""

Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
