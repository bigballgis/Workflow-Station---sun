# Process Debug Console MVP 规格（Developer Workstation）

## 背景与目标

当前 `Process Design` 已具备基础 debug 能力（路径模拟、变量查看、断点、节点表单预览），但对以下关键能力覆盖不足：

- Lookup 字段缺少真实查询调试（当前以预览 mock 为主）
- Gateway 缺少可解释分支判定信息（仅结果，不含命中原因）
- Action Button 缺少执行入口与结果追踪（仅配置，不可在 debug 中运行）

本规格定义一个最小可落地（MVP）的 `Debug Console` 扩展方案，在不推翻现有实现的前提下补齐上述能力。

---

## 范围（MVP）

### In Scope

- `Gateway Explain`：展示分支条件、命中结果、命中原因、默认分支
- `Lookup Live Probe`：在 debug 中发起 lookup 实时探测并查看回填效果
- `Action Button Runner`：在 debug 中执行 action 并查看执行日志与变量补丁
- 统一 debug 事件流（便于日志过滤与后续 runtime 对齐）

### Out of Scope（后续阶段）

- 与 workflow-engine 运行时 trace 的实时双向联动
- 跨服务分布式链路追踪与可观测平台集成
- 复杂权限模拟（多角色并发会话）

---

## 现状（As-Is）

### 前端现状

- 入口：`frontend/developer-workstation/src/components/debug/ProcessDebugPanel.vue`
- 节点表单：`frontend/developer-workstation/src/components/debug/ProcessDebugNodeForm.vue`
- 表单预览构建：`frontend/developer-workstation/src/utils/savedFormPreviewBuilder.ts`
- Lookup 预览：`frontend/developer-workstation/src/components/designer/LookupPreview.vue`

当前特征：

- `simulateProcess` 返回步骤后，前端执行 step-over/continue
- `nodeForm` 以 saved form preview 为主，Lookup 组件数据源是预览 mock 行
- actionIds 仅在节点属性配置侧可见，debug panel 无动作执行入口

### 后端现状

- 模拟入口：`POST /function-units/{functionUnitId}/process/simulate`
- 组件：`backend/developer-workstation/src/main/java/com/developer/component/impl/ProcessDesignComponentImpl.java`
- 模拟器：`backend/developer-workstation/src/main/java/com/developer/util/BpmnProcessSimulator.java`

当前特征：

- 轻量 BPMN 路径模拟（非 Flowable runtime）
- 网关条件求值为简化表达式判断，返回值不含详细判定过程
- 多实例子流程已支持上下文与模拟集合生成

---

## 目标架构（MVP）

在现有 Debug Panel 上新增两类能力：

1. **可解释性**：Gateway 的每条分支判定过程可见
2. **可执行性**：Lookup / Action 在 debug 会话内可真实触发并产生可观测结果

调试台保留“模拟器主导”的架构，不引入完整运行时引擎，避免爆炸半径扩大。

---

## API 契约（MVP）

## 1) 扩展 Process Simulate 返回结构

接口：

- `POST /function-units/{functionUnitId}/process/simulate`

在 `ApiResponse.data.steps[i]` 上新增可选字段 `gatewayEval`：

```json
{
  "success": true,
  "data": {
    "steps": [
      {
        "nodeId": "Gateway_1",
        "nodeType": "exclusiveGateway",
        "variables": {
          "amount": 1200
        },
        "gatewayEval": {
          "gatewayId": "Gateway_1",
          "gatewayType": "exclusiveGateway",
          "defaultFlowId": "Flow_3",
          "evaluations": [
            {
              "flowId": "Flow_1",
              "condition": "${amount > 1000}",
              "result": true,
              "reason": "amount=1200 > 1000"
            },
            {
              "flowId": "Flow_2",
              "condition": "${amount <= 1000}",
              "result": false,
              "reason": "amount=1200 <= 1000 is false"
            }
          ],
          "selectedFlowId": "Flow_1"
        }
      }
    ]
  }
}
```

### 字段说明

- `gatewayEval.evaluations[*].reason`: 人类可读判定理由（用于 explain）
- `gatewayEval.selectedFlowId`: 最终选中的 sequence flow
- `defaultFlowId`: BPMN `default` 属性对应分支

网关分支选择优先级（建议写入实现注释）：

1. 按表达式逐条评估候选分支；
2. 若无分支命中且存在 `defaultFlowId`，走默认分支；
3. 若无 `defaultFlowId`，才进入模拟器 fallback 策略（需在日志中标识）。

---

## 2) 新增 Lookup Probe 接口（Debug 专用）

接口建议：

- `POST /function-units/{functionUnitId}/process/debug/lookup/probe`

请求体建议：

```json
{
  "formId": 101,
  "bindingId": 12,
  "lookupConfig": {
    "searchFields": ["customer_name"],
    "displayFields": ["customer_name", "customer_code"],
    "filterConditions": [
      { "fieldName": "country", "value": "${country}" }
    ]
  },
  "keyword": "ACME",
  "runtimeVariables": {
    "country": "MY"
  },
  "page": 0,
  "size": 20,
  "sort": [
    "customer_name,asc"
  ],
  "searchMode": "contains"
}
```

返回体建议：

```json
{
  "success": true,
  "data": {
    "columns": [
      { "fieldName": "customer_name", "label": "Customer Name" },
      { "fieldName": "customer_code", "label": "Customer Code" }
    ],
    "rows": [
      { "customer_name": "ACME SDN", "customer_code": "C001" }
    ],
    "appliedFilters": [
      { "fieldName": "country", "value": "MY" }
    ],
    "page": 0,
    "size": 20,
    "total": 1
  }
}
```

---

## 3) 新增 Action Run 接口（Debug 专用）

接口建议：

- `POST /function-units/{functionUnitId}/process/debug/actions/run`

请求体建议：

```json
{
  "nodeId": "UserTask_1",
  "actionId": "46",
  "runtimeVariables": {
    "requestId": "R-001"
  },
  "formData": {
    "amount": 1200
  },
  "dryRun": true
}
```

返回体建议：

```json
{
  "success": true,
  "data": {
    "success": true,
    "actionResult": {
      "code": "OK",
      "message": "Action executed"
    },
    "variablePatches": {
      "approval_status": "APPROVED"
    },
    "logs": [
      "validate input",
      "invoke action handler",
      "build output patch"
    ],
    "durationMs": 38
  }
}
```

字段类型约束（与现有模型保持一致）：

- `actionId`: `string | number`
- `actionIds`: `Array<string | number>`

---

## 4) 错误响应规范（Debug API）

所有 Debug API 错误响应遵循统一 `ApiResponse` 结构：

```json
{
  "success": false,
  "error": {
    "code": "BIZ_DEBUG_LOOKUP_CONFIG_INVALID",
    "message": "lookupConfig is invalid for bindingId=12"
  }
}
```

### 建议错误码（MVP）

- `BIZ_DEBUG_LOOKUP_CONFIG_INVALID`：Lookup 配置缺失或非法
- `BIZ_DEBUG_ACTION_NOT_FOUND`：`actionId` 不存在或不属于当前 FunctionUnit
- `BIZ_DEBUG_ACTION_DRY_RUN_UNSUPPORTED`：该 action 暂不支持 dry run
- `BIZ_DEBUG_GATEWAY_EXPRESSION_UNSUPPORTED`：表达式超出模拟器支持范围
- `BIZ_DEBUG_PERMISSION_DENIED`：无调试权限（FunctionUnit 访问受限）

### 示例 A：Lookup Probe 参数错误

`POST /function-units/{functionUnitId}/process/debug/lookup/probe`

```json
{
  "success": false,
  "error": {
    "code": "BIZ_DEBUG_LOOKUP_CONFIG_INVALID",
    "message": "lookupConfig.filterConditions[0].fieldName is required"
  }
}
```

### 示例 B：Action Run 无权限

`POST /function-units/{functionUnitId}/process/debug/actions/run`

```json
{
  "success": false,
  "error": {
    "code": "BIZ_DEBUG_PERMISSION_DENIED",
    "message": "You do not have permission to debug this function unit"
  }
}
```

### 示例 C：Gateway 表达式不支持（simulate 可部分成功）

当流程可继续模拟但某条表达式不可解释时，建议保留 `success=true`，并在数据中标记：

```json
{
  "success": true,
  "data": {
    "steps": [
      {
        "nodeId": "Gateway_1",
        "gatewayEval": {
          "selectedFlowId": "Flow_3",
          "evaluations": [
            {
              "flowId": "Flow_1",
              "condition": "${complexExpr()}",
              "result": false,
              "reason": "UNSUPPORTED_EXPRESSION"
            }
          ]
        }
      }
    ],
    "warnings": [
      {
        "code": "BIZ_DEBUG_GATEWAY_EXPRESSION_UNSUPPORTED",
        "message": "Gateway_1 contains unsupported expression syntax"
      }
    ]
  }
}
```

---

## 5) 向后兼容策略（灰度发布）

为避免前后端灰度期出现调试面板不可用，前端需实现以下降级行为：

### 前端降级规则

- `gatewayEval` 缺失：
  - `Decision` Tab 显示“当前步骤无可解释网关数据”
  - 不阻断 `step over / continue`
- `variablePatches` 缺失：
  - `Actions` Tab 仅展示 `actionResult / logs`
  - 隐藏“应用变量补丁”按钮
- `lookup probe` 接口不可用（404/501）：
  - Node Form 中 `Probe` 按钮置灰并提示“当前环境未启用 Lookup Probe”
  - 保留原有 mock 预览能力
- `actionId` 返回 number 或 string：
  - 前端统一按字符串比较（展示保持原值）

### 后端兼容策略

- 扩展 `simulate` 字段时保持原字段不变（新增可选字段，不破坏旧解析）
- Debug 新接口初期可通过 feature flag 控制启用：
  - `debug.lookup.probe.enabled`
  - `debug.action.runner.enabled`

### 版本协同建议

- 第 1 阶段：后端先上线（新增字段/接口，前端不依赖）
- 第 2 阶段：前端开启新能力并保留降级逻辑
- 第 3 阶段：观测稳定后再收紧兜底提示与日志告警

---

## 前端改造方案（MVP）

### 1) `ProcessDebugPanel.vue`

新增 Tab：

- `Decision`：展示 `gatewayEval`
- `Actions`：展示当前节点可执行动作并触发 action run

保留现有 Tab：

- Variables / Logs / Node Form / Breakpoints

新增状态建议：

- `currentGatewayEval`
- `currentNodeActions`
- `actionRunResult`
- `lookupProbeState`

### 2) `ProcessDebugNodeForm.vue`

增强点：

- 对 lookup 字段增加 `Probe` 触发入口
- Probe 结果弹窗展示 rows 与 columns
- 支持“选择行后应用到 previewData（仅本次 debug 会话）”

### 3) `ExecutionLogViewer.vue`

支持按事件类型过滤：

- `NODE_ENTER`
- `GATEWAY_EVAL`
- `LOOKUP_PROBE`
- `ACTION_RUN`
- `VARIABLE_PATCH`

---

## 后端改造方案（MVP）

### 1) `BpmnProcessSimulator`

增强点：

- 在网关分支选择时记录每条候选分支的计算过程
- 将判定明细挂入当前 step 的 `gatewayEval`

### 2) 新增 Debug 组件（建议）

- `DebugLookupComponent`（编排 lookup probe 查询）
- `DebugActionComponent`（编排 action dry run）

保持分层：

- Controller -> Component -> Service/Repository

### 3) 安全与边界

- debug 接口必须遵循现有 function unit 访问控制
- 返回日志内容脱敏（不回传 token/secret/PII）
- `dryRun=true` 默认不落库，不触发不可逆副作用

---

## 统一事件模型（建议）

建议前后端统一 `DebugEvent` 结构：

```json
{
  "type": "GATEWAY_EVAL",
  "timestamp": "2026-05-27T14:00:00Z",
  "nodeId": "Gateway_1",
  "payload": {
    "selectedFlowId": "Flow_1"
  }
}
```

收益：

- 日志可筛选
- 可扩展到 runtime 对齐
- 降低 UI 逻辑分散

---

## 实施顺序与里程碑

## Phase 1（高收益，低改动）

- 扩展 `simulate` 返回 `gatewayEval`
- 前端新增 `Decision` tab 与可视化

**交付结果**：网关路径可解释

## Phase 2（中等改动）

- 新增 `debug/actions/run`
- 前端 `Actions` tab 支持执行与结果展示

**交付结果**：动作按钮可调试

## Phase 3（中等偏高改动）

- 新增 `debug/lookup/probe`
- 节点表单 lookup 支持 live probe 与回填预览

**交付结果**：lookup 可真实调试

---

## 验收标准（Definition of Done）

- 含 `exclusiveGateway` 的流程可展示分支条件、结果、命中原因、最终路径
- 含 lookup 字段的节点表单可执行 probe 并展示真实结果
- 含 `actionIds` 的节点可执行 action（dry run）并看到结果与变量补丁
- 执行日志支持按事件类型筛选
- 不影响现有 `step over / continue / breakpoint / variable monitor`

---

## 风险与应对

- **表达式兼容性风险**：网关表达式并不总是简单比较
  - 应对：MVP 覆盖当前已支持子集，不支持场景给出 `reason=UNSUPPORTED_EXPRESSION`
- **Action 副作用风险**：
  - 应对：默认 dry run，明确标注执行模式
- **Lookup 权限差异风险**：
  - 应对：沿用现有用户上下文与 function unit 权限校验

---

## 后续展望（非 MVP）

以下内容建议在 MVP 稳定后分阶段推进，避免一次性扩面导致调试台复杂度失控。

## Horizon A：Simulator 与 Runtime 双轨对齐

### 目标

将“设计时模拟结果”与“运行时真实执行结果”并排展示，快速定位两者偏差。

### 关键能力

- 双轨时间线：
  - 左轨：`simulate` 事件流
  - 右轨：runtime 事件流（任务创建、变量变更、网关命中）
- 偏差检测：
  - 节点路径偏差（模拟命中 A，运行时命中 B）
  - 变量偏差（关键变量值或类型不一致）
  - 动作副作用偏差（预期变量补丁未出现）
- 偏差解释：
  - 标注“表达式不支持/权限差异/外部系统回包差异”等原因类别

### 前置条件

- MVP 事件模型稳定（`DebugEvent` 类型不频繁变更）
- Runtime 侧提供可消费的 trace 查询接口或事件订阅

### 验收信号

- 可对同一流程实例展示双轨并高亮首个偏差点
- 支持导出偏差报告（JSON 或 Markdown）

## Horizon B：Portal 行为一致性自动校验

### 目标

把 debug console 从“人工比对工具”升级为“自动一致性检查器”，覆盖设计器与 user-portal 的关键行为对齐。

### 关键能力

- 场景模板化：
  - To Do 办理人视图
  - My Request 发起人视图
  - MI 子表（发起人全案 vs 办理人切片）
- 一致性断言：
  - 子表可见列、行数、Portal Display 模式
  - lookup 回填结果
  - action button 显示与可执行性
- 回归任务化：
  - 将校验场景纳入 CI 可执行脚本（失败即提示契约偏差）

### 前置条件

- 已沉淀稳定的场景样本数据（至少覆盖 2-3 个复杂 FunctionUnit）
- Portal 与 Designer 关键配置项有统一语义映射

### 验收信号

- 新增/修改表单或流程配置后，可一键跑一致性校验
- 产出可定位到 bindingId / nodeId / viewContext 的失败报告

## Horizon C：Gateway 决策回放与诊断

### 目标

提供可复盘的网关决策分析能力，提升复杂流程分支问题排查效率。

### 关键能力

- 决策快照：
  - 每次网关求值时记录输入变量快照（可配置白名单）
  - 记录表达式、求值结果、命中路径
- 决策树回放：
  - 按时间回放某次执行中所有网关判定链
  - 支持“如果变量 X 改为 Y，会走哪条分支”的假设演算
- 规则健康度：
  - 标记长期未命中分支（潜在死分支）
  - 标记高度重叠条件（可读性差、维护风险高）

### 前置条件

- 网关表达式解析能力从“简化子集”逐步扩展到平台常用表达式
- 日志脱敏与数据留存策略明确（避免敏感变量泄露）

### 验收信号

- 支持按实例 ID 回放完整网关判定链
- 支持输出“死分支/重叠条件”诊断摘要给流程设计者

## Horizon D：可观测与治理联动（可选）

### 目标

将 debug 能力从“开发态工具”延伸到“运行治理能力”，形成质量闭环。

### 关键能力

- 指标化：
  - 网关命中分布
  - action 成功率 / 平均耗时
  - lookup 查询失败率
- 告警化：
  - 某关键动作连续失败
  - 某分支命中率异常突变
- 治理联动：
  - 与网关治理文档、发布检查清单联动
  - 发布前自动出具流程调试健康报告

### 前置条件

- 统一指标埋点规范
- 环境间（dev/test/prod）指标口径一致

### 验收信号

- 发布前可自动生成并审阅“流程调试健康报告”
- 生产问题可通过指标快速定位到节点/动作/分支层级
