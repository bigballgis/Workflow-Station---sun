# 工作流平台架构图文档

## 📚 文档概述

本目录包含工作流平台的完整解决方案架构图，提供多种格式以适应不同的使用场景。

---

## 📁 文件列表

| 文件名 | 格式 | 说明 |
|--------|------|------|
| `architecture-diagrams.md` | Mermaid | 包含10个架构图的 Mermaid 代码 |
| `architecture-plantuml.puml` | PlantUML | 包含7个架构图的 PlantUML 代码 |
| `architecture-drawio.xml` | Draw.io | 系统总体架构的 Draw.io 格式 |
| `confluence-import-guide.md` | Markdown | Confluence 导入详细指南 |
| `generate-diagrams.ps1` | PowerShell | 批量生成图片的脚本 |
| `README-架构图.md` | Markdown | 本文档 |

---

## 🎨 架构图清单

### 1. 系统总体架构图
展示前端、网关、微服务、工作流引擎和数据层的整体结构。

**包含组件**:
- 前端层: Admin Center, Developer Workstation, User Portal
- API网关层: Spring Cloud Gateway
- 微服务层: 4个核心服务
- 工作流引擎: Flowable Engine
- 数据层: PostgreSQL, Redis

### 2. 微服务交互架构图
展示各微服务之间的调用关系和依赖。

**关键交互**:
- User Portal → Workflow Engine (启动流程、完成任务)
- Workflow Engine → Flowable (Flowable API)
- Workflow Engine → Admin Center (查询用户/部门信息)
- Developer Workstation → Workflow Engine (部署流程)

### 3. 工作流引擎架构图
详细展示工作流引擎的内部组件和 Flowable 集成。

**核心组件**:
- ProcessComponent, TaskProcessComponent (User Portal)
- WorkflowEngineClient (HTTP 客户端)
- ProcessController, TaskController (REST API)
- ProcessEngineComponent, TaskManagerComponent (业务逻辑)
- TaskAssigneeResolver, TaskAssignmentListener (任务分配)
- Flowable Services (RuntimeService, TaskService, etc.)

### 4. 任务分配机制架构图
展示7种任务分配方式的处理流程。

**分配类型**:
- 直接分配: FUNCTION_MANAGER, ENTITY_MANAGER, INITIATOR
- 认领分配: DEPT_OTHERS, PARENT_DEPT, FIXED_DEPT, VIRTUAL_GROUP

### 5. 数据库架构图
ER 图展示核心数据表关系。

**表分类**:
- admin_* : 管理员中心表 (组织、部门、用户、角色、虚拟组)
- dw_* : 开发者工作站表 (功能单元、表定义、字段、表单、动作、流程)
- sys_* : 平台安全表 (用户、角色、权限)
- ACT_* : Flowable 引擎表

### 6. 部署架构图
Kubernetes 部署拓扑。

**部署层次**:
- Ingress Layer: Nginx Ingress Controller
- Frontend Pods: 3个前端应用
- Gateway Pod: API Gateway
- Backend Pods: 4个后端服务
- Data Layer: PostgreSQL, Redis StatefulSet
- Config & Discovery: ConfigMap, Secrets

### 7. 技术栈架构图
前后端技术选型。

**前端技术栈**:
- Vue 3, Element Plus, BPMN.js, Pinia, Vue Router, Axios, Vue I18n

**后端技术栈**:
- Spring Boot 3.x, Spring Cloud Gateway, Spring Security
- Flowable 7.x, Spring Data JPA, Flyway, Lombok, Jackson

**数据存储**:
- PostgreSQL 15, Redis 7

**DevOps**:
- Docker, Kubernetes, Helm, Maven, Vite

### 8. 安全架构图
JWT 认证和 RBAC 授权流程。

**安全流程**:
- 用户登录 → 验证凭证 → 生成 JWT Token
- API 请求 → JWT 验证 → RBAC 权限检查 → 业务逻辑

### 9. 功能单元设计流程图
从设计到部署的完整流程。

**设计步骤**:
1. 创建功能单元
2. 设计表结构
3. 定义字段
4. 设置外键关系
5. 创建表单
6. 绑定表单-表关系
7. 配置表单规则 (form-create)
8. 创建动作
9. 配置动作 (config_json)
10. 设计流程 (BPMN)
11. 绑定节点 (表单+动作)
12. 配置处理人 (7种分配方式)
13. 部署流程
14. 测试流程

### 10. 系统集成架构图
外部系统集成和监控日志。

**外部系统**:
- LDAP/AD (用户目录)
- 邮件服务 (SMTP)
- 短信服务 (SMS Gateway)
- 文件存储 (MinIO/S3)

**监控与日志**:
- Prometheus (监控)
- Grafana (可视化)
- ELK Stack (日志分析)

---

## 🚀 快速开始

### 方法 1: 在线预览（最快）

1. 访问 **https://mermaid.live/**
2. 打开 `architecture-diagrams.md`
3. 复制任意一个 mermaid 代码块
4. 粘贴到编辑器
5. 导出为 PNG/SVG

### 方法 2: Confluence 导入（推荐）

1. 在 Confluence 中安装 **Draw.io** 插件
2. 创建新页面
3. 插入 Draw.io Diagram
4. 导入 `architecture-drawio.xml`
5. 保存

详细步骤请参考 `confluence-import-guide.md`

### 方法 3: 批量生成图片

```powershell
cd docs
powershell -ExecutionPolicy Bypass -File generate-diagrams.ps1
```

---

## 🎯 使用场景

### 场景 1: 技术方案评审
**推荐图表**: 
- 系统总体架构图
- 技术栈架构图
- 部署架构图

### 场景 2: 开发团队培训
**推荐图表**:
- 微服务交互架构图
- 工作流引擎架构图
- 数据库架构图

### 场景 3: 运维部署文档
**推荐图表**:
- 部署架构图
- 系统集成架构图
- 安全架构图

### 场景 4: 产品设计文档
**推荐图表**:
- 功能单元设计流程图
- 任务分配机制架构图
- 系统总体架构图

---

## 📊 格式对比

| 格式 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| **Mermaid** | 代码简洁、易于版本控制 | 样式定制有限 | 技术文档、Git 仓库 |
| **PlantUML** | 功能强大、支持多种图表 | 语法复杂 | 详细设计文档 |
| **Draw.io** | 可视化编辑、样式丰富 | 不易版本控制 | 演示文稿、Confluence |
| **PNG/SVG** | 兼容性好、无需插件 | 不可编辑 | 快速分享、静态文档 |

---

## 🔧 工具安装

### Mermaid CLI
```bash
npm install -g @mermaid-js/mermaid-cli
```

### PlantUML
```bash
# Windows (Chocolatey)
choco install plantuml

# macOS (Homebrew)
brew install plantuml

# 或手动下载
# https://plantuml.com/download
```

### Draw.io Desktop
下载地址: https://github.com/jgraph/drawio-desktop/releases

---

## 📖 相关文档

- [工作流引擎架构指南](../.kiro/steering/workflow-engine-architecture.md)
- [功能单元SQL生成指南](../.kiro/steering/function-unit-generation.md)
- [开发细则指南](../.kiro/steering/development-guidelines.md)
- [多语言开发指南](../.kiro/steering/i18n-guidelines.md)

---

## 🆘 常见问题

### Q: 如何选择合适的格式？
**A**: 
- 需要在 Confluence 中编辑 → Draw.io
- 需要版本控制 → Mermaid 或 PlantUML
- 快速分享 → 导出为图片

### Q: 图表太复杂看不清怎么办？
**A**: 
- 使用在线工具时调整浏览器缩放
- Draw.io 中可以分层显示
- 导出高分辨率图片 (SVG 格式)

### Q: 如何更新架构图？
**A**: 
- Mermaid/PlantUML: 直接编辑代码文件
- Draw.io: 在编辑器中修改
- 图片: 重新生成并替换

### Q: 中文显示有问题怎么办？
**A**: 
- 确保文件使用 UTF-8 编码
- PlantUML 添加字体设置
- 使用在线工具通常没有编码问题

---

## 📝 维护说明

### 更新架构图

1. **修改源文件**
   - Mermaid: 编辑 `architecture-diagrams.md`
   - PlantUML: 编辑 `architecture-plantuml.puml`
   - Draw.io: 使用 Draw.io 编辑器打开 `architecture-drawio.xml`

2. **重新生成图片**
   ```powershell
   cd docs
   powershell -ExecutionPolicy Bypass -File generate-diagrams.ps1
   ```

3. **更新 Confluence**
   - Draw.io: 双击图表进入编辑模式
   - 图片: 重新上传替换

### 添加新图表

1. 在对应的源文件中添加新图表代码
2. 更新本 README 文档的图表清单
3. 重新生成图片（如需要）

---

## 📞 联系方式

如有问题或建议，请联系架构团队。

---

**文档版本**: 1.0  
**最后更新**: 2026-01-14  
**维护者**: 架构团队
