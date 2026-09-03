import type { FormField } from '@/components/formRendererHelpers/formRendererTypes'
import { extractFieldsRecursive } from '@/components/formRendererHelpers/formRendererRuleParsing'
import type { SubTableBinding } from '@/composables/formRenderer/useSubTableBindings'
import {
  subTableStoreKey,
  type SubTableStoreBindingLike,
} from '@/composables/tasks/subTableStore'
import { resolveLookupCellTagText } from '@/components/subTableAddDialogHelpers/lookup'

export type ViewDetailLookupDbConfig = {
  tableId: number
  searchFields: string[]
  displayField: string
  viewFields: unknown[]
}

type ViewDetailColumn = { field: string; label: string; type?: string }

function columnsFromListView(
  binding: Record<string, unknown>,
  subListViews: Record<string, { columns?: Array<Record<string, unknown>> }> | undefined,
): ViewDetailColumn[] | null {
  const id = Number(binding.bindingId)
  const lv = subListViews?.[id] ?? subListViews?.[String(id)]
  if (!Array.isArray(lv?.columns) || lv.columns.length === 0) return null
  return lv.columns
    .map(c => {
      const field = String(c.fieldName ?? c.field ?? '')
      if (!field) return null
      return {
        field,
        label: String(c.displayName ?? c.label ?? field),
        ...(typeof c.columnType === 'string' || typeof c.type === 'string'
          ? { type: String(c.columnType ?? c.type) }
          : {}),
      }
    })
    .filter((c): c is ViewDetailColumn => c != null)
}

function columnsFromFieldDefinitions(binding: Record<string, unknown>): ViewDetailColumn[] {
  const defs = (binding.fieldDefinitions as Array<Record<string, unknown>> | undefined) ?? []
  const out: ViewDetailColumn[] = []
  const seen = new Set<string>()
  for (const fd of defs) {
    const field = String(fd.fieldName ?? fd.field_name ?? '').trim()
    if (!field || seen.has(field)) continue
    seen.add(field)
    out.push({ field, label: String(fd.displayName ?? fd.display_name ?? field) })
  }
  return out
}

function columnsFromBinding(
  binding: Record<string, unknown>,
  subListViews: Record<string, { columns?: Array<Record<string, unknown>> }> | undefined,
): ViewDetailColumn[] {
  return columnsFromListView(binding, subListViews) ?? columnsFromFieldDefinitions(binding)
}

/**
 * 取某个 binding 在 `__subTables__` 里的行。
 *
 * <p>只按规范 key 读（见 `subTableStore.ts` 的 {@link subTableStoreKey}）。曾经按
 * `bindingId` / 展示名读：写入端早已只写 `dw:<name>` / `rt:<name>`，那条链永远取不到行，
 * 明细里的子表就静默空着。
 */
export function nestedRowsFromViewValues(
  values: Record<string, unknown>,
  binding: SubTableStoreBindingLike,
): unknown[] {
  const sto = values.__subTables__
  if (!sto || typeof sto !== 'object' || Array.isArray(sto)) return []
  const key = subTableStoreKey(binding)
  if (!key) return []
  const hit = (sto as Record<string, unknown>)[key]
  return Array.isArray(hit) ? hit : []
}

function parseLookupConfig(raw: unknown): Record<string, unknown> {
  if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
    return raw as Record<string, unknown>
  }
  if (typeof raw !== 'string' || raw.trim() === '') return {}
  try {
    const parsed: unknown = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
    return {}
  } catch {
    // FALLBACK(ux): malformed designer lookupConfig must not blank the rest of the Views detail form.
    return {}
  }
}

function toViewDetailLookupField(
  item: Record<string, unknown>,
  field: string,
  lookupDbConfigs: Record<string, ViewDetailLookupDbConfig>,
): FormField {
  const props = (item.props || {}) as Record<string, unknown>
  const lookupCfg = parseLookupConfig(props.lookupConfig)
  const dbCfg = lookupDbConfigs[field]
  const searchFields = lookupCfg.searchFields
  const displayFields = lookupCfg.displayFields
  return {
    key: field,
    label: String(item.title ?? field),
    type: 'lookup',
    span: 12,
    _lookupTableId: lookupCfg.tableId || dbCfg?.tableId || 0,
    _lookupSearchFields: (Array.isArray(searchFields) && searchFields.length ? searchFields : null) || dbCfg?.searchFields || [],
    _lookupDisplayField: (Array.isArray(displayFields) ? displayFields[0] : undefined) || dbCfg?.displayField || '',
    _lookupDisplayFields: displayFields || [],
    _lookupSelectedDisplayField: lookupCfg.selectedDisplayField || lookupCfg.displayField || '',
    _lookupMultiple: lookupCfg.multiple === true,
    _lookupConfig: typeof props.lookupConfig === 'string' ? props.lookupConfig : JSON.stringify(lookupCfg || {}),
    _lookupViewFields: lookupCfg.showBackfillView === false ? [] : (dbCfg?.viewFields || []),
    _lookupShowBackfillView: lookupCfg.showBackfillView !== false,
  } as unknown as FormField
}

/**
 * 把「整行对象」型取值换成它该显示的文本。
 *
 * <p>lookup 列在 `values` 里存的是被引用关联表的一整行（`{id, code, type_name, ...}`）。列表页
 * 另做一次 hydration 取显示列，明细页却把原值直接交给只读渲染器 —— 对象被 `String()` 成
 * `[object Object]`（实测 ATM Correspondence 的 Correspondence type / Channel 两列）。
 *
 * <p>判据是「值是不是对象」，不是字段声明的 type：设计器把 lookup 列摆到明细表单上时通常
 * 就是个 `input`，只认 `type==='lookup'` 会把真正出问题的字段全放过。
 *
 * <p>取哪一列由 {@link resolveLookupCellTagText} 决定 —— 列表页与子表弹窗用的是同一条链
 * （selectedDisplayField → displayFields → displayField → 第一个非主键标量列）。这里不另猜
 * 列名：本 FU 的显示列叫 `standardizations`，任何「name/code/label」白名单都会猜错。
 *
 * <p>只改对象型取值；字符串、数字、null 原样返回。多选（数组）逐项解析后用 `, ` 连接。
 */
export function resolveLookupDisplayValues(
  fields: FormField[],
  values: Record<string, unknown>,
): Record<string, unknown> {
  const out: Record<string, unknown> = { ...values }
  const byKey = new Map<string, Record<string, unknown>>()
  for (const f of fields) {
    const meta = f as unknown as Record<string, unknown>
    const k = String(meta.key ?? '')
    if (k) byKey.set(k, meta)
  }

  // 按「值是不是整行对象」来判断，而不是按字段声明的 type。设计器把 lookup 列摆到明细表单上
  // 时常常就是个 `input`（实测 FU atm 的 DETAIL 表单 12 个字段全是 input/datePicker），
  // 只认 type==='lookup' 会把真正出问题的字段全放过去。
  for (const [key, raw] of Object.entries(values)) {
    if (key === '__subTables__') continue
    const isRowObject = raw != null && typeof raw === 'object'
      && (!Array.isArray(raw) || raw.some(v => v != null && typeof v === 'object'))
    if (!isRowObject) continue

    const meta = byKey.get(key) ?? {}
    const candidates = [
      meta._lookupSelectedDisplayField,
      meta._lookupDisplayField,
      ...(Array.isArray(meta._lookupDisplayFields) ? meta._lookupDisplayFields : []),
    ]
      .map(c => (typeof c === 'string' ? c.trim() : ''))
      .filter(Boolean)

    const pick = (row: unknown): unknown => {
      if (!row || typeof row !== 'object' || Array.isArray(row)) return row
      // 复用列表/子表弹窗那条已有的解析链（selectedDisplayField → displayFields →
      // displayField → 第一个非主键标量列），而不是在这里另猜一套列名。
      const text = resolveLookupCellTagText({
        selectedDisplayField: candidates[0],
        displayFields: candidates,
        searchFields: Array.isArray(meta._lookupSearchFields)
          ? (meta._lookupSearchFields as string[])
          : undefined,
        lookupConfig: meta._lookupConfig,
      }, row as Record<string, unknown>)
      // '-' 是那条链「解析不出」的信号；这里留空，交给渲染器显示空字段。
      return text && text !== '-' ? text : ''
    }

    out[key] = Array.isArray(raw)
      ? raw.map(pick).filter(v => v !== '' && v != null).join(', ')
      : pick(raw)
  }
  return out
}

/**
 * Flattens a Views DETAIL form rule into display fields.
 * Nested {@code subTable} widgets are emitted by the shared extractor; this converter
 * only fills lookup / ordinary fields (the previous skip of {@code subTable} in the
 * converter never ran — the extractor handles that type first).
 */
export function toViewDetailFields(
  items: Record<string, unknown>[],
  lookupDbConfigs: Record<string, ViewDetailLookupDbConfig>,
): FormField[] {
  return extractFieldsRecursive(items, (item) => {
    const field = item.field as string | undefined
    if (!field) return null
    if (item.type === 'lookup') return toViewDetailLookupField(item, field, lookupDbConfigs)
    const props = (item.props || {}) as Record<string, unknown>
    return {
      key: field,
      label: String(item.title ?? field),
      type: String(item.type ?? 'input'),
      span: 12,
      options: (props.options as unknown[]) ?? undefined,
    } as FormField
  })
}

export function buildViewDetailSubTableBindings(
  tableBindings: Array<Record<string, unknown>> | undefined,
  formConfig: Record<string, unknown>,
  rowValues: Record<string, unknown>,
): SubTableBinding[] {
  const subForms = (formConfig.subForms || {}) as Record<string, { rule?: unknown[] }>
  const subListViews = formConfig.subListViews as Record<string, { columns?: Array<Record<string, unknown>> }> | undefined
  const out: SubTableBinding[] = []
  for (const b of tableBindings || []) {
    if (String(b.bindingType || '') === 'PRIMARY') continue
    const bindingId = Number(b.bindingId)
    if (!Number.isFinite(bindingId)) continue
    const columns = columnsFromBinding(b, subListViews)
    if (columns.length === 0) continue
    const tableName = String(b.tableDisplayName || b.tableName || '')
    // 展示名(tableName 变量)与设计器表名是两回事：前者进 UI，后者是 __subTables__ 的 key 来源。
    const physicalTableName = typeof b.tableName === 'string' ? b.tableName : undefined
    const storeBinding: SubTableStoreBindingLike = {
      physicalTableName,
      relationTableId: b.relationTableId != null ? Number(b.relationTableId) : undefined,
      relationTableName: typeof b.relationTableName === 'string' ? b.relationTableName : undefined,
    }
    const design = subForms[bindingId] ?? subForms[String(bindingId)] ?? {}
    const formFields = Array.isArray(design.rule) && design.rule.length > 0
      ? toViewDetailFields(design.rule as Record<string, unknown>[], {})
      : []
    out.push({
      bindingId,
      tableId: b.tableId != null ? Number(b.tableId) : null,
      bindingType: String(b.bindingType || 'SUB'),
      bindingMode: String(b.bindingMode || 'READONLY'),
      tableName,
      physicalTableName,
      tableType: String(b.tableType || 'SUB'),
      tableDescription: String(b.tableDescription || ''),
      columns,
      data: nestedRowsFromViewValues(rowValues, storeBinding),
      ...(formFields.length > 0 ? { formFields } : {}),
      fieldDefinitions: (b.fieldDefinitions as SubTableBinding['fieldDefinitions']) ?? [],
      foreignKeyField: (b.foreignKeyField as string | null | undefined) ?? null,
    })
  }
  return out
}
