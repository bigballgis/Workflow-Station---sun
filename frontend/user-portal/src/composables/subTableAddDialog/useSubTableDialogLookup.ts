import { ref, type Ref } from 'vue'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'

/**
 * Lookup backfill state for the sub-table add/edit dialog: view-field metadata
 * (from column props or LookupField API load) and the hydrated selected row used
 * when modelValue is still a scalar PK (matches FormRenderer).
 *
 * Behaviour preserved verbatim from the original SFC.
 */
export function useSubTableDialogLookup(formData: Ref<Record<string, any>>) {
  /** View-field metadata for lookup backfill (from column props or LookupField API load). */
  const lookupLoadedViewFields = ref<Record<string, any[]>>({})
  /** Hydrated lookup row for backfill when modelValue is still a scalar PK (matches FormRenderer). */
  const lookupSelectedData = ref<Record<string, Record<string, unknown>>>({})

  function isLookupRowSelected(val: unknown): boolean {
    return (
      val != null &&
      typeof val === 'object' &&
      !Array.isArray(val) &&
      Object.keys(val as Record<string, unknown>).length > 0
    )
  }

  function effectiveLookupViewFieldsForDialog(col: DialogColumn): any[] {
    const fromCol = col.props?.viewFields
    if (Array.isArray(fromCol) && fromCol.length > 0) return fromCol as any[]
    return lookupLoadedViewFields.value[col.field] || []
  }

  function onLookupViewFieldsLoaded(field: string, fields: any[]) {
    lookupLoadedViewFields.value = { ...lookupLoadedViewFields.value, [field]: fields }
  }

  function onLookupSelect(field: string, row: Record<string, unknown>) {
    lookupSelectedData.value = { ...lookupSelectedData.value, [field]: row }
  }

  function effectiveLookupSelectedRow(field: string): Record<string, unknown> | null {
    const fromSelect = lookupSelectedData.value[field]
    if (fromSelect && Object.keys(fromSelect).length > 0) return fromSelect
    const val = formData.value[field]
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
    onLookupViewFieldsLoaded,
    onLookupSelect,
    effectiveLookupSelectedRow,
    resetLookupState,
  }
}
