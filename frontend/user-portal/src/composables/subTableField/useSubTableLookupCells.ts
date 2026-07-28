import { nextTick, ref, watch, type Ref } from 'vue'
import {
  getLookupPrimaryKeyFieldFromProps,
  isUserSnapshotLikeObject,
  resolveDisplayValue,
  resolveLookupCellTagText,
  userObjectTagDisplayString,
  userSnapshotViewFieldsFromRow
} from '@/components/subTableAddDialogHelpers'
import { fetchLookupRowByPrimaryKey } from '@/components/lookup/fetchLookupRowByPrimaryKey'
import type { Column, SubTableFieldProps } from './subTableFieldTypes'

/** Return a reasonable minimum column width based on field type */
export function columnMinWidth(col: Column): number {
  if (col.minWidth) return col.minWidth
  switch (col.type) {
    case 'upload':       return 180
    case 'timerange':    return 200
    case 'datetime':     return 180
    case 'date':         return 130
    case 'tree':         return 180
    case 'checkbox':     return 160
    case 'treeselect':   return 160
    case 'colorPicker':  return 100
    case 'rate':         return 140
    case 'editor':       return 200
    case 'signature':    return 150
    case 'transfer':     return 180
    case 'cascader':     return 180
    case 'lookup':       return 260
    case 'slider':       return 160
    case 'password':     return 120
    default:             return 120
  }
}

/**
 * 关联表主键列名（**目标表**，不是宿主子表）。设计器显式配置优先，其余与
 * {@link getLookupPrimaryKeyFieldFromProps} / LookupField.pkField() 同一优先级：searchFields[0] → id。
 */
function lookupPrimaryKeyField(col: Column): string {
  const explicit = typeof col.props?.primaryKeyField === 'string' ? col.props.primaryKeyField.trim() : ''
  if (explicit) return explicit
  return getLookupPrimaryKeyFieldFromProps(col.props ?? null)
}

/** 主键标量的缓存键必须带主键列名：同一 tableId 的两列若主键列不同，否则会互相串行。 */
function scalarCacheKey(tableId: number, pkField: string, rawValue: string | number): string {
  return `${Number(tableId)}:${pkField}:${String(rawValue).trim()}`
}

export function lookupSelectedRow(col: Column, rawValue: unknown): Record<string, any> | null {
  if (rawValue == null || rawValue === '') return null
  if (typeof rawValue === 'object' && !Array.isArray(rawValue)) return rawValue as Record<string, any>
  return { [lookupPrimaryKeyField(col)]: rawValue }
}

/** 回填视图列（与 LookupField/designer 的 viewFields 同构）。 */
export interface LookupCellViewField {
  fieldName: string
  displayLabel?: string
  sortOrder?: number
  visible?: boolean
}

function lookupDisplayViewFields(col: Column): LookupCellViewField[] {
  const fields = col.props?.viewFields
  if (!Array.isArray(fields)) return []
  return [...fields]
    .filter((field: any) => field?.visible !== false)
    .sort((a: any, b: any) => (a?.sortOrder ?? 0) - (b?.sortOrder ?? 0))
    .map((field: any) => ({
      fieldName: String(field?.fieldName ?? ''),
      displayLabel: typeof field?.displayLabel === 'string' ? field.displayLabel : undefined,
      sortOrder: typeof field?.sortOrder === 'number' ? field.sortOrder : undefined,
      visible: field?.visible,
    }))
    // 无 fieldName 的条目取不到值，还会让 el-descriptions-item 的 :key 撞车
    .filter(field => field.fieldName !== '')
}

export function effectiveLookupViewFields(col: Column, rawValue: unknown): LookupCellViewField[] {
  const configured = lookupDisplayViewFields(col)
  if (configured.length > 0) return configured
  if (isUserSnapshotLikeObject(rawValue)) {
    return userSnapshotViewFieldsFromRow(rawValue).map(f => ({
      fieldName: f.key,
      displayLabel: f.label
    }))
  }
  return []
}

export function getSnapshotField(rowData: unknown, key: string): unknown {
  if (rowData == null || typeof rowData !== 'object' || Array.isArray(rowData)) return undefined
  return (rowData as Record<string, unknown>)[key]
}

/** Lookup cell display + scalar PK hydration for sub-table cells. */
export function useSubTableLookupCells(props: SubTableFieldProps, rows: Ref<any[]>) {
  /** 子表单元格：主键标量经 {@link fetchLookupRowByPrimaryKey} 解析后的行（缓存），供标签/回填使用。 */
  const lookupHydratedScalar = ref<Record<string, Record<string, any>>>({})

  function effectiveLookupRowForCell(col: Column, rawValue: unknown): Record<string, any> | null {
    const tid = col.props?.tableId
    if (tid != null && rawValue !== '' && (typeof rawValue === 'string' || typeof rawValue === 'number')) {
      const hit = lookupHydratedScalar.value[scalarCacheKey(Number(tid), lookupPrimaryKeyField(col), rawValue)]
      if (hit) return hit
    }
    return lookupSelectedRow(col, rawValue)
  }

  /**
   * 紧凑列表模式下默认不展开回填块；列上显式开启回填视图时仍渲染（与 FormRenderer 设计选项一致）。
   */
  function shouldShowLookupBackfill(col: Column): boolean {
    if (col.props?.showBackfillView === false) return false
    if (col.props?.showBackfillView === true) return true
    return !props.compactLookupCells
  }

  function lookupTagDisplayText(col: Column, rawValue: unknown): string {
    const eff = effectiveLookupRowForCell(col, rawValue)
    // Lookup columns must honor designer selectedDisplayField — never userObjectTagDisplayString (always id).
    if (col.type === 'lookup' && eff) {
      return resolveLookupCellTagText(col.props ?? null, eff)
    }
    if (rawValue != null && isUserSnapshotLikeObject(rawValue)) {
      return userObjectTagDisplayString(rawValue)
    }
    if (eff) {
      return resolveLookupCellTagText(col.props ?? null, eff)
    }
    return resolveDisplayValue(col, rawValue)
  }

  async function hydrateLookupScalarsInTable() {
    const tableRows = rows.value || []
    // Thunks, not live promises: the fetch must start inside the batch loop, otherwise every
    // row fires at once and BATCH throttling is dead code.
    const pending: Array<() => Promise<void>> = []
    const queued = new Set<string>()
    const resolvedScalars: Record<string, Record<string, any>> = {}
    for (const col of props.columns || []) {
      if (col.type !== 'lookup') continue
      const tableId = col.props?.tableId
      if (tableId == null || !Number.isFinite(Number(tableId))) continue
      // 关联表主键 —— 绝不能用 props.primaryKeyFields（那是宿主子表自己的主键列，
      // 拿去查关联表会让 eq 条件落到不存在的列上，行永远解析不出来）。
      const pk = lookupPrimaryKeyField(col)
      for (const row of tableRows) {
        const raw = row[col.field]
        if (raw == null || raw === '' || typeof raw === 'object') continue
        const ck = scalarCacheKey(Number(tableId), pk, raw)
        if (lookupHydratedScalar.value[ck] || queued.has(ck)) continue
        queued.add(ck)
        pending.push(() =>
          fetchLookupRowByPrimaryKey(Number(tableId), raw, {
            searchFields: (col.props?.searchFields as string[]) || [],
            displayField: (col.props?.displayField as string) || '',
            filterConditions: (col.props?.filterConditions as import('@/utils/lookupFilterConditions').LookupFilterCondition[]) || [],
            primaryKeyField: pk,
          }).then(loaded => {
            if (loaded) resolvedScalars[ck] = loaded
          }),
        )
      }
    }
    if (pending.length === 0) return
    const BATCH = 12
    for (let i = 0; i < pending.length; i += BATCH) {
      await Promise.all(pending.slice(i, i + BATCH).map(run => run()))
    }
    if (Object.keys(resolvedScalars).length > 0) {
      lookupHydratedScalar.value = { ...lookupHydratedScalar.value, ...resolvedScalars }
    }
  }

  let lookupHydrateSeq = 0
  watch(
    () => [rows.value, props.columns],
    () => {
      const seq = ++lookupHydrateSeq
      const run = () => {
        if (seq !== lookupHydrateSeq) return
        void hydrateLookupScalarsInTable()
      }
      nextTick(() => {
        if (typeof requestIdleCallback === 'function') {
          requestIdleCallback(run, { timeout: 2000 })
        } else {
          setTimeout(run, 80)
        }
      })
    },
    { immediate: true }
  )

  return { effectiveLookupRowForCell, shouldShowLookupBackfill, lookupTagDisplayText }
}
