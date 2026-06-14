import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { type TableDefinition, type FieldDefinition } from '@/api/functionUnit'
import { serializePkGeneration } from '@/utils/pkGenerationConfig'

interface UseTableTemplateOptions {
  functionUnitId: number
  store: {
    createTable: (functionUnitId: number, form: any) => Promise<unknown>
  }
  validateName: (name: string) => boolean
  assertTableNameAvailable: (tableName: string, excludeTableId?: number) => Promise<boolean>
  loadTables: () => Promise<void> | void
  t: (key: string, params?: Record<string, unknown>) => string
}

const EXPORT_FORMAT = 'workflow-station-table-template'
const EXPORT_VERSION = 1

interface TableTemplate {
  format: string
  version: number
  tableName: string
  tableDisplayName?: string
  tableType: string
  description?: string
  fieldDefinitions: Omit<FieldDefinition, 'id'>[]
}

/**
 * Table template export (download JSON) and import (parse + create) actions.
 */
export function useTableTemplate(options: UseTableTemplateOptions) {
  const { functionUnitId, store, validateName, assertTableNameAvailable, loadTables, t } = options

  const importing = ref(false)
  const fileInputRef = ref<HTMLInputElement | null>(null)

  function handleExportTable(row: TableDefinition) {
    const template: TableTemplate = {
      format: EXPORT_FORMAT,
      version: EXPORT_VERSION,
      tableName: row.tableName,
      tableDisplayName: row.tableDisplayName,
      tableType: row.tableType,
      description: row.description,
      fieldDefinitions: (row.fieldDefinitions || []).map(f => ({
        fieldName: f.fieldName,
        dataType: f.dataType,
        length: f.length,
        precision: f.precision,
        scale: f.scale,
        nullable: f.nullable,
        isPrimaryKey: f.isPrimaryKey,
        defaultValue: f.defaultValue,
        displayName: f.displayName,
      })),
    }
    const json = JSON.stringify(template, null, 2)
    const blob = new Blob([json], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.tableName}.table-template.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(t('table.exportSuccess'))
  }

  function handleImportClick() {
    fileInputRef.value?.click()
  }

  async function handleImportFile(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return

    importing.value = true
    try {
      const text = await file.text()
      const template: TableTemplate = JSON.parse(text)

      // Validate format
      if (template.format !== EXPORT_FORMAT) {
        ElMessage.warning(t('table.importInvalidFormat'))
        return
      }
      if (!template.tableName || !validateName(template.tableName)) {
        ElMessage.warning(t('table.invalidTableName'))
        return
      }
      if (!await assertTableNameAvailable(template.tableName)) {
        return
      }
      if (!template.fieldDefinitions || !Array.isArray(template.fieldDefinitions)) {
        ElMessage.warning(t('table.importNoFields'))
        return
      }

      // Build create request
      const requestData = {
        tableName: template.tableName,
        tableDisplayName: template.tableDisplayName,
        tableType: template.tableType || 'MAIN',
        description: template.description,
        fields: template.fieldDefinitions
          .filter(f => f.fieldName && f.fieldName.trim())
          .map((f, index) => ({
            fieldName: f.fieldName,
            dataType: f.dataType || 'VARCHAR',
            length: f.length,
            precision: f.precision,
            scale: f.scale,
            nullable: f.nullable !== undefined ? f.nullable : true,
            defaultValue: f.defaultValue,
            isPrimaryKey: f.isPrimaryKey || false,
            displayName: f.displayName,
            pkGeneration: serializePkGeneration(f.pkGeneration, f.isPrimaryKey),
            sortOrder: index,
          })),
      }

      await store.createTable(functionUnitId, requestData as any)
      ElMessage.success(t('table.importSuccess'))
      loadTables()
    } catch (e: any) {
      if (e instanceof SyntaxError) {
        ElMessage.error(t('table.importParseError'))
      } else {
        const msg = e.response?.data?.error?.message
          || e.response?.data?.message
          || e.message
          || t('common.error')
        ElMessage.error(msg)
      }
    } finally {
      importing.value = false
      // Reset file input so the same file can be re-imported
      if (input) input.value = ''
    }
  }

  return {
    importing,
    fileInputRef,
    handleExportTable,
    handleImportClick,
    handleImportFile,
  }
}
