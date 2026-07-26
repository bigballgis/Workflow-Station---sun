import { Calendar, Document, Coin, Switch as SwitchIcon, EditPen } from '@element-plus/icons-vue'
import type { ComputedRef } from 'vue'
import type { SubTableFieldDTO } from '@/api/subTableView'
import { resolveBindingDisplayName } from '@/utils/bindingDisplayHelpers'
import {
  buildPreviewAutofillModelValue,
  effectiveLookupFilterConditionsForRow,
  normalizeLookupRow,
  type LookupCascadeConfig,
} from '@/utils/lookupCascade'
import { parseLookupConfig } from '@/utils/formPreview'
import type {
  LookupPreviewConfig,
  SubTableBindingOption,
  SubTableListColumnDTO,
  SubTableListViewProps,
  TFn,
} from './types'

interface UseColumnHelpersOptions {
  props: SubTableListViewProps
  subTableBindingOptions: ComputedRef<SubTableBindingOption[]>
  t: TFn
}

/**
 * 列分类、键/标签、字段图标与 mock 值等纯展示帮助函数（列配置/Link Form/Lookup）。
 */
export function useColumnHelpers(options: UseColumnHelpersOptions) {
  const { props, subTableBindingOptions, t } = options

  const isLinkColumn = (column: SubTableListColumnDTO) => column.columnType === 'linkForm'
  const isLookupColumn = (column: SubTableListColumnDTO) => column.columnType === 'lookup'
  const isConfigurableActionColumn = (column: SubTableListColumnDTO) => isLinkColumn(column) || isLookupColumn(column)
  const getLinkColumnKey = (componentId: number) => `linkForm:${componentId}`
  const getColumnKey = (column: SubTableListColumnDTO) => isLinkColumn(column)
    ? getLinkColumnKey(column.componentId || 0)
    : isLookupColumn(column)
      ? column.fieldName
      : column.fieldName
  const getColumnLabel = (column: SubTableListColumnDTO) => {
    if (isLinkColumn(column)) {
      return column.columnLabel || column.displayName || column.linkText || t('linkForm.defaultLinkText')
    }
    if (isLookupColumn(column)) {
      return column.columnLabel || column.displayName || 'Lookup'
    }
    return column.displayName || column.fieldName
  }
  const getLinkText = (column: SubTableListColumnDTO) => column.linkText || t('linkForm.defaultLinkText')

  function resolveSubTableBindingDisplayName(bindingId: unknown): string {
    return resolveBindingDisplayName(bindingId, subTableBindingOptions.value)
  }

  function getLinkFormBoundTableName(column: SubTableListColumnDTO | null): string {
    if (!column || !isLinkColumn(column)) {
      return props.binding.tableDisplayName || props.binding.tableName
    }
    return (
      column.boundSubTableName
      || resolveSubTableBindingDisplayName(column.boundSubTableBindingId)
      || props.binding.tableDisplayName
      || props.binding.tableName
    )
  }

  const defaultLookupPreviewConfig: LookupPreviewConfig = {
    placeholder: 'Click to search',
    searchFields: [],
    displayFields: [],
    selectedDisplayField: '',
    filterConditions: [],
    viewFields: [],
    fieldDefs: [],
    showBackfillView: true,
    multiple: false,
  }
  const getLookupPreviewConfig = (column: SubTableListColumnDTO): LookupPreviewConfig => {
    const base = props.resolveLookupPreviewConfig?.(column.lookupConfig || '{}') || defaultLookupPreviewConfig
    const cfg = parseLookupConfig(column.lookupConfig || '{}')
    return {
      ...base,
      derivedFrom: base.derivedFrom || cfg.derivedFrom,
      multiple: base.multiple === true || cfg.multiple === true,
    }
  }

  /** List/Card design preview: cascade filters for the mock data row. */
  const getLookupFilterConditionsForMockRow = (
    column: SubTableListColumnDTO,
    mockRow: Record<string, unknown>,
  ) => {
    const preview = getLookupPreviewConfig(column)
    const cfg: LookupCascadeConfig = {
      filterConditions: preview.filterConditions || [],
      derivedFrom: preview.derivedFrom as LookupCascadeConfig['derivedFrom'],
    }
    return effectiveLookupFilterConditionsForRow(cfg.filterConditions || [], cfg, mockRow)
  }

  const applyLookupCascadeOnMockRow = (
    column: SubTableListColumnDTO,
    value: unknown,
    mockRow: Record<string, unknown>,
    lookupColumns: SubTableListColumnDTO[],
  ): Record<string, unknown> => {
    const next = { ...mockRow }
    const parentRow = normalizeLookupRow(value)
    next[column.fieldName] = parentRow
    for (const dep of lookupColumns) {
      if (dep.fieldName === column.fieldName) continue
      const depPreview = getLookupPreviewConfig(dep)
      if (depPreview.derivedFrom?.parentField !== column.fieldName) continue
      if (depPreview.derivedFrom.derivedMode !== 'autofill') continue
      const depCfg: LookupCascadeConfig = {
        filterConditions: depPreview.filterConditions || [],
        derivedFrom: depPreview.derivedFrom as LookupCascadeConfig['derivedFrom'],
      }
      next[dep.fieldName] = parentRow
        ? buildPreviewAutofillModelValue(depCfg, parentRow, {
          searchFields: depPreview.searchFields,
          selectedDisplayField: depPreview.selectedDisplayField,
          displayFields: depPreview.displayFields,
          multiple: depPreview.multiple === true,
        })
        : (depPreview.multiple === true ? [] : null)
    }
    return next
  }

  const getFieldIcon = (dataType: string) => {
    const type = (dataType || '').toUpperCase()
    if (type.includes('INT') || type.includes('DECIMAL') || type.includes('NUMERIC')) return Coin
    if (type.includes('DATE') || type.includes('TIME') || type.includes('TIMESTAMP')) return Calendar
    if (type.includes('BOOL')) return SwitchIcon
    if (type.includes('TEXT') || type.includes('CLOB')) return Document
    return EditPen
  }

  const getMockValue = (field: SubTableFieldDTO): string => {
    const type = (field.dataType || '').toUpperCase()
    if (type.includes('INT') || type === 'BIGINT') return '1'
    if (type.includes('DECIMAL') || type.includes('NUMERIC') || type.includes('FLOAT') || type.includes('DOUBLE')) return '100.00'
    if (type === 'BOOLEAN' || type === 'BOOL') return 'true'
    if (type === 'DATE') return '2026-01-01'
    if (type.includes('TIMESTAMP') || type === 'DATETIME') return '2026-01-01 00:00:00'
    if (type.includes('TIME')) return '00:00:00'
    if (type === 'TEXT' || type.includes('CLOB')) return 'Sample text'
    if (type === 'FILE') return 'file.pdf'
    return 'Sample'
  }

  return {
    isLinkColumn,
    isLookupColumn,
    isConfigurableActionColumn,
    getLinkColumnKey,
    getColumnKey,
    getColumnLabel,
    getLinkText,
    resolveSubTableBindingDisplayName,
    getLinkFormBoundTableName,
    getLookupPreviewConfig,
    getLookupFilterConditionsForMockRow,
    applyLookupCascadeOnMockRow,
    getFieldIcon,
    getMockValue,
  }
}
