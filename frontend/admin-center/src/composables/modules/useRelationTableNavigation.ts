import { computed, ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { RelationTableResponse } from '@/api/relationTable'

/** URL owns the selected FU/table; grouping always comes from deployed table metadata. */
export function useRelationTableNavigation(
  tables: Ref<RelationTableResponse[]>,
  onSelect: () => void,
) {
  const route = useRoute()
  const router = useRouter()
  const { t } = useI18n()
  const selectedTableId = ref<number | null>(null)
  const selectionError = ref<string | null>(null)
  let ready = false
  const activeGroupKey = computed(() => {
    if (typeof route.params.functionUnitCode === 'string' && route.params.functionUnitCode.length > 0) return route.params.functionUnitCode
    return route.query.group === 'common' ? null : undefined
  })
  const selectedTable = computed(() => tables.value.find(table => table.id === selectedTableId.value) ?? null)
  const selectedGroupLabel = computed(() => {
    if (!selectedTable.value) return ''
    if (activeGroupKey.value === undefined) return t('menu.allFunctionUnits')
    if (activeGroupKey.value === null) return t('tableDataNavigation.common')
    const fu = selectedTable.value.functionUnits.find(unit => unit.code === activeGroupKey.value)
    return fu ? fu.name || fu.code : selectedTable.value.functionUnits.map(unit => unit.name || unit.code).join(' / ')
  })

  function syncSelection(reload = false): void {
    ready = true
    const scope = activeGroupKey.value
    const scopedTables = tables.value.filter(table => scope === undefined
      || (scope === null ? table.functionUnits.length === 0 : table.functionUnits.some(unit => unit.code === scope)))
    const rawId = route.query.tableId
    const requestedId = typeof rawId === 'string' && /^\d+$/.test(rawId) ? Number(rawId) : null
    const selected = rawId === undefined
      ? scopedTables[0]
      : scopedTables.find(table => table.id === requestedId)
    selectionError.value = rawId !== undefined && !selected ? t('tableDataNavigation.unavailable') : null
    const nextId = selected?.id ?? null
    if (nextId !== selectedTableId.value || reload) {
      selectedTableId.value = nextId
      onSelect()
    }
  }

  function handleSelectTable(tableId: number, functionUnitCode: string | null | undefined): void {
    void router.push({
      name: 'RelationTableData',
      params: { functionUnitCode: typeof functionUnitCode === 'string' ? functionUnitCode : '' },
      query: { tableId: String(tableId), ...(functionUnitCode === null ? { group: 'common' } : {}) },
    })
  }

  watch(() => [route.params.functionUnitCode, route.query.tableId, route.query.group], () => {
    if (ready && route.name === 'RelationTableData') syncSelection()
  })

  return { selectedTableId, selectedTable, activeGroupKey, selectedGroupLabel, selectionError, syncSelection, handleSelectTable }
}
