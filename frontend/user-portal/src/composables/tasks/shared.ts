/**
 * Shared utility functions for task detail composables.
 * Pure helpers — no reactive state, no Vue/API dependencies.
 */

export function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

export function subTableBindingMatches(
  target: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null },
  source: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null }
): boolean {
  const targetPhysicalName = normalizeSubTableName(target.physicalTableName)
  const sourcePhysicalName = normalizeSubTableName(source.physicalTableName)
  if (targetPhysicalName && sourcePhysicalName && targetPhysicalName === sourcePhysicalName) return true
  const targetName = normalizeSubTableName(target.tableName)
  const sourceName = normalizeSubTableName(source.tableName)
  const samePhysicalTable = target.tableId != null && source.tableId != null && Number(target.tableId) === Number(source.tableId)
  return target.bindingId === source.bindingId || samePhysicalTable || (!!targetName && targetName === sourceName)
}

export function cloneSubTableRows(rows: any[]): any[] {
  try {
    return JSON.parse(JSON.stringify(rows))
  } catch {
    return rows.map(row => ({ ...row }))
  }
}

export function cloneSubTableBindings<T extends Array<{ data: any[] }>>(bindings: T): T {
  return bindings.map(binding => ({
    ...binding,
    data: cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
  })) as T
}

const ROW_KEY_MERGE_SEP = '\u001f'

/**
 * Normalize PK / rowKey scalar values so merge maps treat `555` and `"555"` as the same row
 * (JSON.stringify would not; this caused duplicate MI rows in My Request when variables mixed types).
 */
function scalarForMergeKey(v: unknown): string | null {
  if (v == null) return null
  if (typeof v === 'number' && Number.isFinite(v)) return String(v)
  if (typeof v === 'bigint') return String(v)
  if (typeof v === 'string') {
    const t = v.trim()
    return t === '' ? null : t
  }
  if (typeof v === 'boolean') return v ? 'true' : 'false'
  try {
    return JSON.stringify(v)
  } catch {
    const s = String(v).trim()
    return s === '' ? null : s
  }
}

function canonicalRowKeyFromPayload(r: Record<string, unknown>): string | null {
  const rk = r.rowKey
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    const o = rk as Record<string, unknown>
    return Object.keys(o)
      .sort()
      .map(k => {
        const part = scalarForMergeKey(getRowValueIgnoreCase(o, k) ?? o[k])
        return part != null ? `${k}=${part}` : `${k}=${JSON.stringify(o[k])}`
      })
      .join(ROW_KEY_MERGE_SEP)
  }
  return null
}

/**
 * Same idea as {@code SubTableRowKeySupport.getRowValueIgnoreCase} — form rows use mixed key casing.
 */
function getRowValueIgnoreCase(row: Record<string, unknown>, key: string): unknown {
  if (!row || !key) return undefined
  if (Object.prototype.hasOwnProperty.call(row, key)) {
    const v = row[key]
    if (v != null && !(typeof v === 'string' && v.trim() === '')) return v
  }
  const lower = key.toLowerCase()
  for (const k of Object.keys(row)) {
    if (k.startsWith('__')) continue
    if (k.toLowerCase() === lower) {
      const v = row[k]
      if (v != null && !(typeof v === 'string' && v.trim() === '')) return v
    }
  }
  return undefined
}

/**
 * PK column value on row or nested {@code rowKey}. For a single-column PK, matches
 * {@code SubTableRowKeySupport.rowKeyFromVariableRow}: {@code rowId}, and the platform {@code id}/{@code id_idw} alias pair.
 */
function rowValueForPkFieldSingle(row: Record<string, unknown>, field: string, pkFieldCount: number): unknown {
  const f = String(field || '').trim()
  if (!f) return undefined

  let v = getRowValueIgnoreCase(row, f)
  if (v != null) return v

  const rk = row.rowKey
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    const rko = rk as Record<string, unknown>
    v = getRowValueIgnoreCase(rko, f)
    if (v != null) return v
  }

  if (pkFieldCount !== 1) return undefined

  v = getRowValueIgnoreCase(row, 'rowId')
  if (v != null) return v
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    v = getRowValueIgnoreCase(rk as Record<string, unknown>, 'rowId')
    if (v != null) return v
  }

  const fl = f.toLowerCase()
  if (fl === 'id') {
    v = getRowValueIgnoreCase(row, 'id_idw')
    if (v != null) return v
    if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
      v = getRowValueIgnoreCase(rk as Record<string, unknown>, 'id_idw')
    }
    if (v != null) return v
  } else if (fl === 'id_idw') {
    v = getRowValueIgnoreCase(row, 'id')
    if (v != null) return v
    if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
      v = getRowValueIgnoreCase(rk as Record<string, unknown>, 'id')
    }
    if (v != null) return v
  }

  return undefined
}

function compositePkMergeKey(row: Record<string, unknown>, pkFieldNames: string[]): string | null {
  const parts: string[] = []
  const n = pkFieldNames.length
  for (const f of pkFieldNames) {
    const part = scalarForMergeKey(rowValueForPkFieldSingle(row, f, n))
    if (part == null) return null
    parts.push(`${f}=${part}`)
  }
  return `__pk__${parts.join('\u001e')}`
}

/**
 * Merge sub-table rows for the same logical table. Later rows win on field conflicts.
 *
 * Key resolution order:
 * 1. Designer primary key columns (`pkFieldNames`) when present and all parts are resolvable
 *    (from top-level row and/or nested `rowKey`). This must beat raw `rowKey` so My Request
 *    merges snapshots that use only `id` with ones that attach a full `rowKey` map.
 * 2. Legacy `id` / `rowId` when no designer PK list — before bare `rowKey`, so mixed payloads dedupe.
 * 3. Flowable / platform `rowKey` object canonical string.
 * 4. Stable content fingerprint — when PK values are missing so rows are not dropped.
 */
export function mergeSubTableRowsByRowId(
  existing: any[] | undefined,
  incoming: any[],
  pkFieldNames?: string[] | null
): any[] {
  const byId = new Map<string, any>()
  let keyFallbackSeq = 0
  const pkCols =
    Array.isArray(pkFieldNames) && pkFieldNames.length > 0
      ? pkFieldNames.map(f => String(f).trim()).filter(Boolean)
      : null

  const contentKeyNoPk = (r: Record<string, unknown>): string => {
    try {
      const keys = Object.keys(r).filter(k => k !== '__subTables__' && !k.startsWith('__'))
      if (keys.length === 0) return '__empty__'
      return keys.map(k => `${k}:${JSON.stringify(r[k])}`).join('\u001f')
    } catch {
      return `__key_err__${keyFallbackSeq++}`
    }
  }

  const add = (r: any) => {
    if (!r || typeof r !== 'object') return
    const o = r as Record<string, unknown>
    let k: string | null = null

    if (pkCols?.length) {
      const pkKey = compositePkMergeKey(o, pkCols)
      if (pkKey != null) k = pkKey
    }

    if (k == null && !pkCols?.length) {
      const rawId = rowValueForPkFieldSingle(o, 'id', 1)
      const idPart = scalarForMergeKey(rawId)
      if (idPart != null) k = idPart
    }

    if (k == null) {
      const canon = canonicalRowKeyFromPayload(o)
      if (canon != null && canon !== '') {
        k = `__rowKey__${canon}`
      }
    }

    if (k == null) {
      k = `__noid__${contentKeyNoPk(o)}`
    }

    const cur = byId.get(k)
    byId.set(k, cur ? { ...cur, ...r } : { ...r })
  }
  for (const r of existing || []) add(r)
  for (const r of incoming || []) add(r)
  return Array.from(byId.values())
}

/**
 * Prefer API {@code primaryKeyFields} (admin-center / dw_field_definitions); if missing, infer from
 * designer sub-list columns marked {@code isPrimaryKey}, then subForm rules — never a fixed column name.
 */
export function resolveSubTablePrimaryKeyFields(
  apiPrimaryKeys: string[] | undefined | null,
  bindingId: number | string | undefined | null,
  formConfig?: Record<string, any> | null
): string[] | undefined {
  const trimmed = (apiPrimaryKeys || [])
    .map(f => String(f).trim())
    .filter(Boolean)
  if (trimmed.length > 0) return trimmed
  if (bindingId == null || bindingId === '' || formConfig == null) return undefined

  const lv =
    formConfig.subListViews?.[bindingId] ?? formConfig.subListViews?.[String(bindingId)]
  const cols = lv?.columns
  if (Array.isArray(cols)) {
    const fromList = cols
      .filter(
        (c: any) =>
          c &&
          c.isPrimaryKey === true &&
          typeof c.fieldName === 'string' &&
          c.fieldName.trim().length > 0
      )
      .map((c: any) => String(c.fieldName).trim())
    if (fromList.length > 0) return fromList
  }

  const subForms = formConfig.subForms
  if (subForms && typeof subForms === 'object') {
    const sf = subForms[bindingId] ?? subForms[String(bindingId)]
    const rule = sf?.rule
    if (Array.isArray(rule)) {
      const fromRule = rule
        .filter(
          (r: any) =>
            r &&
            (r.isPrimaryKey === true || r.props?.isPrimaryKey === true) &&
            typeof r.field === 'string' &&
            r.field.trim().length > 0
        )
        .map((r: any) => String(r.field).trim())
      if (fromRule.length > 0) return fromRule
    }
  }

  return undefined
}

export function getSavedSubTableRows(subTables: Record<string, any>, binding: {
  bindingId: number
  tableName: string
  physicalTableName?: string
  tableId?: number | null
}): any[] | undefined {
  const key = String(binding.bindingId)
  const byId = (subTables[key] as any[] | undefined) || (subTables[String(binding.bindingId)] as any[] | undefined)
  if (Array.isArray(byId)) return byId as any[]
  if (binding.tableName && Array.isArray(subTables[binding.tableName])) return subTables[binding.tableName] as any[]
  return undefined
}
