import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { suggestTableName } from '@/utils/fieldNameSlug'

interface UseTableCreateOptions {
  functionUnitId: number
  store: {
    createTable: (functionUnitId: number, form: any) => Promise<unknown>
  }
  validateName: (name: string) => boolean
  existingTableNames: (excludeId?: number) => string[]
  assertTableNameAvailable: (tableName: string, excludeTableId?: number) => Promise<boolean>
  loadTables: () => Promise<void> | void
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * "Create table" dialog: form state, name auto-slug syncing, and submission.
 */
export function useTableCreate(options: UseTableCreateOptions) {
  const { functionUnitId, store, validateName, existingTableNames, assertTableNameAvailable, loadTables, t } = options

  const showCreateDialog = ref(false)
  const createForm = reactive({ tableName: '', tableDisplayName: '', tableType: 'MAIN', description: '' })
  const createTableNameTouched = ref(false)

  function resetCreateForm() {
    Object.assign(createForm, { tableName: '', tableDisplayName: '', tableType: 'MAIN', description: '' })
    createTableNameTouched.value = false
  }

  function openCreateDialog() {
    resetCreateForm()
    showCreateDialog.value = true
  }

  function onCreateTableDisplayNameInput() {
    if (createTableNameTouched.value) return
    createForm.tableName = suggestTableName(createForm.tableDisplayName || '', existingTableNames())
  }

  function onCreateTableNameManualInput() {
    createTableNameTouched.value = true
  }

  async function handleCreateTable() {
    if (!createForm.tableDisplayName?.trim()) {
      ElMessage.warning(t('table.displayNameRequired'))
      return
    }
    // Validate table name
    if (!validateName(createForm.tableName)) {
      ElMessage.warning(t('table.invalidTableName'))
      return
    }
    if (!await assertTableNameAvailable(createForm.tableName)) {
      return
    }
    try {
      await store.createTable(functionUnitId, createForm)
      ElMessage.success(t('functionUnit.createSuccess'))
      showCreateDialog.value = false
      resetCreateForm()
      loadTables()
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('common.error'))
    }
  }

  return {
    showCreateDialog,
    createForm,
    openCreateDialog,
    onCreateTableDisplayNameInput,
    onCreateTableNameManualInput,
    handleCreateTable,
  }
}
