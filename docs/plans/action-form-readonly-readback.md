# Action Form（FORM_POPUP ACTION 表绑定）只读回显到 Task Detail 主表单

状态：已确认，待实施
关联 FU：Multi-Instance Subtask Demo（`fu-20260422-23tfag`）

## 背景 / 问题

Task Detail 页面上的 "Add Remark"（FORM_POPUP action，绑定 ACTION 类型表 `meeting_remark`）能正常弹窗填写并提交，
数据也真实落库（`ActionFormPopupSubmitComponent` 写入物理表，按 `foreignKeyField`/`main_id` 关联当前 request）。
但 Task Detail 页面没有任何地方能看到已提交的 remark 内容——用户点开任务详情，看不到历史备注列表。

## Action Form 本质澄清

- ACTION 表在语义上是「操作留痕」（谁在任务处理过程中通过这个弹窗动作提交了什么），不是像 SUB 表那样「这条请求
  业务数据的一部分」——它绕开流程变量、绕开主表单，直接落自己的物理表。见
  [`ActionFormPopupSubmitComponent.java`](../../backend/user-portal/src/main/java/com/portal/component/ActionFormPopupSubmitComponent.java)
  类注释："This does not touch process variables at all."
- DW 端实现上，ACTION 绑定从一开始就和 SUB 绑定结构对等（同一个画布 Tab 模式：Form Design + List View 子页），
  见 [`FormDesigner.vue:315-317`](../../frontend/developer-workstation/src/components/designer/FormDesigner.vue#L315-L317)
  注释 "structurally identical to a sub-table"。
- 展示形式复用子表拖拽机制（方案 A：可选拖入主画布），但必须和 SUB 表在两个维度上做区分：
  1. **不需要 List View 设计页**——ACTION 表的只读列直接复用它自己 Form Design 页已定义的字段，不需要二次设计。
  2. **不需要 allowAdd/allowEdit/allowDelete 三个开关**——ACTION 挂到主画布后应该天生只读，不是三个开关默认关掉。

## 目标

1. DW 画布 Sub-Table 组件的绑定选择器能选中 ACTION 绑定（当前被硬过滤）。
2. 选中 ACTION 绑定后：
   - 属性面板不展示 List View Tab。
   - 属性面板不展示 allowAdd/allowEdit/allowDelete 三个开关；运行时恒只读，不依赖这三个 prop 的值。
   - 列直接取自该 ACTION 绑定自己 Form Design 页设计的字段。
3. Task Detail 页面渲染该只读表格，展示当前请求（requestId）已提交的历史记录，数据来自独立的、按 taskId 精确
   查询的新接口——**不经过 `fuContentCache`**（见【关键风险】）。

## 非目标（本阶段不做）

- 不支持在主表单里编辑/删除已挂载的 ACTION 行（只读，无例外）。
- 不改动 FORM_POPUP 提交逻辑本身。
- 不做 Applicant / My Request 页面的对称改动（后续分期，走的是不同 loader）。
- 不引入 ACTION 表参与 `__subTables__` / 流程变量的语义。
- List View 相关的列排序 / 自定义展示能力，本阶段不提供任何等价物。
- 不改组件显示名字 / 文案（"Sub-Table" 标签维持不变，用户明确要求本轮先实现业务逻辑）。

## 模块

dw（developer-workstation 前端）、portal（user-portal 前端 + 后端新接口）

## 方案（方案 A，已确认采用）

### DW 端（3 处）

1. [`SubTableBindingSelect.vue:35`](../../frontend/developer-workstation/src/components/designer/SubTableBindingSelect.vue#L35)
   过滤条件从 `b.bindingType === 'SUB'` 放开为 `b.bindingType === 'SUB' || b.bindingType === 'ACTION'`。
2. [`FormDesigner.vue:330-334`](../../frontend/developer-workstation/src/components/designer/FormDesigner.vue#L330-L334)
   List View Tab 的 `v-if` 从 `binding.subMode !== 'FORM_ONLY'` 改为
   `binding.subMode !== 'FORM_ONLY' && binding.bindingType !== 'ACTION'`（ACTION 恒不显示该 Tab）。
3. [`FormDesigner.vue:1918-1942`](../../frontend/developer-workstation/src/components/designer/FormDesigner.vue#L1918-L1942)
   的 `componentRule.subTable.rule()`：按 `activeRule.props?._bindingId` 反查 `designerSubBindings` 得到
   `bindingType`；`ACTION` 分支不注入 `allowAdd`/`allowEdit`/`allowDelete` 三个开关（`showFormBelowTable`/
   `compactCells` 展示类开关是否保留，见【待确认】，默认保留）。
   `_bindingId` 已经挂在 `activeRule.props._bindingId`
   （[`formDesigner.ts:271-291`](../../frontend/developer-workstation/src/utils/formDesigner.ts#L271-L291) 已维护）。
   绑定已被删除的历史脏数据兜底按非 ACTION 处理（不隐藏开关，保守）。

### Portal 后端（2 处）

1. [`TaskFormController.java`](../../backend/user-portal/src/main/java/com/portal/controller/TaskFormController.java)
   新增只读端点（如 `GET /{taskId}/action-table-rows`），一次性返回该任务所属请求下**所有已挂载 ACTION 绑定**
   的行数据（binding 级映射，避免 N+1，参考现有端点风格：`/{taskId}/form-data`、
   `/{taskId}/actions/{actionId}/form-popup-submit`）。
2. 新增组件（如 `ActionTableReadComponent`）：
   - 从 `taskId` 解出 `processInstanceId` / `requestId`（复用
     [`ActionFormPopupSubmitComponent.readRequestId`](../../backend/user-portal/src/main/java/com/portal/component/ActionFormPopupSubmitComponent.java#L212-L223) 同款逻辑）。
   - 按各 ACTION 绑定的 `foreignKeyField = requestId` 直接查物理表。
   - 复用 `assertSafeIdentifier` 白名单校验（表名 / 字段名，防注入，`secure-coding-sast` 技能相关）。
   - **此接口绝不经过 `fuContentCache`**（见【关键风险】），每次任务详情加载都精确查询当前请求的数据。

### Portal 前端（2 处）

1. [`useTaskDetailFuLoader.ts`](../../frontend/user-portal/src/composables/taskDetail/useTaskDetailFuLoader.ts)：
   在 `for (const b of tableBindings)` 循环前，并行发起新接口请求（参考已有 `lookupConfigsPromise` 的
   `await` 模式，[line 241](../../frontend/user-portal/src/composables/taskDetail/useTaskDetailFuLoader.ts#L241)），
   拿到结果后按 `bindingId` 回填对应 binding 的 `data`；ACTION 类型绑定跳过原有的 `__subTables__` 回填分支
   （[line 328-392](../../frontend/user-portal/src/composables/taskDetail/useTaskDetailFuLoader.ts#L328-L392)）。
2. `SubTableField.vue`（DW 预览
   [`developer-workstation`](../../frontend/developer-workstation/src/components/designer/SubTableField.vue#L436-L487)
   + Portal 运行时
   [`user-portal`](../../frontend/user-portal/src/components/SubTableField.vue#L593-L691) 两份）：
   ACTION 类型 binding 强制 `editable=false`，不依赖 `allow*` props 的值（两份必须同步改，否则出现「设计器只读、
   线上可编辑」的不一致）。

## 影响面

| 层级 | 变更 |
|------|------|
| DW 前端 | `SubTableBindingSelect.vue`、`FormDesigner.vue`（List View 条件 + 三开关注入逻辑）、`SubTableField.vue`（DW 预览态强制只读） |
| Portal 后端 | `TaskFormController.java`（新端点）、新增 `ActionTableReadComponent`（读物理表，taskId→requestId 解析，白名单校验） |
| Portal 前端 | `useTaskDetailFuLoader.ts`（并行拉取 + 回填）、`SubTableField.vue`（运行时强制只读） |
| DTO | 新增行数据响应结构 `{ bindingId, rows: [...] }[]`，对齐现有子表行数据契约 |
| 测试 | DW: `SubTableBindingSelect.test.ts` + `SubTableBindingSelect.bindingIdPanel.test.ts` 补 ACTION 用例、`subTablePermission.property.test.ts` 补 ACTION 用例；Portal 后端：新接口单测/集成测试（含跨 requestId 隔离用例）；Portal 前端：`useTaskDetailFuLoader` 相关测试补数据回填断言 |

## 数据与契约

- 不新增数据库字段，`foreignKeyField` 已在 `dw_form_table_bindings.foreign_key_field` 存在。
- 新接口响应对齐现有子表行数据结构，方便 `SubTableField` 直接复用渲染。
- 兼容：未挂载画布的 ACTION 表行为完全不变（继续纯写入、不查询、不展示）。

## 关键风险（已在方案设计中规避）

**缓存污染 / 跨请求数据泄漏**：`getFunctionUnitContent` 走 `fuContentCache`（按 `functionUnitId` 缓存，TTL 级别，
跨所有任务/请求实例共享，见
[`ProcessComponent.java:73,492-508`](../../backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java#L73)）。
**绝不能**把 ACTION 表行数据塞进这个响应——那样会把 A 请求提交的 remark 缓存后泄漏给同一 FU 的所有其他请求/任务。
必须走独立的、按 `taskId`/`requestId` 精确查询的新接口。Code review 时需重点检查这一点未被破坏。

其他风险：
- `_bindingId` 反查绑定类型需处理绑定已被删除的历史脏数据（兜底按非 ACTION 处理）。
- `SubTableField.vue` 两份必须同步改只读逻辑。
- 回滚：功能受「是否挂载 ACTION 绑定到主画布」配置状态开关控制，未使用的 FU 不受影响，可安全回滚。

## 验收

- 反例 1：现状——Add Remark 提交后 Task Detail 看不到记录。
- 反例 2：挂载 ACTION 绑定后，属性面板不出现 List View Tab、不出现三个权限开关。
- 反例 3：A 请求提交的 remark，不出现在同一 FU 下 B 请求（不同 requestId）的 Task Detail 页面——验证接口按
  requestId 精确隔离，不是共享缓存。
- 正例：挂载后 Task Detail 显示只读表格，展示当前请求的历史 remark，无任何编辑/删除入口。
- FU：Multi-Instance Subtask Demo（`fu-20260422-23tfag`），demo 账号 `developer` / `e2e_lina`。

## 分期

- **MVP**（本次实施）：DW 三处 + Portal 后端两处 + Portal 前端两处，在 Meeting Remark demo 验证，并造相应演示数据。
- **后续**：Applicant/My Request 对称支持；组件面板文案/命名调整；如需要再评估轻量列排序能力。

## 验证（实现后最低命令）

- DW 前端：`pnpm --filter developer-workstation build` + 上述补充的单测
- Portal 后端：`mvn -pl backend/user-portal -am package -DskipTests` + 新接口单测/集成测试
- Portal 前端：`pnpm --filter user-portal build` + 相关 vitest + `pnpm run regression:mi`
- `/verify-ui` 截图：DW 属性面板确认无 List View/无三开关 + Portal Task Detail 只读表格 + 两个不同 requestId 验证数据隔离

## 待确认（不阻塞实施，实施中按默认值处理，如有异议随时调整）

- `showFormBelowTable`/`compactCells` 两个展示类开关对 ACTION 默认保留（不影响可编辑性）。
- 新接口路由命名 `/{taskId}/action-table-rows` 为示意，实现时对齐现有命名风格即可。
