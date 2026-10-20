# Flow History: nested boundary events

## 修复验证

- 复现：用户提供的已完成任务 `3109514b-cc50-11f1-89f2-0a31b3ca61b3` 及申请 `16efcba4-cc50-11f1-89f2-0a31b3ca61b3`，展开 Flow History，各有 8 条 Unknown Node、4 条 Handle assigned row。
- 根因：多实例子流程的 4 个开始事件和 4 个结束事件没有名称，API 的名称为 null。页面把这些自动事件当作业务任务展示，甚至显示了人工 Approve。
- 修复：ProcessHistory 从部署的 BPMN XML 读取节点父级；隐藏 subProcess/transaction 内部开始、结束事件。保留主流程边界和业务任务，但边界事件不显示人工操作、处理人。过滤仅发生在时间线组件，不改变原始记录、流程图状态或快照截断逻辑。
- 不根据具体 FU、节点 ID、名称或 Unknown Node 文案进行判断。没有 BPMN 时不猜节点层级，不误删记录。

## 红 → 绿

命令（frontend 目录，LOGIN_USER 从环境传入）：

```
VERIFY_STAGE=before node scripts/verify-flow-history-events.mjs
VERIFY_STAGE=after node scripts/verify-flow-history-events.mjs
```

- 同一申请和任务入口：修复前 Unknown Node 各 8；重新构建、重建 Portal 容器后各 0。
- 两入口均保留 4 条 Handle assigned row；主流程 End 保留，且没有 Action。
- 页面无 console error/pageerror；历史 API 前后深比较一致。脚本不提交任务或写业务数据。
- 新组件回归用例最初 4/4 失败（包含技术事件和虚假操作）；修复后通过，并增加命名、命名空间及 transaction 边界测试。

截图目录：`frontend/user-portal/verification-screenshots/`

- `2026-10-20_flow-history-before-application.png`
- `2026-10-20_flow-history-before-task.png`
- `2026-10-20_flow-history-after-application.png`
- `2026-10-20_flow-history-after-task.png`
- `2026-10-20_flow-history-after-application-end.png`
- `2026-10-20_flow-history-after-task-end.png`

## Code Review 结果

### 结论

CONDITIONAL：本缺陷的双入口部署后验证通过；工程级完整类型检查和 MI 全门禁不报 PASS。

### Findings / 逐条处置

- 本轮修复范围未发现未处置的产品 Blocker/Major/Minor。
- 审查检查了潜在的“隐藏技术事件导致流程图失去完成依据”：过滤放在 ProcessHistory 展示层；loader 的完整 historyRecords 保留，不引入该问题。
- 审查检查了“按空名称误删真实任务”：仅按 BPMN 中的嵌套边界 ID 过滤，未命名 userTask 保留；测试覆盖。
- 审查检查了“折叠数量仍包含被隐藏事件”：展开阈值、数量和切片全部改用过滤后的 businessRecords。
- 首轮检查及增量重审后无额外产品修改要求；结论仍为 CONDITIONAL，不把专项通过等同于完整平台通过。

### 范围 / 验收映射

- ProcessHistory.vue：统一双入口时间线展示规则。
- TaskHistorySection.vue、tasks/detail.vue、applications/detail.vue：传递现有部署 BPMN。
- ProcessHistory.events.test.ts：嵌套/根边界、真实任务、源数据不变、折叠、无 XML、命名空间、transaction。
- verify-flow-history-events.mjs：固定用户报告实例的只读复现和部署后验证。
- 不增加技术轨迹开关，不改后端、任务状态、设计器配置或历史存储。此次为已有历史展示纠错，不新增页面或可配置能力。

### 编译与静态检查

- Portal `pnpm run build`：PASS，Vite built in 24.66s。
- Docker `up -d --build --no-deps user-portal-frontend`：镜像重建、容器重建并启动。
- `git diff --check`：PASS（已有锁文件 CRLF 提示）。
- `vue-tsc --noEmit`：FAIL，仓库范围大量诊断，不能宣称类型检查全绿；不在本修复中扩大为全仓类型治理。

### 测试与运行证据

- `pnpm exec vitest run ProcessHistory.events completedTaskHistoryFilter historyActionSend batchAssignmentHistory --reporter=dot`：5 文件，22/22 通过。
- 加跑 `bpmnDiagramParserMiPending bpmnDiagramParserSendTask useBpmnParserPreviousForms` 后：8 文件，40/40 通过，验证图状态和历史表单消费者未回归。
- 过滤 `vue-tsc` 输出检查 ProcessHistory、TaskHistorySection 及新测试：没有对应诊断；整体类型检查仍失败，不能替代全仓门禁。
- 实际用户申请 / 已完成任务的 before/after 同路径验证：PASS。
- `pnpm run regression:mi`：单测 29 文件、234/234 通过；截图前两场景通过，第三场景 `verify-mi-attachment-rows.mjs` 失败：`no To Do or My Request with attachment rows`。环境缺少该样本，后续场景未执行，完整 MI 门禁不报 PASS。
- 未运行整个 Portal 全量单测；本次未 stash/reset 用户已有未提交工作。

### Function Unit 通用性与生命周期

- 通用性：静态 PASS；按 BPMN 结构和事件类型工作，无示例名判断。用户同一实例双入口运行通过；未为此纯展示修复新建、导入 FU 做双 FU 运行验证。
- Save/Load、Export/Import、Clone、Version/Rollback、Admin 两条 Import、Deploy/Activate：N/A，不新增或修改设计配置、API 契约及持久化数据。
- Portal runtime：共享展示组件，两入口均传部署 BPMN；原始历史与流程图消费不变。

### 安全与性能

- 增量源码、测试、脚本检查：未新增凭证、token 或业务个人信息；验证仅使用已有登录 helper，账号来自环境。
- 浏览器 DOMParser 只读解析 XML，不插入 DOM、不执行脚本，不新增 Java 服务端 XML 解析器或外部实体访问。
- 节点集合按 XML computed 缓存；不新增请求、持久化、全局缓存或订阅。不宣称性能提升。

### 未验证项 / 残余风险

- 全仓类型检查未通过；完整 MI 截图门禁受附件样本缺失阻塞；全量 Portal 单测及新建/导入 FU 双样本未执行，不作为合并级 PASS。上述范围由后续工程门禁治理处理，不扩展本次用户授权的历史展示修复。
- 无 BPMN 的旧页面不猜测节点归属，因此仍显示原始事件；边界事件不再错误归为人工操作。
- 范围外不在本轮修改。
