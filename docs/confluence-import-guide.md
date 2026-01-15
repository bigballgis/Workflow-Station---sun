# Confluence 架构图导入指南

本指南提供多种方法将工作流平台架构图导入到 Confluence 中。

---

## 📋 文件清单

已生成以下架构图文件：

| 文件名 | 格式 | 图表数量 | 推荐用途 |
|--------|------|---------|---------|
| `architecture-diagrams.md` | Mermaid | 10个 | 在线预览、Mermaid插件 |
| `architecture-plantuml.puml` | PlantUML | 7个 | PlantUML插件、在线转换 |
| `architecture-drawio.xml` | Draw.io | 1个 | Draw.io插件、桌面编辑 |
| `generate-diagrams.ps1` | PowerShell | - | 批量生成图片 |

---

## 🎯 方案 1: Draw.io 插件（最推荐）

**优点**: 可编辑、交互式、原生支持  
**适用**: Confluence Cloud 和 Server

### 步骤：

#### 1.1 安装 Draw.io 插件

1. 登录 Confluence 管理后台
2. 导航到 **Settings** > **Find new apps**
3. 搜索 **"draw.io Diagrams for Confluence"**
4. 点击 **Install** 安装插件

#### 1.2 导入架构图

1. 创建或编辑 Confluence 页面
2. 点击工具栏的 **"+"** 按钮
3. 选择 **"Draw.io Diagram"**
4. 在弹出窗口中选择 **"Import"**
5. 上传 `architecture-drawio.xml` 文件
6. 点击 **"Save"** 保存

#### 1.3 编辑和自定义

- 双击图表进入编辑模式
- 可以修改颜色、布局、文字
- 支持导出为 PNG、SVG、PDF

---

## 🎨 方案 2: PlantUML 插件

**优点**: 代码即图表、版本控制友好  
**适用**: 技术文档、需要频繁更新的图表

### 步骤：

#### 2.1 安装 PlantUML 插件

1. 进入 Confluence 管理后台
2. 搜索 **"PlantUML for Confluence"**
3. 安装插件

#### 2.2 插入 PlantUML 图表

1. 编辑 Confluence 页面
2. 输入 `/plantuml` 或点击 **"+"** > **"PlantUML"**
3. 打开 `architecture-plantuml.puml` 文件
4. 复制其中一个图表的代码（从 `@startuml` 到 `@enduml`）
5. 粘贴到 PlantUML 宏中
6. 保存页面

#### 2.3 可用的图表

`architecture-plantuml.puml` 包含以下图表：

1. 工作流平台系统架构
2. 工作流引擎架构
3. 任务分配机制
4. 微服务交互
5. 部署架构
6. 数据库ER图
7. 功能单元设计流程

---

## 🖼️ 方案 3: 导出为图片（最简单）

**优点**: 无需插件、兼容所有版本  
**适用**: 快速分享、静态文档

### 步骤：

#### 3.1 使用 Mermaid Live 在线转换

1. 访问 **https://mermaid.live/**
2. 打开 `architecture-diagrams.md` 文件
3. 复制一个 mermaid 代码块（从 ` ```mermaid` 到 ` ``` `）
4. 粘贴到 Mermaid Live 编辑器
5. 点击 **"Actions"** > **"Export as PNG"** 或 **"Export as SVG"**
6. 下载图片

#### 3.2 使用 PlantUML 在线转换

1. 访问 **https://www.plantuml.com/plantuml/uml/**
2. 打开 `architecture-plantuml.puml` 文件
3. 复制一个图表的代码（从 `@startuml` 到 `@enduml`）
4. 粘贴到在线编辑器
5. 点击 **"Submit"** 生成图片
6. 右键保存图片

#### 3.3 在 Confluence 中插入图片

1. 编辑 Confluence 页面
2. 直接拖拽图片到页面
3. 或点击 **"Insert"** > **"Image"** > **"Upload"**
4. 添加图片标题和说明

---

## 🔧 方案 4: 使用 PowerShell 批量生成

**优点**: 自动化、批量处理  
**适用**: 需要生成多个图片文件

### 步骤：

#### 4.1 安装必要工具

**选项 A: Mermaid CLI**
```powershell
npm install -g @mermaid-js/mermaid-cli
```

**选项 B: PlantUML**
```powershell
# 使用 Chocolatey
choco install plantuml

# 或手动下载
# https://plantuml.com/download
```

#### 4.2 运行生成脚本

```powershell
cd docs
powershell -ExecutionPolicy Bypass -File generate-diagrams.ps1
```

#### 4.3 生成单个图片

**Mermaid:**
```powershell
# 需要先将 mermaid 代码块提取到单独的 .mmd 文件
mmdc -i system-architecture.mmd -o system-architecture.png
```

**PlantUML:**
```powershell
plantuml -tpng architecture-plantuml.puml
# 会生成多个 PNG 文件
```

---

## 📊 方案对比

| 方案 | 难度 | 可编辑 | 交互性 | 版本控制 | 推荐度 |
|------|------|--------|--------|---------|--------|
| Draw.io 插件 | ⭐⭐ | ✅ | ✅ | ⚠️ | ⭐⭐⭐⭐⭐ |
| PlantUML 插件 | ⭐⭐⭐ | ✅ | ❌ | ✅ | ⭐⭐⭐⭐ |
| 导出图片 | ⭐ | ❌ | ❌ | ❌ | ⭐⭐⭐ |
| PowerShell 批量 | ⭐⭐⭐⭐ | ❌ | ❌ | ✅ | ⭐⭐⭐ |

---

## 🎯 推荐方案

### 场景 1: 首次导入，需要快速展示
**推荐**: 方案 3（导出为图片）
- 访问 https://mermaid.live/
- 复制粘贴代码
- 导出图片并上传到 Confluence

### 场景 2: 需要频繁更新和编辑
**推荐**: 方案 1（Draw.io 插件）
- 安装 Draw.io 插件
- 导入 `architecture-drawio.xml`
- 在 Confluence 中直接编辑

### 场景 3: 技术团队，需要版本控制
**推荐**: 方案 2（PlantUML 插件）
- 安装 PlantUML 插件
- 使用 `architecture-plantuml.puml` 中的代码
- 代码存储在 Git 中

### 场景 4: 需要生成多种格式
**推荐**: 方案 4（PowerShell 批量）
- 安装 Mermaid CLI 或 PlantUML
- 运行 `generate-diagrams.ps1`
- 批量生成 PNG/SVG 文件

---

## 🆘 常见问题

### Q1: Confluence 没有 Draw.io 插件怎么办？
**A**: 使用方案 3（导出为图片），或联系管理员安装插件。

### Q2: 图片太大或太小怎么办？
**A**: 
- Mermaid Live: 调整浏览器缩放后再导出
- PlantUML: 在代码中添加 `scale 1.5` 调整大小
- Draw.io: 在编辑器中调整画布大小

### Q3: 中文显示乱码怎么办？
**A**: 
- 确保文件使用 UTF-8 编码
- PlantUML 添加: `skinparam defaultFontName "Microsoft YaHei"`
- 使用在线工具通常不会有编码问题

### Q4: 如何更新已导入的图表？
**A**: 
- Draw.io: 双击图表进入编辑模式
- PlantUML: 编辑宏中的代码
- 图片: 重新生成并替换

---

## 📝 快速开始

**最快的方法（5分钟内完成）：**

1. 打开 https://mermaid.live/
2. 打开 `docs/architecture-diagrams.md`
3. 复制第一个 mermaid 代码块
4. 粘贴到 Mermaid Live
5. 点击 "Export as PNG"
6. 上传到 Confluence

**完成！** 🎉

---

## 📞 需要帮助？

如果遇到问题，请检查：
- Confluence 版本是否支持所选插件
- 是否有管理员权限安装插件
- 文件编码是否为 UTF-8
- 浏览器是否支持（推荐使用 Chrome）
