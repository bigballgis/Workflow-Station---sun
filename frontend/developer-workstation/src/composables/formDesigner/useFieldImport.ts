import { computed, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FieldDefinition, FormDefinition, RequestIdConfig, TableBinding } from '@/api/functionUnit'
import { isRequestIdRule, buildRequestIdSyntheticField } from '@/utils/formFieldMeta'
import { functionUnitApi } from '@/api/functionUnit'
import { relationTableBindingApi } from '@/api/relationTable'
import type { SubTableFieldDTO } from '@/api/subTableView'
import { injectUploadButtonLabels } from '@/utils/formDesigner'
import type { SubTableListColumnDTO } from './useSubTableViews'

interface UseFieldImportOptions {
  functionUnitId: number
  store: {
    tables: any[]
    fetchTables: (functionUnitId: number) => Promise<unknown>
  }
  selectedForm: Ref<FormDefinition | null>
  designerRef: Ref<any>
  subDesignerRefs: Ref<any[]>
  designerSubBindings: ComputedRef<Array<{ bindingId: number; bindingType: string; tableId: number; tableName: string }>>
  activeDesignerTab: Ref<string>
  relationViewState: Ref<Record<number, { allFields: any[]; viewFields: any[] }>>
  subTableViewState: Ref<Record<number, { allFields: SubTableFieldDTO[]; viewFields: SubTableListColumnDTO[] }>>
  getSubTableListViewBaseColumns: (bindingId: number) => SubTableListColumnDTO[]
  appendSubTableListFieldColumns: (existing: SubTableListColumnDTO[], newFields: SubTableFieldDTO[]) => SubTableListColumnDTO[]
  mapFieldsToFormRules: (fields: FieldDefinition[], requestIdConfig?: RequestIdConfig | null) => any[]
  getRequestIdConfigByTableId: (tableId?: number | null) => RequestIdConfig | null
  mergeTaskPermissionsForFields: (fields: FieldDefinition[]) => void
  refreshFormRulesFromTableMetadata: () => void
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * "Import fields from table" dialog state and actions for FormDesigner:
 * table selection, field multi-select, and importing into the main designer,
 * a sub-table designer/list view, or a relation table view.
 */
export function useFieldImport(options: UseFieldImportOptions) {
  const {
    functionUnitId, store, selectedForm, designerRef, subDesignerRefs, designerSubBindings,
    activeDesignerTab, relationViewState, subTableViewState,
    getSubTableListViewBaseColumns, appendSubTableListFieldColumns,
    mapFieldsToFormRules, getRequestIdConfigByTableId, mergeTaskPermissionsForFields,
    refreshFormRulesFromTableMetadata, t,
  } = options

  // Import fields state
  const showImportFieldsDialog = ref(false)
  const importTableId = ref<number | null>(null)
  const selectedImportFields = ref<FieldDefinition[]>([])
  const formBindings = ref<TableBinding[]>([])
  // Relation table fields loaded from API (for RELATION type table import)
  const relationTableFields = ref<FieldDefinition[]>([])

  // Check if the currently selected import table is a RELATION type table
  function isImportingRelationTable(): boolean {
    if (!importTableId.value) return false
    // Check store.tables first (for local RELATION type tables)
    const table = store.tables.find(t => t.id === importTableId.value)
    if (table?.tableType === 'RELATION') return true
    // Check formBindings (for deployed relation tables where tableId is from rt_table_definitions)
    const binding = formBindings.value.find(b => b.tableId === importTableId.value)
    return binding?.bindingType === 'RELATED'
  }

  // Check if the currently selected import table is a SUB type table
  function isImportingSubTable(): boolean {
    if (!importTableId.value) return false
    // Check store.tables for SUB type tables
    const table = store.tables.find(t => t.id === importTableId.value)
    if (table?.tableType === 'SUB') return true
    // Check formBindings for SUB type bindings
    const binding = formBindings.value.find(b => b.tableId === importTableId.value)
    return binding?.bindingType === 'SUB'
  }

  // Get bindingId for a given tableId (for relation table imports)
  // Checks formBindings first, then designerSubBindings, then falls back to active tab
  function getBindingIdForTable(tableId: number): number | null {
    // Check formBindings (populated in handleImportFieldsToDesigner)
    const fromFormBindings = formBindings.value.find(b => b.tableId === tableId)
    if (fromFormBindings) return fromFormBindings.id as number

    // Check designerSubBindings (from selectedForm.tableBindings)
    const fromSubBindings = designerSubBindings.value.find(b => b.tableId === tableId)
    if (fromSubBindings) return fromSubBindings.bindingId

    // Fallback: if importing from the active RELATED tab, use active tab's bindingId
    if (activeDesignerTab.value !== 'main') {
      return Number(activeDesignerTab.value)
    }

    return null
  }

  // Computed: available fields for selected table
  const availableFields = computed(() => {
    if (!importTableId.value) return []
    // If importing from a relation table, use the fetched relation fields
    if (isImportingRelationTable() && relationTableFields.value.length > 0) {
      return relationTableFields.value
    }
    const table = store.tables.find(t => t.id === importTableId.value)
    const fields = table?.fieldDefinitions ? [...table.fieldDefinitions] : []
    // Importing into the MAIN designer from a table that configures Request ID:
    // expose Request ID as a checkable virtual field at the top of the list.
    if (
      activeDesignerTab.value === 'main' &&
      getRequestIdConfigByTableId(importTableId.value) != null
    ) {
      fields.unshift(buildRequestIdSyntheticField(t('form.requestId')))
    }
    return fields
  })

  // Computed: all fields selected
  const isAllFieldsSelected = computed(() => {
    return availableFields.value.length > 0 &&
           selectedImportFields.value.length === availableFields.value.length
  })

  // Computed: indeterminate selection state
  const isFieldsIndeterminate = computed(() => {
    return selectedImportFields.value.length > 0 &&
           selectedImportFields.value.length < availableFields.value.length
  })

  // Get binding info for the currently selected import table
  function getImportTableBinding(): TableBinding | undefined {
    if (!importTableId.value) return undefined
    return formBindings.value.find(b => b.tableId === importTableId.value)
  }

  /**
   * Check if a field is selected
   */
  function isFieldSelected(fieldName: string): boolean {
    return selectedImportFields.value.some(f => f.fieldName === fieldName)
  }

  /**
   * Toggle field selection
   */
  function toggleFieldSelection(field: FieldDefinition) {
    const index = selectedImportFields.value.findIndex(f => f.fieldName === field.fieldName)
    if (index >= 0) {
      selectedImportFields.value.splice(index, 1)
    } else {
      selectedImportFields.value.push({ ...field })
    }
  }

  /**
   * Select/deselect all fields
   */
  function handleSelectAllFields(checked: boolean) {
    if (checked) {
      selectedImportFields.value = availableFields.value.map((f: FieldDefinition) => ({ ...f }))
    } else {
      selectedImportFields.value = []
    }
  }

  /**
   * Reset selected fields when table changes
   */
  async function handleTableChange() {
    selectedImportFields.value = []
    relationTableFields.value = []
    // If the selected table is a RELATION type, fetch its fields from the relation table API
    if (isImportingRelationTable()) {
      try {
        const res = await relationTableBindingApi.getAvailableTables()
        const tables = res.data || []
        // For deployed relation tables, importTableId is the rt_table_definitions ID
        // Try direct ID match first, then fall back to name match
        let rtTable = tables.find((t: any) => t.id === importTableId.value)
        if (!rtTable) {
          const selectedTable = store.tables.find(t => t.id === importTableId.value)
          if (selectedTable) {
            rtTable = tables.find((t: any) => t.tableName === selectedTable.tableName || t.displayName === selectedTable.tableName)
          }
        }
        if (rtTable?.fieldDefinitions) {
          relationTableFields.value = rtTable.fieldDefinitions.map((f: any) => ({
            fieldName: f.fieldName,
            dataType: f.dataType,
            length: f.length,
            precision: f.precision,
            scale: f.scale,
            nullable: f.nullable,
            isPrimaryKey: f.isPrimaryKey,
            defaultValue: f.defaultValue,
            displayName: f.displayName,
          } as FieldDefinition))
        }
      } catch {
        relationTableFields.value = []
      }
    }
  }

  /**
   * Open import fields dialog (from list page)
   */
  async function handleImportFromTable() {
    await store.fetchTables(functionUnitId)
    formBindings.value = []
    importTableId.value = null
    selectedImportFields.value = []
    showImportFieldsDialog.value = true
  }

  /**
   * Open import fields dialog (from designer page)
   */
  async function handleImportFieldsToDesigner() {
    await store.fetchTables(functionUnitId)

    // Load form bindings
    if (selectedForm.value) {
      try {
        const res = await functionUnitApi.getFormBindings(functionUnitId, selectedForm.value.id)
        formBindings.value = res.data || []
      } catch (e) {
        formBindings.value = []
      }

      // If a non-primary tab (SUB or RELATED) is active, default to that tab's table
      if (activeDesignerTab.value !== 'main') {
        const bindingId = Number(activeDesignerTab.value)
        const subBinding = designerSubBindings.value.find(b => b.bindingId === bindingId)
        if (subBinding) {
          if (subBinding.bindingType === 'RELATED') {
            // RELATED tables live in dw_table_definitions with RELATION type — match by name
            const dwTable = store.tables.find(t => t.tableName === subBinding.tableName || t.tableDisplayName === subBinding.tableName)
            importTableId.value = dwTable ? dwTable.id : (subBinding.tableId ?? null)
          } else {
            // SUB (and any other) bindings reference a dw_table_definitions id directly
            importTableId.value = subBinding.tableId ?? null
          }
          selectedImportFields.value = []
          relationTableFields.value = []
          showImportFieldsDialog.value = true
          // Fetch relation table fields if applicable
          await handleTableChange()
          return
        }
      }

      // Auto-select primary table if bound
      const primaryBinding = formBindings.value.find(b => b.bindingType === 'PRIMARY')
      if (primaryBinding) {
        importTableId.value = primaryBinding.tableId
      } else if (selectedForm.value.boundTableId) {
        importTableId.value = selectedForm.value.boundTableId
      } else {
        importTableId.value = null
      }
    } else {
      formBindings.value = []
      importTableId.value = null
    }

    selectedImportFields.value = []
    relationTableFields.value = []
    showImportFieldsDialog.value = true
  }

  /**
   * Confirm importing fields to form designer
   */
  async function handleConfirmImportFields() {
    if (selectedImportFields.value.length === 0) {
      ElMessage.warning(t('form.selectAtLeastOne'))
      return
    }

    if (selectedForm.value) {
      // Check if importing into a relation table
      if (isImportingRelationTable()) {
        // Convert selected fields to RelationFieldDTO format and pass directly to the view
        const relationFields = selectedImportFields.value.map((f, idx) => ({
          id: idx,
          fieldName: f.fieldName,
          dataType: f.dataType || 'VARCHAR',
          length: f.length,
          precision: f.precision,
          scale: f.scale,
          nullable: f.nullable ?? true,
          isPrimaryKey: f.isPrimaryKey ?? false,
          defaultValue: f.defaultValue,
          displayName: f.displayName,
          sortOrder: idx,
        }))
        // Convert ALL available fields (not just selected) for the left panel
        const allRelationFields = availableFields.value.map((f: FieldDefinition, idx: number) => ({
          id: idx,
          fieldName: f.fieldName,
          dataType: f.dataType || 'VARCHAR',
          length: f.length,
          precision: f.precision,
          scale: f.scale,
          nullable: f.nullable ?? true,
          isPrimaryKey: f.isPrimaryKey ?? false,
          defaultValue: f.defaultValue,
          displayName: f.displayName,
          sortOrder: idx,
        }))
        // Update relation view state directly
        // Always update based on the imported table's binding, regardless of current tab
        const bindingId = importTableId.value
          ? getBindingIdForTable(importTableId.value)
          : Number(activeDesignerTab.value)
        if (bindingId) {
          relationViewState.value = {
            ...relationViewState.value,
            [bindingId]: { allFields: allRelationFields, viewFields: relationFields }
          }
        }
        ElMessage.success(t('form.importedSuccess', { count: selectedImportFields.value.length }))
        showImportFieldsDialog.value = false
        return
      }

      // Check if importing into a sub table - update both form designer and list view
      // Skip sub-table routing when the main tab is active: the user is explicitly
      // importing into the main form canvas, even if the selected table happens to be
      // of SUB type (e.g. a miParticipantRow binding without a PRIMARY binding).
      if (activeDesignerTab.value !== 'main' && isImportingSubTable()) {
        // Update sub-table list view state
        const subFields = selectedImportFields.value.map((f) => ({
          fieldName: f.fieldName,
          dataType: f.dataType || 'VARCHAR',
          displayName: f.displayName || f.fieldName,
        })) as SubTableFieldDTO[]
        const allSubFields = availableFields.value.map((f: FieldDefinition) => ({
          fieldName: f.fieldName,
          dataType: f.dataType || 'VARCHAR',
          displayName: f.displayName || f.fieldName,
        })) as SubTableFieldDTO[]

        const bindingId = importTableId.value
          ? getBindingIdForTable(importTableId.value)
          : Number(activeDesignerTab.value)
        // Also import to sub-table form designer
        const rules = mapFieldsToFormRules(selectedImportFields.value)
        mergeTaskPermissionsForFields(selectedImportFields.value)

        // Find target sub designer ref
        let targetRef: any = null
        if (bindingId) {
          const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
          if (index >= 0) targetRef = subDesignerRefs.value[index]
        }

        if (targetRef) {
          const currentRules: any[] = targetRef.getRule() || []
          const existingFields = new Set(currentRules.map((r: any) => r.field))
          const newRules = rules.filter(r => !existingFields.has(r.field))
          const duplicateCount = rules.length - newRules.length

          if (duplicateCount > 0) {
            ElMessage.warning(t('form.skipExisting', { count: duplicateCount }))
          }

          if (newRules.length > 0) {
            const merged = [...currentRules, ...newRules]
            injectUploadButtonLabels(merged, t('form.clickToUpload'))
            targetRef.setRule(merged)
          }
        }

        if (bindingId) {
          subTableViewState.value = {
            ...subTableViewState.value,
            [bindingId]: {
              allFields: allSubFields,
              viewFields: getSubTableListViewBaseColumns(bindingId),
            },
          }
        }

        refreshFormRulesFromTableMetadata()

        ElMessage.success(t('form.importedSuccess', { count: selectedImportFields.value.length }))
        showImportFieldsDialog.value = false
        return
      }

      // Request ID (when checked) rides along in selectedImportFields as a virtual
      // field and is turned into a readonly rule by fieldToFormRule.
      const rules = mapFieldsToFormRules(selectedImportFields.value)
      mergeTaskPermissionsForFields(selectedImportFields.value)

      // Determine target designer: active sub tab or main
      let targetRef: any = null
      if (activeDesignerTab.value !== 'main') {
        const bindingId = Number(activeDesignerTab.value)
        const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
        if (index >= 0) targetRef = subDesignerRefs.value[index]
      }
      // Fall back to main designer if no sub ref found
      if (!targetRef) targetRef = designerRef.value

      if (targetRef) {
        const currentRules: any[] = targetRef.getRule() || []
        const hasRequestIdAlready = currentRules.some(isRequestIdRule)
        const existingFields = new Set(currentRules.map((r: any) => r.field))
        // Skip fields already on canvas; also skip a duplicate Request ID rule.
        const newRules = rules.filter(
          r => !existingFields.has(r.field) && !(hasRequestIdAlready && isRequestIdRule(r)),
        )
        const duplicateCount = rules.length - newRules.length

        if (duplicateCount > 0) {
          ElMessage.warning(t('form.skipExisting', { count: duplicateCount }))
        }

        if (newRules.length > 0) {
          const merged = [...currentRules, ...newRules]
          injectUploadButtonLabels(merged, t('form.clickToUpload'))
          targetRef.setRule(merged)
          ElMessage.success(t('form.importedSuccess', { count: newRules.length }))
        }
      }
    } else {
      ElMessage.info(t('form.selectOrCreateForm'))
    }

    showImportFieldsDialog.value = false
  }

  return {
    showImportFieldsDialog,
    importTableId,
    selectedImportFields,
    formBindings,
    relationTableFields,
    availableFields,
    isAllFieldsSelected,
    isFieldsIndeterminate,
    getImportTableBinding,
    isFieldSelected,
    toggleFieldSelection,
    handleSelectAllFields,
    handleTableChange,
    handleImportFromTable,
    handleImportFieldsToDesigner,
    handleConfirmImportFields,
  }
}
