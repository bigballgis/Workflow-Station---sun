/**
 * MI 字段名的**统一入口** —— 一律来自每个 Function Unit 的 Sub-Task Config，不写死。
 *
 * <p><b>为什么存在。</b>MI 的列名由 developer-workstation 的 Sub-Task Config（BPMN extension
 * properties）逐 FU 配置：`miTaskStatusField` / `miTaskCurrentNodeField` / `assigneeField` /
 * `subTableName` 等。运行时代码里散落着 `'task_status'` / `'task_current_node'` / `'id_idw'`
 * 这类字面量，实测 20 个文件 45 处。
 *
 * <p>`resolveMiDashboardFieldNames(miFields)` 这类函数**早就支持传配置**（配置优先、字面量只是
 * 兜底），但全代码库**没有任何调用点真的传过 `miFields`** —— 于是 100% 的调用都落在默认值上。
 * 结果：状态列不叫 `task_status` 的 FU 静默失效，且**只在那个 FU 上复现**，这正是 MI 反复出问题
 * 的结构性根因。
 *
 * <p><b>为什么用模块级注册表而不是逐层传参。</b>实测有 113 个调用点分布在 23 个文件里，且多数
 * 是深层纯函数（merge / filter / scrub）。逐个加参数会产生巨大且易漏的 diff（漏一处就是一个
 * 静默失效的 FU）。这里改为：任务/申请详情在**解析完 BPMN 后**调用 `setActiveMiConfig()` 注册一次，
 * 深层函数通过 `getActiveMiFieldNames()` 隐式取到；显式传参的路径依然优先（见下）。
 *
 * <p><b>优先级</b>：显式传入的 `miFields` > 已注册的活动配置 > 平台默认字面量。
 * 显式传参永远赢，所以既有的、已经正确传值的调用点不会被注册表干扰。
 *
 * <p><b>生命周期</b>：单页应用里同一时刻只展示一个 task / application 详情，故用模块级单例；
 * 详情页进入时 `setActiveMiConfig`，离开/切换时 `clearActiveMiConfig()`，避免上一个 FU 的
 * 配置泄漏到下一个（跨 FU 串配置和写死一样糟）。
 */

import type { MiSubProcessScopeConfig } from './miSubProcessScopeBpmn'

/*
 * 2026-09-02：MI_DEFAULT_STATUS_FIELD / MI_DEFAULT_CURRENT_NODE_FIELD 已删除。
 *
 * 原按「平台契约」保留，依据是 2026-09-01 实测无一 FU 配置过这两项。该依据不成立：
 * developer-workstation 的 Sub-Task Config 下拉曾把 `task_status` / `task_current_node`
 * 作为固定选项**注入**，而子表真实列名可能完全不同（demo FU 50005 的 subtable 实为
 * `task_statuss` / `task_current_nodes`）——「平台确定写入的列名」其实是设计器造出来的假象。
 *
 * 后端写入/读取侧的兜底已同步删除（MultiInstanceTaskWriter / MiOverlaySupport /
 * MiOverlayComponent / SubTableEnrichmentComponent / MultiInstanceDataResolver）：
 * 没配置时后端根本不写这两列 —— 前端再兜底一个名字，只会去读一个永远不存在的键。
 * 列名一律来自 Sub-Task Config；解析不出就是 null，调用方按「没有这一列」处理。
 */

export interface MiFieldNames {
  /** Sub-Task Config `miTaskStatusField`；无配置时为 null（调用方不得猜列名）。 */
  statusField: string | null
  /** Sub-Task Config `miTaskCurrentNodeField`；无配置时为 null。 */
  currentNodeField: string | null
  /** Sub-Task Config `assigneeField`；无配置时为 null（调用方不得猜列名）。 */
  assigneeField: string | null
  /** Sub-Task Config `subTableName`（设计器表名）；无配置时为 null。 */
  subTableName: string | null
}

/** 当前详情页的活动 MI 配置；无 MI 子流程时为 null。 */
let activeMiConfig: MiSubProcessScopeConfig | null = null

/**
 * 注册当前 task / application 的 MI 配置。
 * 由 `useTaskDetailMiScope` / `useApplicationDetailMiScope` 在解析完 BPMN 后调用。
 */
export function setActiveMiConfig(scope: MiSubProcessScopeConfig | null): void {
  activeMiConfig = scope
  // 注销 MI 配置时一并清掉分类表 id：两者描述同一个 FU，留着上一个 FU 的 collection tableId
  // 会让下一个 FU 的 binding 被判成 participant-child（跨 FU 串配置，和写死一样糟）。
  // 注册表在 `registerMiKindTableIdsFromBindings` 里于 binding 构建完成后重新写入。
  if (scope == null) {
    activeMiKindTableIds = { miCollectionTableId: null, primaryTableId: null }
  }
}

/** 离开详情页时清除，避免配置泄漏到下一个 FU。 */
export function clearActiveMiConfig(): void {
  activeMiConfig = null
  activeMiKindTableIds = { miCollectionTableId: null, primaryTableId: null }
}

/** 当前活动的 MI 配置（只读）。 */
export function getActiveMiConfig(): MiSubProcessScopeConfig | null {
  return activeMiConfig
}

/**
 * Binding 分类所需的表 id（`{ miCollectionTableId, primaryTableId }`）。
 *
 * <p>与 {@link activeMiConfig} 同样是模块级注册表，理由也相同：分类谓词
 * （`isMiParticipantScopedSubTableBinding` 等）有 **39 个调用点**散布在 20+ 文件里，多数是深层
 * 纯函数。逐个加参数的 diff 巨大且漏一处就是一个静默失效的 FU —— 而"漏传就退回猜列名"正是
 * 本次要根除的东西。改为详情页解析完 BPMN + binding 后注册一次，谓词隐式读取。
 *
 * <p>显式传入的 ctx 永远优先（见 `resolveMiKindContext`），所以已经传参的调用点不受影响。
 */
let activeMiKindTableIds: { miCollectionTableId: number | null; primaryTableId: number | null } = {
  miCollectionTableId: null,
  primaryTableId: null,
}

/** 注册当前详情页的 collection / 主表 tableId。 */
export function setActiveMiKindTableIds(ids: {
  miCollectionTableId?: number | null
  primaryTableId?: number | null
} | null): void {
  activeMiKindTableIds = {
    miCollectionTableId: ids?.miCollectionTableId ?? null,
    primaryTableId: ids?.primaryTableId ?? null,
  }
}

/** 当前活动的分类表 id（只读）。 */
export function getActiveMiKindTableIds(): {
  miCollectionTableId: number | null
  primaryTableId: number | null
} {
  return activeMiKindTableIds
}

function trimOrNull(v: unknown): string | null {
  const s = String(v ?? '').trim()
  return s.length > 0 ? s : null
}

/**
 * 解析 MI 列名。
 *
 * @param explicit 调用方显式知道的配置（优先级最高）。省略时用已注册的活动配置。
 */
export function getActiveMiFieldNames(
  explicit?: { statusField?: string | null; currentNodeField?: string | null } | null,
): MiFieldNames {
  const cfg = activeMiConfig
  const status =
    trimOrNull(explicit?.statusField)
    ?? trimOrNull(cfg?.miTaskStatusField)
  const currentNode =
    trimOrNull(explicit?.currentNodeField)
    ?? trimOrNull(cfg?.miTaskCurrentNodeField)
  return {
    statusField: status,
    currentNodeField: currentNode,
    assigneeField: trimOrNull(cfg?.assigneeField),
    subTableName: trimOrNull(cfg?.subTableName),
  }
}

/**
 * 某个列名是否是「当前 FU 的 MI 状态列」。
 *
 * <p>不做 `endsWith('_task_status')` 之类的名字猜测 —— 猜名字正是写死的变体。
 */
export function isMiStatusField(field: unknown, explicit?: { statusField?: string | null } | null): boolean {
  const f = String(field ?? '').trim()
  if (!f) return false
  return f === getActiveMiFieldNames(explicit).statusField
}

/** 某个列名是否是「当前 FU 的 MI 当前节点列」。 */
export function isMiCurrentNodeField(
  field: unknown,
  explicit?: { currentNodeField?: string | null } | null,
): boolean {
  const f = String(field ?? '').trim()
  if (!f) return false
  return f === getActiveMiFieldNames(explicit).currentNodeField
}

/**
 * MI 配置缺失时抛出的错误。**不要**在 catch 里退回某个字面量列名 —— 那正是静默失效的来源。
 */
export class MiConfigMissingError extends Error {
  constructor(readonly configKey: string, detail?: string) {
    super(
      `MI_CONFIG_MISSING: Sub-Task Config 缺少 ${configKey}`
      + `（在 developer-workstation 的 Process Design → Sub-Task Config 配置）`
      + (detail ? ` — ${detail}` : ''),
    )
    this.name = 'MiConfigMissingError'
  }
}

/**
 * 子表名 —— **每个 FU 各不相同，只能由配置回答**，解析不出必须报错。
 *
 * <p>与状态列名不同：状态列有平台确定的写入名，而子表名无从推断，猜一个只会去操作错误的表。
 * 引擎侧同样是硬失败（`MultiInstanceDataResolver`：
 * `throw new WorkflowValidationException("Task is missing multi-instance configuration information")`）。
 *
 * @throws MiConfigMissingError 未注册配置或 `subTableName` 为空
 */
export function requireMiSubTableName(): string {
  const name = trimOrNull(activeMiConfig?.subTableName)
  if (!name) {
    throw new MiConfigMissingError('subTableName', 'MI 子表名无法推断，不能猜表名')
  }
  return name
}

/**
 * 分派字段 —— 同样是 FU 特有配置，解析不出必须报错。
 *
 * @throws MiConfigMissingError 未注册配置或 `assigneeField` 为空
 */
export function requireMiAssigneeField(): string {
  const field = trimOrNull(activeMiConfig?.assigneeField)
  if (!field) {
    throw new MiConfigMissingError('assigneeField', 'MI 分派字段无法推断，不能猜列名')
  }
  return field
}

/**
 * 行主键 —— 来自 binding 的 `primaryKeyFields`（`dw_field_definitions`），解析不出必须报错。
 *
 * <p>实测各 FU 主键各不相同（ATM_Transaction 是 `row_id`、subtable 是 `id_idwvvbz`），
 * 猜 `id_idw` 会匹配到错误的行或匹配不到自己的行 —— 后端 `MiSubTaskSubTableRowMerger`
 * 同样在解析不出行主键时抛 `MI_ROW_KEY_UNRESOLVED` 而不是兜底。
 *
 * @throws MiConfigMissingError binding 未携带设计器主键
 */
export function requireSubTablePrimaryKeyFields(
  binding: { primaryKeyFields?: string[] | null; tableName?: string | null } | null | undefined,
): string[] {
  const pk = (binding?.primaryKeyFields ?? []).map(f => String(f ?? '').trim()).filter(Boolean)
  if (pk.length === 0) {
    throw new MiConfigMissingError(
      'primaryKeyFields',
      `子任务表 ${binding?.tableName ?? '(unknown)'} 未携带设计器主键，无法拆分/定位子任务行`,
    )
  }
  return pk
}

/** 一个 binding 上足以判断「它是不是当前 FU 的 MI 子任务表」的字段。 */
export interface MiSubTaskBindingLike {
  tableName?: string | null
  physicalTableName?: string | null
  primaryKeyFields?: string[] | null
}

function compactName(v: unknown): string {
  return String(v ?? '').trim().toLowerCase().replace(/[\s_-]+/g, '')
}

/**
 * 这个 binding 是不是**当前 FU 的 MI 子任务表**（Sub-Task Config 的 `subTableName` 指向的那张）。
 *
 * <p>只有这张表**一定要有主键** —— MI 要按行拆分子任务，没有行主键就无法拆、也无法定位
 * "我这一行"。其余表（关联表、共享附件、普通子表）**主键是可选的**：没有主键往往只是
 * 这张表不需要主键，不是配置错误。
 */
export function isMiSubTaskCollectionBinding(binding: MiSubTaskBindingLike | null | undefined): boolean {
  const want = compactName(activeMiConfig?.subTableName)
  if (!want || !binding) return false
  return [binding.physicalTableName, binding.tableName]
    .filter(Boolean)
    .some(n => compactName(n) === want)
}

/**
 * 行主键的**统一解析入口**，按「这是哪种表」决定失败方式：
 *
 * <ul>
 *   <li><b>MI 子任务表</b>（`subTableName` 指向的那张）—— 主键是**必需**的：要拆分子任务、
 *       要定位当前参与者的行。缺失即配置错误，**抛 `MI_CONFIG_MISSING`**，不猜列名。</li>
 *   <li><b>其它任何表</b> —— 主键**可选**：关联表（`rt_*`）、平台虚拟表 `sys_users`、
 *       共享附件（`main_id`）本来就可能没有主键。返回 `null`，调用方跳过与主键相关的判定。</li>
 * </ul>
 *
 * <p>踩过的坑：此前按**调用点**决定抛不抛，而行匹配会逐个遍历同一表单上的 peer binding
 * （实测含 relation table `test`、虚拟表 `sys_users`，两者 `primaryKeyFields` 恒为 null），
 * 于是用户**明明配了**子任务主键（`id_idwnn`），点 Save 仍被关联表触发的
 * `MI_CONFIG_MISSING` 打断。判据必须是**表的种类**，不是调用位置。
 *
 * @returns 主键列名数组；非子任务表且未配置主键时返回 `null`
 * @throws MiConfigMissingError 子任务表缺少主键
 */
export function resolveSubTablePrimaryKeyFields(
  binding: MiSubTaskBindingLike | null | undefined,
): string[] | null {
  const pk = (binding?.primaryKeyFields ?? []).map(f => String(f ?? '').trim()).filter(Boolean)
  if (pk.length > 0) return pk
  // 先按配置判断它是不是子任务表 —— 是就必须有主键，不是就允许没有。
  if (isMiSubTaskCollectionBinding(binding)) {
    return requireSubTablePrimaryKeyFields(binding)
  }
  return null
}
