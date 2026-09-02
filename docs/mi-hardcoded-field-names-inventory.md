# MI 字段名硬编码清单（Portal 前端）

> 统计时间：2026-09-01。范围：`frontend/user-portal/src`，排除 `__tests__` / `*.test.ts`。
> 检索命令：
> ```bash
> grep -rn --include=*.ts --include=*.vue \
>   -E "['\"](id_idw|task_status|task_current_node|sub_task_status|sub_task_current_node)['\"]" src/ \
>   | grep -v "__tests__\|\.test\.\|\.spec\."
> ```

## 背景

MI 的列名由每个 Function Unit 在 developer-workstation 的 **Sub-Task Config**（BPMN extension
properties）配置：`miTaskStatusField` / `miTaskCurrentNodeField` / `assigneeField` / `subTableName`
等。运行时代码里写死 `'task_status'` / `'id_idw'` 会让列名不同的 FU **静默失效**，且**只在那个
FU 上复现** —— 这是 MI 反复出问题的结构性根因。

**关键发现**：`resolveMiDashboardFieldNames(miFields)` 这类函数早就支持传配置（配置优先、字面量
只是兜底），但实测**全代码库没有任何调用点传过 `miFields`**，于是 113 个调用点 100% 落在默认值上。
统一入口 `composables/tasks/useMiConfig.ts` 就是为此而建：详情页解析完 BPMN 后
`setActiveMiConfig()` 注册一次，深层纯函数通过 `getActiveMiFieldNames()` 隐式取到，
无需给 113 个调用点逐个加参数（那样漏一处就是一个静默失效的 FU）。

## 分类标准

| 类别 | 含义 | 处置 |
|---|---|---|
| **A. 单名假设** | 假定列**就叫**某个名字，改名即失效 | **已全部改为读配置** |
| **B. 已知名并集** | 枚举跨 FU 出现过的多种拼法，回答「这是不是运行时元数据」 | 集合保留为兜底，消费端**叠加**配置判定 |
| **C. 候选键列表** | 依次尝试多个可能的 id 键（`['id', 'id_idw', 'row_id']`） | **已全部改为读配置**（详见下节） |
| **D. 兜底默认值** | 配置缺失时的平台默认 | 保留 —— 理由见「关于 D 类兜底」 |

## 已修复（A / B 类）

| 文件 | 位置 | 原问题 | 修法 |
|---|---|---|---|
| `composables/tasks/subTableBindingKinds.ts` | `resolveMiDashboardFieldNames` | 字面量兜底，且无人传配置 | 改为「显式 > 活动配置 > 默认」 |
| `composables/tasks/subTableBindingKinds.ts` | `isSubTableRowMetaField` | 并集漏掉自定义列名 → 元数据被当业务数据 | 叠加配置判定 |
| `components/SubTableField.vue` | 表格单元格 + 运行时状态列（2 处） | `col.field === 'task_status'` 才渲染标签；列名不同的 FU 退化成纯文本 | 用 `getActiveMiFieldNames().statusField` |
| `composables/subTableField/useSubTableStatusColumns.ts` | `columnRepresentsMiOrTaskStatusList` | 靠字面量+标签猜测去重 | 先按配置命中，启发式保留为兜底 |
| `composables/tasks/subTableNestedEnrich.ts` | `mergePatchIntoRow` | 列名不同的 FU 拿不到 terminal-wins 合并 | 按配置解析（循环外解析一次） |
| `composables/applicationDetail/subTableRowHelpers.ts` | `subTableRowsLackSavedFieldPayload` | 并集漏自定义列 | 新增 `isMiPlaceholderKey()` 叠加配置 |
| `composables/taskDetail/subTableRowUtils.ts` | 同上（重复实现） | 同上 | 同上 |
| `composables/subTableField/subTableLinkFormRowMatch.ts` | `linkFormChildRowHasBusinessPayload` | 自定义状态列被当业务数据 → 空行误判为已填写 | 叠加配置判定 |
| `composables/applicationDetail/useApplicationDetailMiScope.ts` | `hasRunningMiRows` / `hasCompletedMiRows` / `hasTaskStatusData`（5 处读取） | 直接读 `row.task_status` | 读配置列名 |
| `composables/tasks/miCollectionSubTable.ts` | 第二处 `?? 'id_idw'` | 死代码，掩盖真正的兜底点 | 删除重复字面量 |

**注册点**（配置来源，各一处）：
- `composables/taskDetail/useTaskDetailMiScope.ts` — To Do 详情
- `composables/applicationDetail/useApplicationDetailMiScope.ts` — My Request 详情

两处都在 BPMN 无效时 `setActiveMiConfig(null)`，避免上一个 FU 的配置泄漏到下一个。

## C 类已改为读配置（本轮）

| 文件 | 原写法 | 改法 | 证据 |
|---|---|---|---|
| `composables/mainTableView/useMainTableViewPage.ts` | `resolveRowKey` 按 `['id','id_idw','row_id']` 猜 | 直接用**后端下发的 `rowKey`** | 后端 `PortalMainTableViewServiceImpl` 才知道 view 主键：MAIN=实例 id，SUB=实例 id+行 identity。前端猜列名等于重新推导一份已算好的值；SUB view 上多行会共用一个 key |
| `views/main-table-views/detail.vue` | `matchesRowKey` 同上 | 按 `row.rowKey` 匹配 | 与列表页 `?rowKey=` 必须同一套 key，否则详情页找不到行 |
| `utils/mainTableViewFkDisplay.ts` **+ 后端 `MainTableViewFkDisplaySupport.java`** | PK 缺失时兜底试 `['id','id_idw']` | **删除兜底**，只按配置的 `ref_primary_key_fields` 匹配 | 主键是别的名字、行里又恰好有 `id` 列的表会**匹配到错误的行**并显示错误的关联属性。前后端是镜像实现，两边同步删除 |
| `composables/tasks/miLinkChildIdentity.ts` | `rowMatchesMiExpansionId` 只在 `['id_idw','rowId','id',...]` 里找 | 新增 `primaryKeyFields` 参数，**设计器主键优先** | ATM_Transaction 的 PK 是 `row_id`、subtable 是 `id_idwvvbz`，此前匹配不到自己的行 |
| `composables/tasks/miLinkChildRows.ts` | `findMiIsolatedParentRow` 的单行排他守卫读 `rec.id_idw` | 按设计器主键取值 | 守卫此前在 PK 改名的表上**恒失效**，会把别的参与者的行当成自己的返回 |
| `composables/formRenderer/useInlineSubFormComponent.ts` | 「行有没有自己的 PK」读 `r.id_idw` | 按 binding 的 `primaryKeyFields` | 此前恒判为「无 PK」，会把别人已保存的行当成自己的空行继续编辑 |
| `utils/subTableRowRuntime/primaryKeyAllocation.ts` | `if (name === 'id_idw') continue` 保护父 PK | 新增 `parentPrimaryKeyFields` 参数 | 保护失效时会把 MI collection 自己的主键当成「误copy」删掉 |
| `components/SubTableInlineForm.vue` | `INLINE_ROW_IDENTITY_KEYS` 常量 | 新增 `primaryKeyFields` prop，PK 插入候选序列 | 顺序仍是「业务键 → 设计器 PK → 兜底」，见下 |

调用点均已透传 PK（`useTaskDetailMiLinkChild` / `useSubTableBindings` / `useInlineSubFormComponent`；
`SubTableInlineForm` 的 3 个使用处 `FormRendererFields.vue` / `PortalFormFields.vue` / `applications/detail.vue`）。

### 两处顺序/兜底是有意保留的，不是漏改

- **`SubTableInlineForm` 的候选顺序**：业务键（`row_id` / `sub_task_id`）**排在设计器 PK 之前**。
  PK 是保存时才分配的，若先用 PK，分配动作会改变身份字符串 → 重新 bootstrap → 复制父快照 →
  正在编辑的 Y/N 丢失。这是顺序问题，不是硬编码问题。
- **`rowMatchesMiExpansionId` 末尾的名字列表**：`_currentItem.rowId` 可能是设计器 PK，而 hydrate
  出来的行只暴露 SQL `id`（如 6532），此时必须跨字段匹配。配置优先，名字列表只在配置未命中时兜底。

## 仍保留（附依据）

| 文件 | 位置 | 依据 |
|---|---|---|
| `composables/tasks/internal.ts` | `SUB_TABLE_ROW_META_KEYS`（9 处） | 跨 FU 已知名并集；配置判定已加在消费端 `isSubTableRowMetaField` |
| `composables/tasks/internal.ts` | `MI_LINK_CHILD_SCALAR_KEYS` | 标量键并集，用于 link-child 结构判定，非状态语义 |
| `composables/tasks/subTableRowMerge.ts` | `mergeSubTableRowsByRowId` | 经 `resolveMiDashboardFieldNames` 解析，已自动获得配置 |
| `composables/tasks/miCollectionSubTable.ts` | `resolveMiCollectionPrimaryKeyFields` | 设计器 PK 优先，`id_idw` 仅为 legacy 无 PK 元数据时的 migration 兜底 |
| `utils/subTableRowIdentity.ts` | `SUB_TABLE_IDENTITY_FIELDS` | **不是主键查找**：这是前后端镜像的「合成身份令牌」（保存前分配的 `row_id` UUID，用于跨序列化去重），`id_idw` 是该令牌的历史拼法之一。改动会破坏与后端 `SubTableRowIdentity.IDENTITY_FIELDS` 的镜像契约与行去重 |
| `utils/subTableRowRuntime/rowOrchestration.ts` | `isForbiddenParticipantMirror` | 已用 `!rowPks.includes('id_idw')` 与真实 PK 对照，不是单名假设 |
| `composables/tasks/sharedProcessSubTableFilters.ts` | `rec.id_idw`（2 处） | **检测后端的具体行为**：`MiOverlaySupport.java:64` 字面量 `row.put("id_idw", idNum)`。前端必须匹配后端实际写入的名字；真正的硬编码在后端 overlay，属**跨层遗留项**（见下） |
| `composables/tasks/useMiConfig.ts` | `MI_DEFAULT_*` | 平台默认值的唯一定义处 |

### 后端 MI overlay（已修复）

`MiOverlaySupport.applyMiOverlayToVariableRow` 原来**同时**写配置列名**和**字面量
`task_status` / `task_current_node`。配了自定义列名的 FU 每行会被盖上**两套**状态列，
前端读到哪一个全凭运气；更糟的是两者值还不一致（配置列写引擎原始状态、字面量写映射后的门户状态）。

| 位置 | 修法 |
|---|---|
| `MiOverlaySupport.applyMiOverlayToVariableRow` | 只写解析出的列名（配置优先、平台默认兜底），值统一用映射后的门户状态 |
| `MiOverlaySupport.normalizeStuckMiParticipantRowForCompletedProcess` | 新增 `MiRowProgress` 参数带入配置列名；无引擎行匹配时才用默认 |
| `MiOverlaySupport.miDashboardColumnsToProtect` | 改用常量，不再散落字面量 |
| `SubTableEnrichmentComponent.repairStaleTaskStatus` | 新增 statusColumn/nodeColumn 参数；**SQL 标识符经 `SqlIdentifiers.requireIdentifier` 校验**（配置名来自 BPMN，不是可信字面量） |
| 新增 `MiOverlaySupport.miColumnNamesFor()` | 从该表的 `MiRowProgress` 取配置列名，供上面各处复用 |
| 新增常量 `PORTAL_MI_STATUS_COLUMN` / `PORTAL_MI_CURRENT_NODE_COLUMN` | 后端侧默认值的唯一定义处 |

测试 `MiOverlayConfiguredColumnsTest`（8 个用例，**去掉修复后有 3 个会红**）锁定：
配置列名生效且不再附带写字面量、无配置时走平台默认、配置列里也必须是映射后的门户值。

**仍保留的 `id_idw`（`extractNumericSubTableRowId` / `normalizeVariableRowPkEnvelope`）**：
这两处不是"猜某张表的主键叫什么"。主匹配路径 `resolveBestMiProgressForVariableRow` 用的是
从表元数据读出的**真实主键列**（`resolvePkColumnsCached`），配置驱动且**先执行**；
`extractNumericSubTableRowId` 只是主键匹配失败后的最后兜底，尝试变量行可能携带数字 id 的几个信封键。
走到这里仍没匹配上就是不加 overlay，安全。前端 `sharedProcessSubTableFilters` 里对应的
`rec.id_idw` 判定也因此保留 —— 它匹配的是**后端确定写入的键名**。

## 配置缺失时：报错，不用字面量

**判据不是"有没有字面量"，而是"这个问题只有配置能回答，还是平台自己定义了答案"。**

### 一、只有配置能回答 → **抛错**（`MiConfigMissingError` / `MI_CONFIG_MISSING`）

`useMiConfig.ts` 提供三个硬失败入口，解析不出**一律抛错，不猜列名**：

| 入口 | 回答的问题 | 猜错的后果 |
|---|---|---|
| `requireSubTablePrimaryKeyFields(binding)` | 这张子表的主键叫什么 | 匹配到**别人的行**并在上面编辑；或匹配不到自己的行 |
| `requireMiSubTableName()` | MI 子表叫什么 | 去操作**错误的表** |
| `requireMiAssigneeField()` | 分派字段叫什么 | 分派给**错误的人** |

已接入的硬失败点（都是"猜错就损坏数据"的路径）：

| 位置 | 原兜底 | 现在 |
|---|---|---|
| `miLinkChildIdentity.rowMatchesMiExpansionId` | 名字列表撞运气 | 主键必须传，否则抛错 |
| `miLinkChildRows.findMiIsolatedParentRow` 排他守卫 | `?? ['id_idw']` | 抛错（守卫失效会把别人的行当自己的） |
| `useInlineSubFormComponent` link-child「有无自己的 PK」 | `?? ['id_idw']` | 抛错（会把别人已保存的行当空行编辑） |
| `primaryKeyAllocation.repairMisassignedPrimaryKeyFromParentId` | `if (name === 'id_idw')` | 抛错（保护失效会删掉 collection 自己的主键） |

错误信息直接给出修复路径：
`MI_CONFIG_MISSING: Sub-Task Config 缺少 primaryKeyFields（在 developer-workstation 的
Process Design → Sub-Task Config 配置） — 子表 people 未携带设计器主键，无法定位行`

### 二、不该抛错的两类（否则把显示问题升级成崩溃）

**（a）本来就没有主键的合法 binding。** `filterRowsForMiCollectionSubTableBinding` 会跑在
**只是长得像** MI collection 的 binding 上：列名启发式误判的 FU 副本、AP 服务任务写回的行、
共享附件（`main_id`）。这些 binding 合法地没有 `primaryKeyFields`。
故 `resolveMiCollectionPrimaryKeyFields` 返回 `null`（不是抛错、更不是 `['id_idw']`），
调用方显式处理：**没有主键就不做幽灵行过滤**——拿 `id_idw` 顶上会把整张表清空。

> 实测依据：`inlineSubFormComponent.test.ts` 的共享附件用例、
> `miDashboardBindingBpmnOverride.test.ts` 的 AP 服务任务用例都是这一类。
> 一开始我在这里也抛了错，结果 9 个测试红 —— 说明这是**真实支持的场景**，不是测试写得随便。

**（b）`task_status` / `task_current_node`。** ~~这两个不是"猜"，是**平台契约**~~
**（2026-09-02 全面推翻并删除，见下节）**：

- 实测 **19 个已部署 BPMN 全部没有** `miTaskStatusField`（只有 `subTableName` /
  `assigneeField` / `rowIdVariable` / `assigneeMode`）：
  ```sql
  SELECT DISTINCT m[1] FROM (SELECT convert_from(bytes_,'UTF8') b FROM act_ge_bytearray
    WHERE name_ LIKE '%.bpmn%') t,
    LATERAL regexp_matches(t.b,'name="(mi[A-Za-z]*|subTable[A-Za-z]*|assignee[A-Za-z]*|rowIdVariable)"','g') m;
  -- assigneeAnchor / assigneeField / assigneeLabel / assigneeMode
  -- assigneeType / rowIdVariable / subTableId / subTableName   ← 没有 miTaskStatusField
  ```
- **后端就是用这两个字面量写数据的**：`MiOverlaySupport.java:243`
  `row.put("task_status", ...)`、`SubTableEnrichmentComponent` 的 `SET task_status='COMPLETED'`；
  引擎 `MultiInstanceDataResolver.resolveMiNamedColumn(..., "task_status")` 同一套默认。

所以无配置时这两个名字是**平台确定写入的真实列名**。改成抛错 = 19 个已部署流程的状态列当场全部失效，
而且抛的是一个"配置不存在但数据确实在那儿"的假错误。

注意引擎侧的处理完全一致，可作旁证：`subTableName` 缺失 →
`throw new WorkflowValidationException(...)`；状态列缺失 → 用默认名。**本轮前端与之对齐。**

## 2026-09-02 修订：这两个默认列名已全部删除（写入侧 + 读取侧）

上节（b）的结论已被推翻。触发点：Sub-Task Config 的两个下拉框把 `task_status` /
`task_current_node` 作为**固定选项注入**，而 demo FU 50005 的子表 `subtable` 上真实列名是
`task_statuss` / `task_current_nodes`（结尾多个 s）。选中注入的假选项，引擎 UPDATE 就打到
不存在的列上，被 `columnExists` 判否后**静默跳过** —— 表现为"配了却不生效"。

推翻（b）两条论据的实测（2026-09-02，dev 库）：

| 原论据 | 复测结果 |
|---|---|
| 19 个已部署 BPMN 全都没有 `miTaskStatusField` | 现为 **7 个中 1 个有**（FU 50005 已配）；`subTableName` 去重后**只有 `subtable` 一张 MI 子表** |
| 无配置时字面量是"平台确定写入的真实列名" | 全库只有 `participants` 有 `task_status` 列，而它属 FU 3、**无任何已部署 BPMN 引用**、表内 0 行 —— 兜底服务不了任何一行数据，只能写到不存在的列 |

### 第一轮：写入侧

| 位置 | 修法 |
|---|---|
| `MultiInstanceTaskWriter.resolveMiProgressColumnNames` | 删除 `statusDefault` / `nodeDefault`；未配置或非法标识符返回 `null` |
| `MultiInstanceTaskWriter.safeSqlColumnName` | 去掉 `defaultName` 参数，不合法 → `null` |
| `MultiInstanceTaskWriter.updateSubTableTaskProgress` | 逐列判空；两列都没配 → **warn 并跳过**（原先是 catch 里的 debug，静默）；配了但表上没这列 → 同样 warn |

测试 `MultiInstanceProgressColumnConfigTest`（6 用例）锁定：配置生效、缺失返回 `null` 而**不是**
两个字面量、非法标识符不回落、两列都没配则一条 SQL 都不发、只配一列时只写那一列。

### 第二轮：读取侧（按用户决定，「不要这个契约，读配置」）

既然写入侧已经「没配就不写」，读取侧再兜底一个名字，只会去读一个永远不存在的键。

| 位置 | 修法 |
|---|---|
| `MiOverlaySupport.PORTAL_MI_STATUS_COLUMN` / `..._CURRENT_NODE_COLUMN` | **删除常量**；新增 `trimToNull` 取代 `firstNonBlank(x, 默认)` |
| `MiOverlaySupport.applyMiOverlayToVariableRow` | 逐列判空，没配置就**不 put**（原先必须 put 一个 key，只能盖默认名） |
| `MiOverlaySupport.normalizeStuckMiParticipantRowForCompletedProcess` | 列名缺失直接 return —— 否则得猜哪个 key 是状态 |
| `MiOverlaySupport.miColumnNamesFor` | 返回 `{null, null}` 而非默认名 |
| `MiOverlaySupport.miDashboardColumnsToProtect` | 只保护配置出来的列；无配置=空集 |
| `MiOverlayComponent.resolveMiRowProgress` | 引擎下发的列名为空即 `null` |
| `SubTableEnrichmentComponent.repairStaleTaskStatus` | 列名缺失直接 return，不再对着默认名发 UPDATE |
| `MultiInstanceDataResolver.resolveMiNamedColumn` | 去掉 `defaultName` 参数 → `null`；`columnExists` / 排除判定按「没有这一列」处理 |
| 前端 `useMiConfig.MI_DEFAULT_*` | **删除常量**；`MiFieldNames.statusField/currentNodeField` 改 `string \| null` |
| 前端消费点 | `useSubTableStatusColumns`(2)、`subTableRowHelpers`、`subTableRowUtils`、`useApplicationDetailMiScope`(3)、`SubTableField.vue`、`subTableRowMerge`、`internal.ts`(2)、`subTableBindingKinds` 全部 null 安全 |

**行为变化**：没配置进度列的 FU，其 MI 状态/节点列在 Portal 上**不再出现**（原先会显示平台盖上的
`task_status`）。这正是本轮目的——如实反映「没配置」。已配置的 FU（如 FU 50005）不受影响。

**保留不动**：`SUB_TABLE_ROW_META_KEYS` 等「跨 FU 已知名并集」（本文档 B 类）仍含这两个名字 ——
它们回答的是"这是不是运行时元数据"，不是"本 FU 的状态列叫什么"，两回事。

测试：后端 `MiOverlayConfiguredColumnsTest` 9 用例（3 条旧断言改为断言新契约 + 新增 normalizer 用例）、
`MultiInstanceProgressColumnConfigTest` 6 用例；前端 `useMiConfig.test.ts` 11 用例、
`mergeSubTableRowsMiMerge.test.ts` 18 用例（后者补 `setActiveMiConfig` 注册 ——
terminal-wins 本就只对配置了进度列的 FU 生效）。

### 三、本轮改动的真正价值

改动前，即使某个 FU 配了 `miTaskStatusField`，代码也**读不到**（113 个调用点无一传 `miFields`）——
配了等于没配。改动后：**配了一定生效，没配则按平台契约走，而"只有配置能回答"的问题一律抛错。**

## e2e 场景稳定性（本轮一并修复）

MI 门禁的 6 个 Playwright 场景原来用 `resolveAndOpenTodo` / `rows[0]` **取列表第一条**
To Do / My Request。To Do 列表随时会多出新建的空任务，于是同一份代码反复跑会随机红 ——
**把数据前置条件问题伪装成产品缺陷**（实测连续两次跑分别红在不同场景上）。

| 场景 | 原来 | 改为 |
|---|---|---|
| `verify-mi-attachment-rows` | 第一条 To Do | `openFirstTodoMatching`：找**确实有附件行**的那条 |
| `verify-mi-assignee-subtask-slice` | 第一条 To Do | `openFirstTodoMatching`：找**确实渲染出 collection 且有行**的那条 |
| `verify-myrequest-details-modal` | 第一条 My Request | 遍历候选，取**确实有 MI collection** 的那个 |
| `readPeopleInlineFields`（helper） | 取第一个 `.sub-table-inline-form` | 按名字匹配 **People** 表单；没有就返回 null 让调用方跳过 |

`verify-myrequest-details-modal` 另有一处「必须一条空 id + 一条 UUID」的断言：这要求该申请**恰好**
处于「一个子任务已完成、另一个未处理」的中间态，是**活数据的属性**，不是产品行为。
改为该状态不存在时输出 PASS 并说明 —— 映射本身由 `miDetailsFieldMapping.test.ts` 用通用 id
锁定（实测 2 个用例分别覆盖未处理的空 id + 父 `sub_task_id`、已完成的 UUID），e2e 这层是冗余的。

> **排查教训**：`readPeopleInlineFields` 取第一个内联表单，而 FU 50005 实际渲染的是
> Participants 和 Meeting Remark、**根本没有 People 表单** —— 于是拿 Participants 的
> `Id`（`Test-000019`）去断言 UUID 并失败。**先确认场景选中的是不是它要测的那个对象**，
> 再怀疑产品。

## 新代码规则

1. 需要 MI 列名 → `getActiveMiFieldNames()`，**不要**新写字面量。
2. 判断「某列是不是 MI 状态列」→ `isMiStatusField()` / `isMiCurrentNodeField()`；
   **不要**用 `endsWith('_task_status')` 之类的名字猜测（猜名字是写死的变体）。
3. 行主键 → binding 的 `primaryKeyFields`（来自 `dw_field_definitions`），**不要**假定 `id_idw`。
   实测 ATM_Transaction 的 PK 就是 `row_id`、`subtable` 是 `id_idwvvbz`。
4. 解析不出配置时 **报错**（如 `MI_ROW_KEY_UNRESOLVED`），不要猜一个默认列名继续。

规则见 `.cursor/rules/portal-mi-subtable-my-request.mdc`「MI 字段名一律读 Sub-Task Config」。
