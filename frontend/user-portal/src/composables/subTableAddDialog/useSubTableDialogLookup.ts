import { ref, type Ref } from 'vue'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'
import type { FormField } from '@/components/formRendererHelpers'
import {
  lookupFilterConditionsForField,
  processLookupCascadeClear,
  processLookupCascadeSelect,
} from '@/composables/formRenderer/useFormLookupCascade'
import { parseLookupConfig } from '@/components/subTableAddDialogHelpers/lookup'

function dialogColumnAsLookupField(col: DialogColumn): FormField {
  const cfg = parseLookupConfig(col.props?.lookupConfig)
  return {
    key: col.field,
    label: col.label,
    type: 'lookup',
    _lookupTableId: Number(col.props?.tableId || cfg.tableId || 0),
    _lookupSearchFields: (col.props?.searchFields as string[]) || cfg.searchFields || [],
    _lookupDisplayField: String(col.props?.displayField || cfg.displayFields?.[0] || ''),
    _lookupDisplayFields: (col.props?.displayFields as string[]) || cfg.displayFields || [],
    _lookupSelectedDisplayField: String(col.props?.selectedDisplayField || cfg.selectedDisplayField || ''),
    _lookupFilterConditions: (col.props?.filterConditions as import('@/utils/lookupFilterConditions').LookupFilterCondition[]) || cfg.filterConditions || [],
    _lookupDerivedFrom: cfg.derivedFrom,
    _lookupMultiple: col.props?.multiple === true || cfg.multiple === true,
    _lookupConfig: typeof col.props?.lookupConfig === 'string'
      ? col.props.lookupConfig
      : JSON.stringify(cfg || {}),
    // Carry the designed backfill view through too: the dialog template reads col.props directly
    // today, but a FormField that silently lost these would reintroduce the "all relation-table
    // columns" fallback for any future consumer.
    _lookupViewFields: (col.props?.viewFields as unknown[]) || [],
    _lookupShowBackfillView: col.props?.showBackfillView !== false,
  } as FormField
}

/**
 * Lookup backfill + derived cascade for the sub-table add/edit dialog row.
 */
export function useSubTableDialogLookup(
  formData: Ref<Record<string, unknown>>,
  columns: Ref<DialogColumn[]>,
) {
  const lookupLoadedViewFields = ref<Record<string, unknown[]>>({})
  const lookupSelectedData = ref<Record<string, Record<string, unknown>>>({})

  const lookupFields = (): FormField[] =>
    columns.value.filter(c => c.type === 'lookup').map(dialogColumnAsLookupField)

  function isLookupRowSelected(val: unknown): boolean {
    return (
      val != null &&
      typeof val === 'object' &&
      !Array.isArray(val) &&
      Object.keys(val as Record<string, unknown>).length > 0
    )
  }

  function isLookupModelPresent(val: unknown): boolean {
    if (val == null || val === '') return false
    if (typeof val === 'string' && val.trim() === '') return false
    if (typeof val === 'object' && !Array.isArray(val) && Object.keys(val as object).length === 0) {
      return false
    }
    return true
  }

  function effectiveLookupViewFieldsForDialog(col: DialogColumn): unknown[] {
    const fromCol = col.props?.viewFields
    if (Array.isArray(fromCol) && fromCol.length > 0) return fromCol
    return lookupLoadedViewFields.value[col.field] || []
  }

  function effectiveLookupFilterConditions(col: DialogColumn) {
    return lookupFilterConditionsForField(dialogColumnAsLookupField(col), lookupSelectedData.value)
  }

  function onLookupViewFieldsLoaded(field: string, fields: unknown[]) {
    lookupLoadedViewFields.value = { ...lookupLoadedViewFields.value, [field]: fields }
  }

  async function onLookupSelect(field: string, row: Record<string, unknown>) {
    lookupSelectedData.value = { ...lookupSelectedData.value, [field]: row }
    const colField = lookupFields().find((f) => f.key === field)
    const isMulti = (colField as { _lookupMultiple?: boolean } | undefined)?._lookupMultiple === true
    // Multi LOOKUP modelValue is already set via @update:modelValue.
    if (!isMulti) {
      formData.value[field] = row
    }
    await processLookupCascadeSelect(
      field,
      row,
      lookupFields(),
      formData,
      lookupSelectedData,
      (key, value) => { formData.value[key] = value },
    )
  }

  function onLookupClear(field: string) {
    processLookupCascadeClear(
      field,
      lookupFields(),
      formData,
      lookupSelectedData,
      (key, value) => { formData.value[key] = value },
    )
    delete lookupSelectedData.value[field]
    formData.value[field] = null
  }

  function effectiveLookupSelectedRow(field: string): Record<string, unknown> | null {
    const val = formData.value[field]
    if (!isLookupModelPresent(val)) return null
    const fromSelect = lookupSelectedData.value[field]
    if (fromSelect && Object.keys(fromSelect).length > 0) return fromSelect
    if (isLookupRowSelected(val)) return val as Record<string, unknown>
    return null
  }

  function resetLookupState() {
    lookupLoadedViewFields.value = {}
    lookupSelectedData.value = {}
  }

  return {
    lookupLoadedViewFields,
    lookupSelectedData,
    isLookupRowSelected,
    effectiveLookupViewFieldsForDialog,
    effectiveLookupFilterConditions,
    onLookupViewFieldsLoaded,
    onLookupSelect,
    onLookupClear,
    effectiveLookupSelectedRow,
    resetLookupState,
  }
}
