import { nextTick, ref, watch, type Ref } from 'vue'
import {
  getLookupSelectedDisplayField,
  isUserSnapshotLikeObject,
  parseLookupConfig,
  resolveDisplayValue,
  resolveLookupCellTagText,
  userObjectTagDisplayString,
  userSnapshotViewFieldsFromRow
} from '@/components/subTableAddDialogHelpers'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'
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

function getLookupPrimaryDisplayField(col: Column): string {
  return getLookupSelectedDisplayField(col as DialogColumn)
}

export function lookupSelectedRow(col: Column, rawValue: unknown): Record<string, any> | null {
  if (rawValue == null || rawValue === '') return null
  if (typeof rawValue === 'object' && !Array.isArray(rawValue)) return rawValue as Record<string, any>
  const cfg = parseLookupConfig(col.props?.lookupConfig)
  const pk = String(col.props?.searchFields?.[0] || cfg.searchFields?.[0] || 'id').trim() || 'id'
  return { [pk]: rawValue }
}

function lookupDisplayViewFields(col: Column): Array<{ fieldName: string; displayLabel?: string; sortOrder?: number; visible?: boolean }> {
  const fields = col.props?.viewFields
  if (!Array.isArray(fields)) return []
  return [...fields]
    .filter((field: any) => field?.visible !== false)
    .sort((a: any, b: any) => (a?.sortOrder ?? 0) - (b?.sortOrder ?? 0))
}

export function effectiveLookupViewFields(col: Column, rawValue: unknown): Array<{ fieldName: string; displayLabel?: string; sortOrder?: number; visible?: boolean }> {
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
    if (tid != null && rawValue != null && (typeof rawValue === 'string' || typeof rawValue === 'number')) {
      const ck = `${Number(tid)}:${String(rawValue).trim()}`
      const hit = lookupHydratedScalar.value[ck]
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
    const pending: Promise<void>[] = []
    const resolvedScalars: Record<string, Record<string, any>> = {}
    for (const col of props.columns || []) {
      if (col.type !== 'lookup') continue
      const tableId = col.props?.tableId
      if (tableId == null || !Number.isFinite(Number(tableId))) continue
      const pk =
        (typeof (col.props as { primaryKeyField?: string }).primaryKeyField === 'string' &&
          (col.props as { primaryKeyField?: string }).primaryKeyField) ||
        (props.primaryKeyFields?.length === 1 ? props.primaryKeyFields[0] : undefined) ||
        'id'
      for (const row of tableRows) {
        const raw = row[col.field]
        if (raw == null || typeof raw === 'object') continue
        const ck = `${Number(tableId)}:${String(raw).trim()}`
        if (lookupHydratedScalar.value[ck]) continue
        pending.push(
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
      await Promise.all(pending.slice(i, i + BATCH))
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
