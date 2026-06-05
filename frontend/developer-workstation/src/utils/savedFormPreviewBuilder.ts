/**
 * Build Form Preview items from saved form config (no live designer refs).
 * Used by Process Debug node-form panel and similar read-only previews.
 */

import type { FormDefinition, TableBinding, TableDefinition } from '@/api/functionUnit'
import type { FormPreviewItem, PreviewSubTableBinding, SubTablePortalViewsPreview } from '@/components/designer/formPreviewTypes'
import { getRuleChildren, isCardRule, getLayoutLabel } from '@/utils/formDesigner'
import { mapFormCreateRulesReadonlyDeep } from '@/utils/formCreateRuleUtils'
import { syncFormRulesWithTableFields } from '@/utils/formFieldMeta'
import { derivePreviewColumns, parseLookupConfig } from '@/utils/formPreview'

const DEFAULT_PORTAL_VIEWS: SubTablePortalViewsPreview = {
  assigneeTodo: 'tableOnly',
  initiatorRequest: 'mirrorTodo',
  assigneeTodoFormSource: { type: 'subForm', formId: null, linkFormColumnId: null },
}

const FC_SKIP_PREVIEW = new Set(['subForm', 'tableForm', 'tableFormColumn', 'group', 'el-row', 'el-col'])

export interface SavedFormPreviewBuildOptions {
  form: FormDefinition
  tables: TableDefinition[]
  /** Optional extra bindings from API when form.tableBindings is incomplete */
  tableBindings?: TableBinding[]
  t: (key: string) => string
}

function normalizeRulesForPreview(rawRule: any[]): any[] {
  return (rawRule || []).map((r: any) => {
    const rp = r.props || {}
    if (r.type === 'transfer') {
      return {
        ...r,
        type: 'el-transfer',
        props: {
          data: (rp.options ?? []).map((o: any) => ({ key: o.value, label: o.label })),
          titles: [rp.leftTitle || 'Source', rp.rightTitle || 'Target'],
          filterable: true,
        },
      }
    }
    if (r.type === 'cascader') {
      return {
        ...r,
        type: 'el-cascader',
        props: {
          options: rp.options ?? [],
          props: rp.cascaderProps || rp.props,
          placeholder: rp.placeholder || 'Please select',
          clearable: true,
        },
      }
    }
    if (r.prefix || r.suffix) {
      const { prefix, suffix, ...rest } = r
      return rest
    }
    return r
  })
}

function getTableName(tables: TableDefinition[], tableId: number, fallback?: string): string {
  const table = tables.find(tb => tb.id === tableId)
  return table?.tableDisplayName || table?.tableName || fallback || ''
}

function mapDataTypeToPreviewColumnType(dataType: string): string | undefined {
  const dt = (dataType || '').toUpperCase()
  if (dt === 'FILE') return 'upload'
  if (dt.includes('INT') || dt === 'BIGINT' || dt.includes('DECIMAL') || dt.includes('NUMERIC') || dt.includes('FLOAT') || dt.includes('DOUBLE')) {
    return 'number'
  }
  if (dt === 'DATE') return 'date'
  if (dt.includes('TIMESTAMP') || dt === 'DATETIME') return 'datetime'
  if (dt === 'BOOLEAN' || dt === 'BOOL') return 'switch'
  return undefined
}

function deriveColumnsFromSubFormRule(rule: any[]): any[] {
  if (!Array.isArray(rule) || !rule.length) return []
  return rule.map((r: any) => {
    const rProps = r.props || {}
    let type: string | undefined
    if (r.type === 'input') {
      if (rProps.type === 'textarea') type = 'textarea'
      else type = 'text'
    } else if (r.type === 'inputNumber') type = 'number'
    else if (r.type === 'select') type = 'select'
    else if (r.type === 'switch') type = 'switch'
    else if (r.type === 'datePicker') type = rProps.type === 'datetime' ? 'datetime' : 'date'
    else if (r.type === 'upload') type = 'upload'
    else type = r.type
    return {
      field: r.field,
      label: r.title || r.field,
      type,
      ...(rProps.action ? { props: { action: rProps.action } } : {}),
    }
  })
}

function deriveColumnsFromTable(tables: TableDefinition[], tableId: number): any[] {
  const table = tables.find(tb => tb.id === tableId)
  const fields = (table as { fieldDefinitions?: Array<Record<string, unknown>> } | undefined)?.fieldDefinitions || []
  return [...fields]
    .sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0))
    .map(f => {
      const type = mapDataTypeToPreviewColumnType(String(f.dataType ?? ''))
      return {
        field: String(f.fieldName ?? ''),
        label: String(f.displayName || f.fieldName || ''),
        type,
        minWidth: type === 'upload' ? 180 : 100,
      }
    })
    .filter(col => col.field.length > 0)
}

function resolveLookupPreviewConfig(
  rawLookupConfig: string,
  config: Record<string, any>,
  tables: TableDefinition[],
  bindingId?: number,
) {
  const lookupConfig = parseLookupConfig(rawLookupConfig)
  const savedRelationView = bindingId ? (config.relationViews || {})[bindingId] : null
  const binding = (config as any)._bindings?.find?.((b: TableBinding) => b.id === bindingId)
  const table = binding ? tables.find(tb => tb.id === binding.tableId) : undefined
  const fieldDefs = ((table as any)?.fieldDefinitions || []).map((f: any) => ({
    fieldName: f.fieldName,
    dataType: f.dataType,
    displayName: f.displayName,
  }))
  return {
    placeholder: 'Click to search',
    searchFields: lookupConfig.searchFields || [],
    displayFields: lookupConfig.displayFields || [],
    selectedDisplayField: lookupConfig.selectedDisplayField || lookupConfig.displayField || '',
    filterConditions: Array.isArray(lookupConfig.filterConditions) ? lookupConfig.filterConditions : [],
    viewFields: lookupConfig.showBackfillView === false ? [] : (savedRelationView?.viewFields || []),
    fieldDefs,
    showBackfillView: lookupConfig.showBackfillView !== false,
    bindingId,
  }
}

function mergePortalViewsForSavedPreview(
  ruleItem: any,
  bindingId: number,
  config: Record<string, any>,
): SubTablePortalViewsPreview {
  const base: SubTablePortalViewsPreview =
    (config.subTablePortalViews?.[bindingId] as SubTablePortalViewsPreview | undefined)
    || { ...DEFAULT_PORTAL_VIEWS, assigneeTodoFormSource: { ...DEFAULT_PORTAL_VIEWS.assigneeTodoFormSource! } }
  const ov = ruleItem?.props?.portalViews
  if (!ov || typeof ov !== 'object') return base
  const assigneeTodo =
    ov.assigneeTodo === 'formBelowTable' ? 'formBelowTable' : ov.assigneeTodo === 'tableOnly' ? 'tableOnly' : base.assigneeTodo
  let initiatorRequest = base.initiatorRequest
  if (ov.initiatorRequest === 'summaryWithLinkFormModal') initiatorRequest = 'summaryWithLinkFormModal'
  else if (ov.initiatorRequest === 'tableOnly') initiatorRequest = 'tableOnly'
  else if (ov.initiatorRequest === 'mirrorTodo') initiatorRequest = 'mirrorTodo'
  return { assigneeTodo, initiatorRequest, assigneeTodoFormSource: base.assigneeTodoFormSource }
}

function toSubTablePreviewColumns(
  bindingId: number,
  rule: any[],
  config: Record<string, any>,
  tables: TableDefinition[],
  bindings: TableBinding[],
  getSubFormDesign: (id: number) => { rule: any[]; options: any },
  t: (key: string) => string,
): any[] {
  const savedColumns = (config.subListViews || {})[bindingId]?.columns
  if (Array.isArray(savedColumns) && savedColumns.length) {
    const ruleByField = new Map((Array.isArray(rule) ? rule : []).map(r => [r?.field, r]))
    return savedColumns.map((column: any) => {
      if (column.columnType === 'linkForm') {
        const targetBindingId = column.boundSubTableBindingId || bindingId
        const targetDesign = getSubFormDesign(Number(targetBindingId))
        return {
          field: column.fieldName || `linkForm:${column.componentId || bindingId}`,
          label: column.columnLabel || column.displayName || column.linkText || t('linkForm.defaultLinkText'),
          type: 'linkForm',
          minWidth: 120,
          props: {
            linkText: column.linkText || t('linkForm.defaultLinkText'),
            formRule: targetDesign.rule,
            formOption: targetDesign.options,
            boundSubTableBindingId: targetBindingId,
            componentId: column.componentId,
          },
        }
      }
      const fieldRule = ruleByField.get(column.fieldName)
      const colType =
        fieldRule?.type === 'upload'
          ? 'upload'
          : mapDataTypeToPreviewColumnType(String(column.dataType ?? column.fieldType ?? ''))
      return {
        field: column.fieldName,
        label: column.displayName || column.columnLabel || fieldRule?.title || column.fieldName,
        type: colType,
        minWidth: colType === 'upload' ? 180 : 100,
      }
    })
  }

  const fromRule = deriveColumnsFromSubFormRule(rule)
  if (fromRule.length) return fromRule

  const binding = bindings.find(b => b.id === bindingId)
  if (binding?.tableId) {
    const fromTable = deriveColumnsFromTable(tables, binding.tableId)
    if (fromTable.length) return fromTable
    const table = tables.find(tb => tb.id === binding.tableId)
    const tableType = table?.tableType || (binding.bindingType === 'RELATED' ? 'RELATION' : 'SUB')
    return derivePreviewColumns(tableType, t)
  }
  return []
}

function makeLookupPreviewItem(ruleItem: any, config: Record<string, any>, tables: TableDefinition[]) {
  const bindingId = parseLookupConfig(ruleItem.props?.lookupConfig || '{}').bindingId
  const previewConfig = resolveLookupPreviewConfig(ruleItem.props?.lookupConfig || '{}', config, tables, bindingId)
  return {
    kind: 'lookup' as const,
    label: ruleItem.title || 'Lookup',
    placeholder: ruleItem.props?.placeholder || previewConfig.placeholder,
    searchFields: previewConfig.searchFields,
    displayFields: previewConfig.displayFields,
    selectedDisplayField: previewConfig.selectedDisplayField,
    filterConditions: previewConfig.filterConditions,
    viewFields: previewConfig.viewFields,
    fieldDefs: previewConfig.fieldDefs,
    showBackfillView: previewConfig.showBackfillView,
    bindingId: previewConfig.bindingId,
  }
}

function buildBindingMap(
  options: SavedFormPreviewBuildOptions,
): Map<number, PreviewSubTableBinding> {
  const { form, tables, t } = options
  const config = form.configJson || {}
  const subForms = config.subForms || {}
  const bindings = options.tableBindings?.length
    ? options.tableBindings
    : (form.tableBindings || [])
  const nonPrimary = bindings.filter(b => b.bindingType !== 'PRIMARY')
  const map = new Map<number, PreviewSubTableBinding>()

  const getSubFormDesign = (bindingId: number) => ({
    rule: subForms[bindingId]?.rule || [],
    options: subForms[bindingId]?.options || {},
  })

  for (const b of nonPrimary) {
    const bindingId = b.id as number
    if (bindingId == null) continue
    const { rule, options: subOpt } = getSubFormDesign(bindingId)
    const columns = toSubTablePreviewColumns(bindingId, rule, config, tables, bindings, getSubFormDesign, t)
    map.set(bindingId, {
      bindingId,
      bindingType: b.bindingType,
      bindingMode: b.bindingMode,
      tableName: getTableName(tables, b.tableId, b.tableName),
      tableType: tables.find(tb => tb.id === b.tableId)?.tableType || (b.bindingType === 'RELATED' ? 'RELATION' : ''),
      tableDescription: tables.find(tb => tb.id === b.tableId)?.description || '',
      rule,
      option: subOpt,
      columns,
      subMode: b.subMode,
    })
  }

  return map
}

function containsSubTableRule(item: any): boolean {
  if (!item) return false
  if (item.type === 'subTable' && (item._bindingId ?? item.props?._bindingId) != null) return true
  return getRuleChildren(item).some(child => containsSubTableRule(child))
}

function buildPreviewItems(
  ruleItems: any[],
  localBindingMap: Map<number, PreviewSubTableBinding>,
  config: Record<string, any>,
  tables: TableDefinition[],
  keyPrefix = 'seg',
): FormPreviewItem[] {
  const items: FormPreviewItem[] = []
  let currentSegment: any[] = []
  let segmentIndex = 0

  function flushSegment() {
    if (currentSegment.length > 0) {
      items.push({ kind: 'fields', rule: [...currentSegment], modelKey: `${keyPrefix}_${segmentIndex++}` })
      currentSegment = []
    }
  }

  for (const ruleItem of ruleItems) {
    const itemBindingId = ruleItem._bindingId ?? ruleItem.props?._bindingId ?? null

    if (ruleItem.type === 'subTable' && itemBindingId != null) {
      flushSegment()
      const binding = localBindingMap.get(Number(itemBindingId))
      if (binding) {
        const mergedPv = mergePortalViewsForSavedPreview(ruleItem, Number(itemBindingId), config)
        items.push({ kind: 'subTable', binding: { ...binding, portalViews: mergedPv } })
        localBindingMap.delete(Number(itemBindingId))
      }
    } else if (isCardRule(ruleItem) && containsSubTableRule(ruleItem)) {
      flushSegment()
      items.push({
        kind: 'card',
        title: getLayoutLabel(ruleItem),
        items: buildPreviewItems(getRuleChildren(ruleItem), localBindingMap, config, tables, `card_${segmentIndex++}`),
        modelKey: `${keyPrefix}_card_${segmentIndex}`,
      })
    } else if (ruleItem.type === 'lookup') {
      flushSegment()
      items.push(makeLookupPreviewItem(ruleItem, config, tables))
    } else if (FC_SKIP_PREVIEW.has(ruleItem.type)) {
      if (containsSubTableRule(ruleItem)) {
        flushSegment()
        items.push(...buildPreviewItems(getRuleChildren(ruleItem), localBindingMap, config, tables, `${keyPrefix}_layout_${segmentIndex++}`))
      }
    } else {
      currentSegment.push(ruleItem)
    }
  }

  flushSegment()
  return items
}

/**
 * Build preview items from persisted form definition (saved configJson).
 */
export function buildSavedFormPreviewItems(options: SavedFormPreviewBuildOptions): FormPreviewItem[] {
  const config = options.form.configJson || {}
  let rawRule = config.rule || []
  if (!Array.isArray(rawRule) || !rawRule.length) {
    return []
  }
  rawRule = normalizeRulesForPreview(rawRule)

  const bindings = options.tableBindings?.length
    ? options.tableBindings
    : (options.form.tableBindings || [])
  const primaryBinding = bindings.find(b => b.bindingType === 'PRIMARY')
  const primaryTable = primaryBinding
    ? options.tables.find(t => t.id === primaryBinding.tableId)
    : undefined
  const primaryFields = primaryTable?.fieldDefinitions || []
  if (primaryFields.length) {
    rawRule = syncFormRulesWithTableFields(rawRule, primaryFields) as typeof rawRule
  }
  rawRule = mapFormCreateRulesReadonlyDeep(rawRule) as typeof rawRule

  const bindingMap = buildBindingMap(options)
  try {
    return buildPreviewItems(rawRule, bindingMap, config, options.tables)
  } catch {
    const basicRule = rawRule.filter((r: any) => r.type !== 'subTable')
    return basicRule.length ? [{ kind: 'fields', rule: basicRule, modelKey: 'fallback' }] : []
  }
}
