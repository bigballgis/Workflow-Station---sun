# Feature Design Plan — Reference

本文件为 skill `feature-design-plan` 的扩展清单；主流程见 [SKILL.md](SKILL.md)。

---

## 规则触发矩阵

设计 Plan 阶段按**拟变更路径**加载对照项（只读审查，不复制全文）。

| 若 Plan 涉及 | 必须对照 |
|--------------|----------|
| 任意后端代码 | `code-quality-standards` · `backend-architecture` · `cross-cutting` · `ai-guardrails` |
| 新表 / 改字段 / init-scripts | `init-scripts-append-only` · `domain-model` · `json-row-storage-no-physical-tables`（业务表） |
| deploy / 环境变量 / Docker | `deployment-infra` · `docker-k8s-config-sync`（若改 K8s/compose） |
| user-portal 表单 / 子表 | `portal-design-parity` · `vue-frontend` · `i18n-rules` |
| MI / My Request 子表 | `portal-mi-subtable-my-request` · `performance-change-safety` |
| FK / PK / 表单脚本 | `form-preview-fk-pk-runtime` |
| FU 导出导入 / 版本 | skill `function-unit-portability` · `function-unit-version-rollback` |
| Main Table View 权限 | skill `view-access-control` |
| 鉴权 / 外部输入 / URL | `security-guard` · skill `secure-coding-sast` |
| 异常 / 降级 / 空值 | `error-handling-governance` |
| 性能 / 缓存 / 热路径 | `performance-guardrails` |
| 可见 UI | skill `verify-ui-fix-with-screenshot` · `frontend-screenshot-verification` |
| 弹窗表单布局 | skill `portal-dialog-form-labels` |
| Portal/Admin 新列表菜单 | `shared-list-portal-admin` + `docs/design/shared-list-components.md` §6.7 |

---

## 模块最低验证（写入 Plan 的【验证】）

| 模块 | 典型命令 |
|------|----------|
| dw 表单 / Preview | `cd frontend/developer-workstation && npm run build`；UI → verify:screenshot |
| portal 非 MI | portal build + vitest + 截图 |
| portal MI 热路径 | `cd frontend && npm run regression:mi`（完整，非 unit-only） |
| backend 单服务 | `mvn -pl backend/<m> -am package -DskipTests` + compose rebuild + logs |
| platform-common | 重建所有依赖 jar 的服务 |
| perf 专项 | 与 fix 分 PR；声明只动取数层 |

详表见 [docs/ai-rules/ai-development-playbook.md §5](../../../docs/ai-rules/ai-development-playbook.md)。

---

## Plan 质量自检（输出前）

- [ ] 【非目标】已写，且与【目标】不冲突
- [ ] 至少评估 2 个方案或说明为何唯一
- [ ] 影响面覆盖 DTO ↔ 前端 types ↔ SQL（若适用）
- [ ] 验收含反例 + 正例
- [ ] platform-common / shared.ts 等高风险路径已标注允许或禁止
- [ ] 未在设计阶段承诺「顺便重构」大文件
- [ ] 【待确认】仅保留真正阻塞项（≤2 个优先 AskQuestion）

---

## 与 playbook 任务整理的字段映射

| 设计 Plan | playbook 任务整理 |
|-----------|-------------------|
| 【模块】 | 【模块】 |
| 【验收】 | 【验收】 |
| Plan 中「允许改的路径」 | 【范围】允许 |
| 【非目标】+ 风险项 | 【范围】禁止 |
| 【验证】 | 【验证】 |
| fix/feat/perf/refactor | 【类型】（由 Plan 推断或用户指定） |

---

## 示例

### 示例 1 — 新 API + Portal 列表列

**背景：** Admin 要在用户列表展示「最后登录 BU」。

**方案 A（推荐）：** admin-center 列表 API 扩展 DTO，JOIN 查询批量化。  
**方案 B：** 前端每行调详情 API — 拒：N+1。

**非目标：** 不改 LDAP 同步；不改 portal。

**影响面：** `UserListResponse`、Mapper、admin 前端 types、`UsersView.vue`、i18n。

**验证：** `mvn -pl backend/admin-center -am package -DskipTests` + admin build。

---

### 示例 2 — MI 子表行为变更

**背景：** My Request 发起人视图 Sub Task 行数与 To Do 不一致。

**必须先读：** `portal-mi-subtable-my-request` — 发起人全案 vs 办理人切片不可混用。

**非目标：** 不改 BPMN；不改 backend 持久化。

**验证：** 必须写 `npm run regression:mi` + invariant I1–I7 勾选说明。

---

### 示例 3 — Schema 新字段

**背景：** `up_notification` 增加 `read_at`。

**影响面：** init-scripts append-only、`Notification` entity、DTO、portal 消息中心、Kafka 消费者（若写路径）。

**风险：** 存量行 NULL；兼容「只增字段」。

**验证：** SQL 幂等脚本 review + user-portal package + compose rebuild。
