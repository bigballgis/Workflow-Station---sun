import { ref } from 'vue'
import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { functionUnitApi, type TableDefinition } from '@/api/functionUnit'

interface TableRelation {
  id?: number
  sourceTableId: number | null
  sourceFieldName: string
  relationType: string
  targetTableId: number | null
  targetFieldName: string
}

interface UseTableToolsOptions {
  functionUnitId: number
  selectedTable: Ref<TableDefinition | null>
  relations: Ref<TableRelation[]>
  loadTables: () => Promise<void> | void
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Side-panel tools: DDL preview dialog, table validation, DDL copy, and the
 * relation-config dialog save flow.
 */
export function useTableTools(options: UseTableToolsOptions) {
  const { functionUnitId, selectedTable, relations, loadTables, t } = options

  const showDDLDialog = ref(false)
  const showRelationDialog = ref(false)
  const ddlDialect = ref('POSTGRESQL')
  const ddlContent = ref('')

  async function handleGenerateDDL() {
    if (!selectedTable.value) return
    try {
      const res = await functionUnitApi.generateDDL?.(functionUnitId, selectedTable.value.id, ddlDialect.value)
      ddlContent.value = res?.data || ''
      showDDLDialog.value = true
    } catch {
      ElMessage.info(t('common.loading'))
    }
  }

  async function handleValidate() {
    try {
      const res = await functionUnitApi.validateTables?.(functionUnitId)
      if (res?.data?.valid) {
        ElMessage.success(t('common.success'))
      } else {
        ElMessage.warning(`${t('common.error')}: ${res?.data?.errors?.join(', ') || t('common.error')}`)
      }
    } catch {
      ElMessage.info(t('common.loading'))
    }
  }

  function handleCopyDDL() {
    navigator.clipboard.writeText(ddlContent.value)
    ElMessage.success(t('common.success'))
  }

  async function handleSaveRelations() {
    // Validate relations
    const validRelations = relations.value.filter(r =>
      r.sourceTableId && r.sourceFieldName && r.relationType && r.targetTableId && r.targetFieldName
    )

    // Save to backend API
    try {
      const res = await functionUnitApi.saveTableRelations(functionUnitId, validRelations as any)
      relations.value = res?.data || validRelations
      ElMessage.success(t('common.success'))
      showRelationDialog.value = false
      // Refresh tables so PK/FK badges reflect the applied field metadata
      loadTables()
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('common.error'))
    }
  }

  return {
    showDDLDialog,
    showRelationDialog,
    ddlDialect,
    ddlContent,
    handleGenerateDDL,
    handleValidate,
    handleCopyDDL,
    handleSaveRelations,
  }
}
