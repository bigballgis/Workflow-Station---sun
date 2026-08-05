# Send Email 流程图着色 — 测试用例与执行说明

> 关联改动：P0 `TaskHistoryAssembler`、P1 `sendTaskDiagramStatus`、P2 `ProcessDiagram` Send Task 视觉  
> **不在范围**：P3（实例 definition vs FU BPMN 版本不一致）、Draft Return 方案 B（下游仍灰）

---

## 1. 前置条件

### 1.1 环境

| 项 | 要求 |
|----|------|
| 服务 | `user-portal`、`workflow-engine`、`developer-workstation`、`admin-center` 已部署且健康 |
| 边缘 | `http://localhost:3000`（或你的 `LOGIN_ORIGIN`） |
| 账号 | `developer` / `password`（或具审批权限的测试账号） |
| SMTP | Admin Center → System Config 已配置 SMTP（否则 Send Email 节点不会真正完成） |

### 1.2 测试用 Function Unit / 流程

准备一条 **含 Send Email（sendTask）节点** 的流程，建议结构：

```text
Start → User Task（提交）→ Send Email → User Task（审批）→ End
```

**记录以下 ID（手动测试必填）：**

| 字段 | 从哪里取 |
|------|----------|
| `processInstanceId` | 申请详情 URL / DB `up_process_instance` |
| `applicationId` | Portal `/portal/applications/{id}` |
| `taskId`（审批步） | 待办 `/portal/tasks/{id}` |
| Send Email 节点 `id` | DW Process Design 选中 Send Task → 属性或 BPMN XML |
| Send Email 节点 `name` | 设计器显示名称 |

### 1.3 构建与部署（改代码后必做）

```bash
# 后端
mvn -pl backend/workflow-engine-core -am package -DskipTests

# 前端
cd frontend/user-portal && pnpm run build

# Docker（dev 示例）
cd deploy/environments/dev
docker compose -f docker-compose.dev.yml --env-file .env up -d --build user-portal workflow-engine user-portal-frontend
```

---

## 2. 自动化单测（CI / 本地）

### 2.1 后端

```bash
cd backend/workflow-engine-core
mvn test -Dtest=TaskHistoryAssemblerSendEmailTest
```

| 用例 ID | 描述 | 期望 |
|---------|------|------|
| **UT-BE-01** | 已完成 Send Email serviceTask | 历史 `operationType=SEND`，`operator=system` |
| **UT-BE-02** | Activepieces serviceTask | **不**出现在流程历史中 |
| **UT-BE-03** | nested subprocess 内 Send Email | 仍识别为 SEND |
| **UT-BE-04** | BPMN model 不可用 | Send Email **不**进历史（已知限制） |

### 2.2 前端

```bash
cd frontend/user-portal
pnpm exec vitest run \
  src/utils/__tests__/sendTaskDiagramStatus.test.ts \
  src/composables/applicationDetail/__tests__/diagramParserSendTask.test.ts \
  src/composables/tasks/__tests__/bpmnDiagramParserSendTask.test.ts
```

| 用例 ID | 描述 | 期望 |
|---------|------|------|
| **UT-FE-01** | history 含 SEND / completed | sendTask `status=completed`，在 `completedNodeIds` |
| **UT-FE-02** | 仅 activityId 匹配 | 仍 completed |
| **UT-FE-03** | 未发信 | sendTask `pending`，不在 completed 列表 |
| **UT-FE-04** | 名称多空格归一化 | 仍 completed |

---

## 3. 接口验证（E2E 数据链）

在 **Send Email 已执行成功** 的申请上操作。

### 3.1 流程历史 API

```http
GET /api/portal/tasks/process/{processInstanceId}/history
```
（或任务详情页 Network 里同等 history 请求）

| 用例 ID | 检查项 | 期望 |
|---------|--------|------|
| **API-01** | 存在 Send Email 对应行 | `operationType` = `"SEND"` |
| **API-02** | activityId | 与设计器 sendTask `id` **完全一致** |
| **API-03** | operator | `operatorName` = `system` |
| **API-04** | activityType | `serviceTask`（引擎侧类型，正常） |

**失败含义：**

- 无 SEND 行 → 查 P0（引擎 history / BPMN delegate）
- 有 SEND 但 activityId ≠ 设计器 id → **P3**（版本不一致，本次不修）

### 3.2 引擎日志（可选）

```bash
docker compose ... logs --tail=200 workflow-engine | findstr /i "SMTP-SEND"
```

| 用例 ID | 期望 |
|---------|------|
| **LOG-01** | `[SMTP-SEND] SUCCESS` 在 SEND 历史时间点附近 |

---

## 4. Portal UI 手工 / 截图测试

### 4.1 图例对照

| 颜色 | 含义 |
|------|------|
| 绿 `#00A651` | 已完成（Send Email 发信成功后应为此色） |
| 橙 `#FF6600` | 当前步骤（Send Email 不应长期停在此色） |
| 灰 `#909399` | 待执行 |

### 4.2 用例清单

#### TC-01 主路径 — 发信后申请详情（**必测 / 截图**）

| 步骤 | 操作 |
|------|------|
| 1 | 发起含 Send Email 的流程，完成「提交」User Task |
| 2 | 等待流程自动经过 Send Email（无人工任务） |
| 3 | 打开 **申请详情** → 展开「流程图」 |
| 4 | 打开「流程历史」 |

| 检查项 | 期望 |
|--------|------|
| Send Email 节点边框/填充 | **绿色**（已完成） |
| 当前 User Task（审批） | **橙色**（进行中） |
| 未到达节点 | 灰色 |
| 流程历史 | 有一条 **Send / 发信**，操作人 system |
| Send Email 信封图标 | 描边偏绿（P2 视觉） |

**截图文件名建议：** `{date}_portal-send-task-diagram-app-{applicationId}.png`

---

#### TC-02 主路径 — 发信后任务详情（**必测 / 截图**）

| 步骤 | 操作 |
|------|------|
| 1 | 承接 TC-01 同一实例 |
| 2 | 审批人打开 **待办任务详情** |
| 3 | 查看流程图 + 流程历史 |

| 检查项 | 期望 |
|--------|------|
| Send Email | **绿色**（与 TC-01 一致） |
| 当前任务节点 | **橙色** |

**截图：** `{date}_portal-send-task-diagram-task-{taskId}.png`

---

#### TC-03 发信前 — Send Email 应为灰（回归）

| 步骤 | 操作 |
|------|------|
| 1 | 新建申请，仅完成 Start，**尚未**触发 Send Email |
| 2 | 打开申请详情流程图 |

| 期望 |
|------|
| Send Email = **灰色**；Submit 若已完成则绿 |

---

#### TC-04 Draft Return 方案 B（**必测 / 文档化**）

| 步骤 | 操作 |
|------|------|
| 1 | 流程已走过 Send Email 并到达后续 User Task |
| 2 | 执行 **Draft Return** 回到第一步 |
| 3 | 查看流程图 |

| 期望（方案 B，非 bug） |
|------------------------|
| 第一步 = **橙色** |
| Send Email 及下游 = **灰色**（即使邮件曾发出） |
| 流程历史仍保留此前 SEND 记录 |

**截图：** 注明「Draft Return 预期下游灰色」供 Code Review 区分。

---

#### TC-05 名称边界（可选）

| 步骤 | 操作 |
|------|------|
| 1 | 设计器将 Send Task 名称改为含多余空格（如 `Send  Notification`） |
| 2 | Deploy 后跑完 Send Email |
| 3 | 查看流程图 |

| 期望 |
|------|
| 仍绿色（P1 名称归一化） |

---

#### TC-06 P3 版本不一致（**已知限制，仅记录**）

| 步骤 | 操作 |
|------|------|
| 1 | 用旧版 Deploy 发起实例并完成 Send Email |
| 2 | 设计器 **修改 Send Task 的 id** 并重新 Deploy |
| 3 | 打开**旧实例**申请详情 |

| 期望（本次不修） |
|------------------|
| 可能 **灰色**；history 中 SEND 的 activityId 与新版 BPMN id 不同 |

**用途：** Code Review 说明 P3 边界，不作为本轮回归失败条件。

---

## 5. 自动化截图脚本

在 `frontend/` 目录：

```bash
pnpm exec playwright install chromium   # 首次

# 申请详情 + 可选任务详情
APPLICATION_ID=<你的申请ID> \
TASK_ID=<审批任务ID> \
node scripts/verify-send-task-diagram.mjs
```

输出目录（**勿删**）：

```text
frontend/user-portal/verification-screenshots/
  YYYY-MM-DD_portal-send-task-diagram-app-{id}.png
  YYYY-MM-DD_portal-send-task-history-app-{id}.png
  YYYY-MM-DD_portal-send-task-diagram-task-{id}.png
  YYYY-MM-DD_portal-send-task-history-task-{id}.png
```

或使用通用脚本截全页：

```bash
cd frontend
pnpm run verify:screenshot -- \
  --app portal \
  --url "http://localhost:3000/portal/applications/<APPLICATION_ID>" \
  --selector ".process-diagram" \
  --name send-email-diagram-app
```

---

## 6. Code Review 截图清单

PR / Review 请附至少以下 **4 项**：

| # | 内容 | 路径示例 |
|---|------|----------|
| 1 | 申请详情 — Send Email **绿色** | `.../verification-screenshots/*diagram-app*.png` |
| 2 | 申请详情 — 历史 **SEND** 行 | `.../verification-screenshots/*history-app*.png` |
| 3 | 任务详情 — 同上流程图 | `.../verification-screenshots/*diagram-task*.png` |
| 4 | TC-03 或 Network 截图 — API `operationType:SEND` | 浏览器 DevTools 或 Postman |

可选：

| # | 内容 |
|---|------|
| 5 | TC-04 Draft Return 下游灰色（标注预期） |
| 6 | `vitest` + `mvn test` 终端输出 |

---

## 7. 缺陷判定速查

| 现象 | 优先排查 |
|------|----------|
| 历史无 SEND | P0 / SMTP 未配 / Send Email 未 Deploy |
| 历史有 SEND，图仍灰，id 一致 | P1 / 前端未 rebuild |
| 历史有 SEND，id 与设计器不同 | P3（已知） |
| Draft Return 后 Send Email 变灰 | **方案 B 预期** |
| 只有信封灰、外框绿 | P2 视觉未部署；检查 `ProcessDiagram.vue` |
| 邮件未发但变绿 | **Bug** — 查 history 是否误标 completed |

---

## 8. 相关文件

| 层 | 文件 |
|----|------|
| 后端 | `TaskHistoryAssembler.java` |
| 前端解析 | `sendTaskDiagramStatus.ts`, `useApplicationDetailDiagramParser.ts`, `bpmnDiagramParser.ts` |
| 视觉 | `ProcessDiagram.vue` |
| 截图脚本 | `frontend/scripts/verify-send-task-diagram.mjs` |
