import { computed, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import type { FilterFieldOption } from '@/utils/mainTableViewFilter'
import { SYSTEM_VIEW_FIELDS } from '@/api/mainTableView'
import { functionUnitApi, type TableDefinition } from '@/api/functionUnit'
import { isMainTableDefinition } from '@/utils/formDesigner'
import { markSlaDerivedFields } from '@/utils/formFieldMeta'

/** Pure builder — system fields plus MAIN table columns (same allow-list as DW save validation). */
export function buildActionPostEmailFilterFieldOptions(
  tables: TableDefinition[],
): FilterFieldOption[] {
  const mainTable = tables.find(isMainTableDefinition)
  const tableFields: FilterFieldOption[] = (mainTable?.fieldDefinitions ?? []).map(f => ({
    fieldName: f.fieldName,
    label: f.displayName || f.fieldName,
    systemField: false,
  }))
  const systemFields: FilterFieldOption[] = SYSTEM_VIEW_FIELDS.map(f => ({
    fieldName: f.fieldName,
    label: f.displayName || f.fieldName,
    systemField: true,
  }))
  return [...systemFields, ...tableFields]
}

/**
 * Loads FU table definitions for post-email send conditions (does not rely on Pinia
 * `store.tables`, which may be empty until another tab fetches).
 */
export function useActionPostEmailFilterFields(functionUnitId: MaybeRefOrGetter<number>) {
  const tables = ref<TableDefinition[]>([])
  const loadingFieldOptions = ref(false)

  const fieldOptions = computed(() => buildActionPostEmailFilterFieldOptions(tables.value))

  async function loadFieldOptions(): Promise<void> {
    const id = Number(toValue(functionUnitId))
    if (!Number.isFinite(id) || id <= 0) {
      tables.value = []
      return
    }
    loadingFieldOptions.value = true
    try {
      const res = await functionUnitApi.getTables(id)
      tables.value = (res.data ?? []).map(markSlaDerivedFields)
    } finally {
      loadingFieldOptions.value = false
    }
  }

  watch(
    () => toValue(functionUnitId),
    () => void loadFieldOptions(),
    { immediate: true },
  )

  return { fieldOptions, loadingFieldOptions, loadFieldOptions }
}
