/**
 * `__subTables__` 单一真相源 — key 规则与读写入口。
 *
 * <p><b>为什么存在。</b>历史结构把同一张表的行按「每个 binding 一份 + 每个名字别名一份」
 * 存进 `__subTables__`：FU 50005 的 `subtable` 表被 6 张表单绑定，加上 `subtable` /
 * `Participants` / `participants` 三个别名，同一批行存了 9 份。不同写入路径各写一部分 key，
 * 副本随即分叉（实测 `50539` 有 2 行而 `50544` 只有 1 行），后端再逐 key 处理，
 * 用户的修改会被另一份旧副本盖掉。
 *
 * <p><b>规则：一张表一个 key。</b>binding 不是数据身份，只是「表单 ↔ 表」的连接；
 * 数据身份只能是表本身。
 *
 * <p><b>为什么用 name 而不是 id。</b>表 id 是自增主键，FU clone（`FunctionUnitCloner`
 * 的 `tableIdMapping`）、导入导出、部署到新环境都会重新映射，用 id 做 key 的历史数据
 * 会指向错误的表。表名在这些操作中保持不变，且唯一性由数据库唯一索引强制：
 * `uk_dw_table_name` / `ux_dw_table_name_lower`、`rt_table_definitions_table_name_key` /
 * `ux_rt_table_name_lower`（均含 `lower()` 版本，故本模块统一小写归一）。
 *
 * <p><b>为什么仍要前缀。</b>DW 表和 RT 表是两张独立的表、各自唯一，跨表没有联合约束，
 * 理论上可以同名（实测 id 已经撞过 2 个）。`dw:` / `rt:` 两个命名空间保证不会互相覆盖。
 *
 * <p>本模块只提供规则与纯函数，<b>不接线</b>；调用点在后续步骤逐一切换。
 */

/** 与数据库 `lower(table_name)` 唯一索引对齐的归一化。 */
export function normalizeStoreTableName(name: unknown): string {
  return String(name ?? '').trim().toLowerCase()
}

/** 一个 binding 上可用于定位「它绑的是哪张表」的字段。 */
export interface SubTableStoreBindingLike {
  /** DW 设计器表的真实表名（API 的 `tableName`，如 `subtable`）。 */
  tableName?: string | null
  /** 展示名（如 `Participants`）——**不可**用作 key，仅供 UI。 */
  tableDisplayName?: string | null
  /** 物理/设计器名的另一来源，部分接口用此字段。 */
  physicalTableName?: string | null
  /** RT 关联表名（含平台虚拟表 `sys_users`）。 */
  relationTableName?: string | null
  /** 仅用于判定命名空间：有 relationTableId 即为 RT binding。 */
  relationTableId?: number | string | null
  tableId?: number | string | null
}

export const DW_PREFIX = 'dw:'
export const RT_PREFIX = 'rt:'

/**
 * binding → `__subTables__` 的规范 key。
 *
 * <p>命名空间由「绑的是 DW 表还是 RT 表」决定：`dw_form_table_bindings` 实测
 * 70 条只有 `table_id`、31 条只有 `relation_table_id`，两者都有/都无的均为 0 条，
 * 所以判定无歧义。
 *
 * @returns `dw:<name>` / `rt:<name>`；无法解析出表名时返回 `null`，
 *          由调用方决定是报错还是跳过（本模块不猜）。
 */
export function subTableStoreKey(binding: SubTableStoreBindingLike | null | undefined): string | null {
  if (!binding) return null

  const isRelation = binding.relationTableId != null || !!binding.relationTableName
  if (isRelation) {
    const rtName = normalizeStoreTableName(binding.relationTableName ?? binding.tableName)
    return rtName ? `${RT_PREFIX}${rtName}` : null
  }

  // 注意取值顺序：physicalTableName / tableName 才是设计器表名；
  // tableDisplayName 是展示名（`Participants`），多个 FU 可能重复，绝不能做 key。
  const dwName = normalizeStoreTableName(binding.physicalTableName ?? binding.tableName)
  return dwName ? `${DW_PREFIX}${dwName}` : null
}

/** key 是否为本模块的规范格式（用于过渡期区分新旧 key）。 */
export function isCanonicalStoreKey(key: unknown): boolean {
  const k = String(key ?? '')
  return k.startsWith(DW_PREFIX) || k.startsWith(RT_PREFIX)
}

/**
 * 写入：只写规范 key，绝不再扇出别名。
 *
 * <p>历史实现会同时写 `bindingId` / `String(bindingId)` / `tableName` /
 * `normalizeSubTableName(tableName)` 四个 key（全代码库 14 处），任何一处漏写就产生分叉。
 */
export function writeSubTableRows(
  store: Record<string, unknown>,
  binding: SubTableStoreBindingLike,
  rows: unknown[],
): boolean {
  const key = subTableStoreKey(binding)
  if (!key) return false
  store[key] = rows
  return true
}

/**
 * 读取：规范 key 命中即返回。
 *
 * <p>过渡期（步骤 4 之前）存量数据仍是旧 key，调用方需在未命中时回退到既有解析链；
 * 本函数只负责规范 key，不做任何名字兜底 —— 兜底正是旧结构产生分叉的原因之一。
 */
export function readSubTableRows(
  store: Record<string, unknown> | null | undefined,
  binding: SubTableStoreBindingLike,
): unknown[] | undefined {
  if (!store || typeof store !== 'object') return undefined
  const key = subTableStoreKey(binding)
  if (!key) return undefined
  const v = store[key]
  return Array.isArray(v) ? v : undefined
}

/**
 * 递归收集一个 store（含行内嵌套 `__subTables__`）里的全部规范 key。
 * 行内嵌套与顶层同构（link-form 子表，如 participant → People），改造须一并覆盖。
 */
export function collectCanonicalKeys(store: unknown, out = new Set<string>()): Set<string> {
  if (!store || typeof store !== 'object' || Array.isArray(store)) return out
  for (const [key, value] of Object.entries(store as Record<string, unknown>)) {
    if (isCanonicalStoreKey(key)) out.add(key)
    if (Array.isArray(value)) {
      for (const row of value) {
        if (row && typeof row === 'object') {
          collectCanonicalKeys((row as Record<string, unknown>).__subTables__, out)
        }
      }
    }
  }
  return out
}
