import { computed, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FilterFieldOption } from '@/utils/mainTableViewFilter'
import { SYSTEM_VIEW_FIELDS } from '@/api/mainTableView'
import { functionUnitApi, type TableDefinition } from '@/api/functionUnit'
import { isMainTableDefinition } from '@/utils/formDesigner'
import { markSlaDerivedFields } from '@/utils/formFieldMeta'

export interface ActionPostEmailFilterFieldScope {
  /** ACTION table bound to the FORM_POPUP action's popup form; its fields get `source: 'ACTION'`. */
  actionTableId?: number | null
  mainGroupLabel?: string
  actionGroupLabel?: (tableName: string) => string
}

/**
 * Pure builder — system fields plus MAIN table columns, plus the popup form's ACTION table columns
 * when `scope.actionTableId` resolves (same allow-list as DW save validation). Options are grouped
 * only when ACTION fields are present.
 */
export function buildActionPostEmailFilterFieldOptions(
  tables: TableDefinition[],
  scope: ActionPostEmailFilterFieldScope = {},
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
  const mainOptions = [...systemFields, ...tableFields]

  const actionTable = scope.actionTableId != null
    ? tables.find(t => Number(t.id) === Number(scope.actionTableId))
    : undefined
  if (!actionTable?.fieldDefinitions?.length) {
    return mainOptions
  }
  const tableName = actionTable.tableDisplayName || actionTable.tableName
  const actionGroup = scope.actionGroupLabel?.(tableName) ?? tableName
  const actionOptions: FilterFieldOption[] = actionTable.fieldDefinitions.map(f => ({
    fieldName: f.fieldName,
    label: f.displayName || f.fieldName,
    systemField: false,
    source: 'ACTION',
    group: actionGroup,
  }))
  const mainGroup = scope.mainGroupLabel ?? ''
  return [...mainOptions.map(o => ({ ...o, group: mainGroup })), ...actionOptions]
}

export interface ActionPostEmailFilterTarget {
  actionType?: string | null
  /** FORM_POPUP `config.formId` — the popup form whose ACTION binding scopes ACTION fields. */
  formId?: number | null
}

/**
 * Loads FU table definitions for post-email send conditions (does not rely on Pinia
 * `store.tables`, which may be empty until another tab fetches). For FORM_POPUP actions also
 * resolves the popup form's ACTION table binding.
 */
export function useActionPostEmailFilterFields(
  functionUnitId: MaybeRefOrGetter<number>,
  target: MaybeRefOrGetter<ActionPostEmailFilterTarget> = {},
) {
  const { t } = useI18n()
  const tables = ref<TableDefinition[]>([])
  const actionTableId = ref<number | null>(null)
  const loadingFieldOptions = ref(false)

  const fieldOptions = computed(() => buildActionPostEmailFilterFieldOptions(tables.value, {
    actionTableId: actionTableId.value,
    mainGroupLabel: t('action.postEmail.mainFieldGroup'),
    actionGroupLabel: table => t('action.postEmail.actionFieldGroup', { table }),
  }))

  async function loadActionTableId(id: number): Promise<number | null> {
    const { actionType, formId } = toValue(target)
    if ((actionType || '').toUpperCase() !== 'FORM_POPUP' || !formId) {
      return null
    }
    const res = await functionUnitApi.getFormBindings(id, Number(formId))
    return (res.data ?? []).find(b => b.bindingType === 'ACTION')?.tableId ?? null
  }

  async function loadFieldOptions(): Promise<void> {
    const id = Number(toValue(functionUnitId))
    if (!Number.isFinite(id) || id <= 0) {
      tables.value = []
      actionTableId.value = null
      return
    }
    loadingFieldOptions.value = true
    try {
      const [res, boundActionTableId] = await Promise.all([
        functionUnitApi.getTables(id),
        loadActionTableId(id),
      ])
      tables.value = (res.data ?? []).map(markSlaDerivedFields)
      actionTableId.value = boundActionTableId
    } finally {
      loadingFieldOptions.value = false
    }
  }

  watch(
    [
      () => toValue(functionUnitId),
      () => toValue(target).actionType,
      () => toValue(target).formId,
    ],
    () => void loadFieldOptions(),
    { immediate: true },
  )

  return { fieldOptions, loadingFieldOptions, loadFieldOptions }
}
