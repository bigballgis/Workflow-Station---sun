import { computed, nextTick } from 'vue'
import type { Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { type TableDefinition, type FieldDefinition } from '@/api/functionUnit'
import { suggestFieldName, suggestTableName } from '@/utils/fieldNameSlug'
import { serializePkGeneration } from '@/utils/pkGenerationConfig'
import { hasRequestIdConfig } from '@/utils/formFieldMeta'

type FieldRow = FieldDefinition & {
  __uid?: number
  fieldNameTouched?: boolean
  autoFieldName?: string
}

interface UseTableEditorOptions {
  functionUnitId: number
  store: {
    updateTable: (functionUnitId: number, tableId: number, requestData: any) => Promise<TableDefinition | null>
  }
  selectedTable: Ref<TableDefinition | null>
  tableNameTouched: Ref<boolean>
  validateName: (name: string) => boolean
  existingTableNames: (excludeId?: number) => string[]
  assertTableNameAvailable: (tableName: string, excludeTableId?: number) => Promise<boolean>
  loadTables: () => Promise<void> | void
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Editing surface for a single selected table: field row normalization,
 * name auto-slug syncing, add/remove/reorder fields, PK handling, and save.
 */
export function useTableEditor(options: UseTableEditorOptions) {
  const {
    functionUnitId, store, selectedTable, tableNameTouched,
    validateName, existingTableNames, assertTableNameAvailable, loadTables, t,
  } = options

  const hasDecimalFields = computed(() => {
    return selectedTable.value?.fieldDefinitions?.some(f => f.dataType === 'DECIMAL') ?? false
  })

  let _fieldUidCounter = 0
  let _autoSyncingFieldName = false

  function normalizeFieldRow(f: FieldDefinition): FieldRow {
    const row = { ...f } as FieldRow
    row.__uid = row.__uid ?? ++_fieldUidCounter
    row.autoFieldName = row.fieldName || suggestFieldName(row.displayName || '', [])
    // Only lock auto-generation when the field already has a persisted technical name.
    row.fieldNameTouched = !!(row.id && row.fieldName?.trim())
    row.isForeignKey = row.isForeignKey || false
    row.refPrimaryKeyFields = row.refPrimaryKeyFields || []
    row.fkDisplayMode = row.fkDisplayMode || 'readonly'
    row.pkGeneration = row.pkGeneration ?? row.pkGenerationJson
    if (row.isPrimaryKey && !row.pkGeneration) {
      row.pkGeneration = { strategy: 'uuid' }
    }
    return row
  }

  function existingFieldNames(excludeIndex?: number): string[] {
    if (!selectedTable.value) return []
    return selectedTable.value.fieldDefinitions
      .map((f, i) => (excludeIndex === i ? '' : f.fieldName))
      .filter(Boolean)
  }

  function onTableDisplayNameInput() {
    if (!selectedTable.value || tableNameTouched.value) return
    selectedTable.value.tableName = suggestTableName(
      selectedTable.value.tableDisplayName || '',
      existingTableNames(selectedTable.value.id),
    )
  }

  function onTableNameManualInput() {
    tableNameTouched.value = true
  }

  function handleSelectTable(row: TableDefinition) {
    tableNameTouched.value = !!row.id
    selectedTable.value = {
      ...row,
      fieldDefinitions: [...(row.fieldDefinitions || []).map(f => normalizeFieldRow(f))],
    }
  }

  function onPrimaryKeyChange(row: FieldRow, checked: boolean) {
    if (!checked) {
      row.pkGeneration = undefined
      return
    }
    if (!row.pkGeneration) {
      row.pkGeneration = { strategy: 'uuid' }
    }
  }

  function onFieldDisplayNameInput(row: FieldRow, index: number) {
    if (row.fieldNameTouched) return
    const suggested = suggestFieldName(row.displayName || '', existingFieldNames(index))
    _autoSyncingFieldName = true
    row.fieldName = suggested
    row.autoFieldName = suggested
    nextTick(() => {
      _autoSyncingFieldName = false
    })
  }

  function onFieldNameManualInput(row: FieldRow) {
    if (_autoSyncingFieldName) return
    row.fieldNameTouched = !!row.fieldName?.trim()
  }

  function handleBackToList() {
    selectedTable.value = null
    tableNameTouched.value = false
  }

  function handleAddField() {
    if (!selectedTable.value) return
    selectedTable.value.fieldDefinitions.push({
      __uid: ++_fieldUidCounter,
      fieldName: '',
      dataType: 'VARCHAR',
      length: 255,
      nullable: true,
      isPrimaryKey: false,
      isForeignKey: false,
      refPrimaryKeyFields: [],
      fkDisplayMode: 'readonly',
      displayName: '',
      fieldNameTouched: false,
    } as FieldRow)
  }

  function handleRemoveField(index: number) {
    if (!selectedTable.value) return
    selectedTable.value.fieldDefinitions.splice(index, 1)
  }

  /**
   * Move a field up in the list (swap with previous).
   * Exported for testing via assignSortOrder.
   */
  function moveFieldUp(index: number) {
    if (!selectedTable.value || index <= 0) return
    const fields = selectedTable.value.fieldDefinitions
    const temp = fields[index]
    fields[index] = fields[index - 1]
    fields[index - 1] = temp
    // Trigger reactivity
    selectedTable.value.fieldDefinitions = [...fields]
  }

  function moveFieldDown(index: number) {
    if (!selectedTable.value || index >= selectedTable.value.fieldDefinitions.length - 1) return
    const fields = selectedTable.value.fieldDefinitions
    const temp = fields[index]
    fields[index] = fields[index + 1]
    fields[index + 1] = temp
    selectedTable.value.fieldDefinitions = [...fields]
  }

  async function handleSaveTable() {
    if (!selectedTable.value) return
    // MAIN tables must configure Request ID before saving — block with a popup.
    if (selectedTable.value.tableType === 'MAIN' && !hasRequestIdConfig(selectedTable.value.requestIdConfig)) {
      await ElMessageBox.alert(
        t('table.requestId.requiredHint'),
        t('table.requestId.requiredTitle'),
        { type: 'warning', confirmButtonText: t('common.confirm') },
      ).catch(() => {})
      return
    }
    // Validate table name
    if (!validateName(selectedTable.value.tableName)) {
      ElMessage.warning(t('table.invalidTableName'))
      return
    }
    if (!await assertTableNameAvailable(selectedTable.value.tableName, selectedTable.value.id)) {
      return
    }
    // Validate field names
    const invalidField = selectedTable.value.fieldDefinitions.find(f => f.fieldName && !validateName(f.fieldName))
    if (invalidField) {
      ElMessage.warning(t('table.invalidFieldName', { name: invalidField.fieldName }))
      return
    }
    // Validate PK-Nullable constraint
    const pkFields = selectedTable.value.fieldDefinitions.filter(
      f => f.isPrimaryKey && f.fieldName && f.fieldName.trim()
    )
    if (pkFields.length === 1) {
      // 只有一个主键：该字段的Nullable必须为未勾选
      if (pkFields[0].nullable !== false) {
        ElMessage.warning(t('table.pkNotNullable'))
        return
      }
    } else if (pkFields.length >= 2) {
      // 联合主键：至少有一个主键字段的Nullable不能勾选
      const notNullableCount = pkFields.filter(f => f.nullable !== true).length
      if (notNullableCount === 0) {
        ElMessage.warning(t('table.compositePkNotNullable'))
        return
      }
    }
    try {
      // 转换数据格式：将 fieldDefinitions 转换为 fields
      // 后端期望的是 TableDefinitionRequest，包含 fields 而不是 fieldDefinitions
      const fields = (selectedTable.value.fieldDefinitions || [])
        .filter(f => f.fieldName && f.fieldName.trim()) // 过滤空字段名
        .map((f: any, index: number) => ({
          // Preserve original id so backend can diff fieldName / description (Display Name)
          // and propagate renames to Form Designer rule + fieldPermissions.
          id: f.id,
          fieldName: f.fieldName,
          dataType: f.dataType, // 确保 dataType 是有效的枚举值
          length: f.length,
          precision: f.precision,
          scale: f.scale,
          nullable: f.nullable !== undefined ? f.nullable : true,
          defaultValue: f.defaultValue,
          isPrimaryKey: f.isPrimaryKey || false,
          displayName: f.displayName,
          isForeignKey: f.isForeignKey || false,
          refTableId: f.refTableId,
          refPrimaryKeyFields: f.refPrimaryKeyFields,
          pkGeneration: serializePkGeneration(f.pkGeneration, f.isPrimaryKey),
          fkDisplayMode: f.fkDisplayMode || 'readonly',
          relationCardinality: f.relationCardinality,
          sortOrder: index
        }))

      const requestData = {
        tableName: selectedTable.value.tableName,
        tableDisplayName: selectedTable.value.tableDisplayName,
        tableType: selectedTable.value.tableType,
        description: selectedTable.value.description,
        // Request ID 仅对 MAIN 表有意义;其它表类型不持久化该配置
        requestIdConfig: selectedTable.value.tableType === 'MAIN'
          ? (selectedTable.value.requestIdConfig ?? null)
          : null,
        fields: fields
      }

      console.log('[TableDesigner] Saving table with fields:', {
        tableId: selectedTable.value.id,
        tableName: requestData.tableName,
        fieldCount: fields.length,
        fields: fields,
        requestData: JSON.stringify(requestData, null, 2)
      })

      const result = await store.updateTable(functionUnitId, selectedTable.value.id, requestData)
      console.log('[TableDesigner] Save result:', result)
      console.log('[TableDesigner] Result fieldDefinitions:', result?.fieldDefinitions?.length || 0)
      console.log('[TableDesigner] Result fieldDefinitions array:', result?.fieldDefinitions)

      // 更新当前选中的表，使用返回的数据
      // result 已经是 TableDefinition（store.updateTable 返回 res.data，而 res 是 ApiResponse）
      if (result) {
        selectedTable.value = {
          ...result,
          fieldDefinitions: [...(result.fieldDefinitions || []).map(f => normalizeFieldRow(f))]
        }
        console.log('[TableDesigner] Updated selected table after save with', selectedTable.value.fieldDefinitions?.length || 0, 'fields')
      } else {
        console.warn('[TableDesigner] Save result is null or undefined')
      }

      ElMessage.success(t('common.success'))

      // Delay loading list to ensure transaction is committed
      setTimeout(() => {
      loadTables()
      }, 500)
    } catch (e: any) {
      console.error('[TableDesigner] Save failed:', e)
      ElMessage.error(e.response?.data?.message || t('common.error'))
    }
  }

  return {
    hasDecimalFields,
    normalizeFieldRow,
    onTableDisplayNameInput,
    onTableNameManualInput,
    handleSelectTable,
    onPrimaryKeyChange,
    onFieldDisplayNameInput,
    onFieldNameManualInput,
    handleBackToList,
    handleAddField,
    handleRemoveField,
    moveFieldUp,
    moveFieldDown,
    handleSaveTable,
  }
}
