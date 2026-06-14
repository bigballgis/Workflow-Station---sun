import { ref, computed } from 'vue'
import type { Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { functionUnitApi, type TableDefinition, type FieldDefinition, type ForeignKeyDTO } from '@/api/functionUnit'

interface TableRelation {
  id?: number
  sourceTableId: number | null
  sourceFieldName: string
  relationType: string
  targetTableId: number | null
  targetFieldName: string
}

interface UseTableListOptions {
  functionUnitId: number
  store: {
    tables: TableDefinition[]
    fetchTables: (functionUnitId: number) => Promise<TableDefinition[]>
    deleteTable: (functionUnitId: number, tableId: number) => Promise<unknown>
  }
  selectedTable: Ref<TableDefinition | null>
  relations: Ref<TableRelation[]>
  foreignKeys: Ref<ForeignKeyDTO[]>
  normalizeFieldRow: (f: FieldDefinition) => FieldDefinition
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Table list loading/refresh, relation loading, selection, deletion, and the
 * derived helpers (type label, related-table lookups) used by the list view.
 */
export function useTableList(options: UseTableListOptions) {
  const { functionUnitId, store, selectedTable, relations, foreignKeys, normalizeFieldRow, t } = options

  const loading = ref(false)

  const otherTables = computed(() => {
    const currentId = selectedTable.value?.id
    return store.tables.filter(tbl => tbl.id !== currentId)
  })

  const tableTypeLabel = (type: string) => {
    const map: Record<string, string> = { MAIN: t('table.mainTable'), SUB: t('table.subTable'), ACTION: t('table.actionTable'), RELATION: t('table.relationTable') }
    return map[type] || type
  }

  function getTableFields(tableId: number | null): FieldDefinition[] {
    if (!tableId) return []
    const table = store.tables.find(tbl => tbl.id === tableId)
    return table?.fieldDefinitions || []
  }

  function getTableRelations(tableId: number): (TableRelation | ForeignKeyDTO)[] {
    // Combine local relations and database foreign keys
    const localRelations = relations.value.filter(r => r.sourceTableId === tableId || r.targetTableId === tableId)
    const dbRelations = foreignKeys.value.filter(fk => fk.sourceTableId === tableId || fk.targetTableId === tableId)
    return [...localRelations, ...dbRelations]
  }

  async function loadRelations() {
    // Load table relations from backend API
    try {
      const res = await functionUnitApi.getTableRelations(functionUnitId)
      relations.value = res?.data || []
    } catch {
      relations.value = []
    }

    // Load DB foreign keys from API
    try {
      const res = await functionUnitApi.getForeignKeys(functionUnitId)
      foreignKeys.value = res?.data || []
    } catch {
      foreignKeys.value = []
    }
  }

  async function loadTables() {
    loading.value = true
    try {
      const tables = await store.fetchTables(functionUnitId)
      console.log('[TableDesigner] Loaded tables:', tables)
      tables.forEach(table => {
        console.log(`[TableDesigner] Table ${table.tableName} has ${table.fieldDefinitions?.length || 0} fields`)
      })
      await loadRelations()
      // 如果当前选中的表还在，更新选中表的数据
      if (selectedTable.value) {
        const updatedTable = tables.find(tbl => tbl.id === selectedTable.value!.id)
        if (updatedTable) {
          selectedTable.value = {
            ...updatedTable,
            fieldDefinitions: [...(updatedTable.fieldDefinitions || []).map(f => normalizeFieldRow(f))]
          }
          console.log('[TableDesigner] Updated selected table with', selectedTable.value.fieldDefinitions?.length || 0, 'fields')
        }
      }
    } finally {
      loading.value = false
    }
  }

  async function handleDeleteTable(row: TableDefinition) {
    await ElMessageBox.confirm(t('functionUnit.deleteConfirm'), t('functionUnit.confirmTitle'), { type: 'warning' })
    try {
      await store.deleteTable(functionUnitId, row.id)
      ElMessage.success(t('functionUnit.deleteSuccess'))
      loadTables()
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('common.error'))
    }
  }

  return {
    loading,
    otherTables,
    tableTypeLabel,
    getTableFields,
    getTableRelations,
    loadRelations,
    loadTables,
    handleDeleteTable,
  }
}
