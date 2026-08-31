import type { FormField } from '@/components/formRendererHelpers/formRendererTypes'
import { extractFieldsRecursive } from '@/components/formRendererHelpers/formRendererRuleParsing'
import type { SubTableBinding } from '@/composables/formRenderer/useSubTableBindings'

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

export function nestedRowsFromViewValues(
  values: Record<string, unknown>,
  bindingId: number,
  tableName?: string,
): unknown[] {
  const sto = values.__subTables__
  if (!sto || typeof sto !== 'object' || Array.isArray(sto)) return []
  const map = sto as Record<string, unknown>
  const hit = map[bindingId] ?? map[String(bindingId)] ?? (tableName ? map[tableName] : undefined)
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
      physicalTableName: typeof b.tableName === 'string' ? b.tableName : undefined,
      tableType: String(b.tableType || 'SUB'),
      tableDescription: String(b.tableDescription || ''),
      columns,
      data: nestedRowsFromViewValues(rowValues, bindingId, tableName),
      ...(formFields.length > 0 ? { formFields } : {}),
      fieldDefinitions: (b.fieldDefinitions as SubTableBinding['fieldDefinitions']) ?? [],
      foreignKeyField: (b.foreignKeyField as string | null | undefined) ?? null,
    })
  }
  return out
}
