import { ref, computed, watch, type Ref, type ComputedRef } from 'vue'
import { type FormInstance, type FormRules } from 'element-plus'
import { type TableBinding, type TableBindingRequest, type TableDefinition, type BindingType, type BindingLinkMode } from '@/api/functionUnit'
import { relationTableBindingApi, type RelationTableDTO } from '@/api/relationTable'

interface UseTableBindingFormOptions {
  getTables: () => TableDefinition[]
  bindings: Ref<TableBinding[]>
  restrictPrimarySubOnly: ComputedRef<boolean>
  tableTypeLabel: (type: string) => string
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Add/Edit 绑定表单状态与逻辑：
 * 表单字段、校验规则、可选表过滤、外键/链接模式联动、编辑/重置。
 */
export function useTableBindingForm(options: UseTableBindingFormOptions) {
  const { getTables, bindings, restrictPrimarySubOnly, tableTypeLabel, t } = options

  const submitting = ref(false)
  const showAddDialog = ref(false)
  const editingBinding = ref<TableBinding | null>(null)
  const formRef = ref<FormInstance>()
  const deployedRelationTables = ref<RelationTableDTO[]>([])

  function toRelationTableOptionId(tableId: number): number {
    return tableId < 0 ? tableId : -tableId
  }

  function makeEmptyBindingForm(): TableBindingRequest {
    // 默认类型：若尚无 PRIMARY 就让用户先建 PRIMARY，否则默认 SUB
    const defaultType: BindingType = bindings.value.some(b => b.bindingType === 'PRIMARY') ? 'SUB' : 'PRIMARY'
    return {
      tableId: undefined as unknown as number,
      bindingType: defaultType,
      bindingMode: defaultType === 'PRIMARY' ? 'EDITABLE' : 'READONLY',
      foreignKeyField: undefined,
      bindingLinkMode: defaultType === 'SUB' ? 'structuralFk' : undefined,
      subMode: defaultType === 'SUB' ? 'FULL' : undefined
    }
  }

  const bindingForm = ref<TableBindingRequest>(makeEmptyBindingForm())

  const formRules = computed<FormRules>(() => {
    const base: FormRules = {
      tableId: [{ required: true, message: t('tableBinding.selectTableRequired'), trigger: 'change' }],
      bindingType: [{ required: true, message: t('tableBinding.selectBindingTypeRequired'), trigger: 'change' }],
      bindingMode: [{ required: true, message: t('tableBinding.selectBindingModeRequired'), trigger: 'change' }]
    }
    if (restrictPrimarySubOnly.value && bindingForm.value.bindingType === 'SUB') {
      if (bindingForm.value.bindingLinkMode === 'miParticipantRow') {
        base.foreignKeyField = [
          { required: true, message: t('tableBinding.foreignKeyRequired'), trigger: 'change' }
        ]
      } else if (structuralFkFieldNames.value.length === 0) {
        base.foreignKeyField = [
          { required: true, message: t('tableBinding.structuralFkRequired'), trigger: 'change' }
        ]
      }
    }
    return base
  })

  // Whether a primary binding already exists
  const hasPrimaryBinding = computed(() => {
    return bindings.value.some(b => b.bindingType === 'PRIMARY')
  })

  /** 对 PROCESS / TASK 表单：SUB 必须在有 PRIMARY 之后才能添加（后端 SUB_REQUIRES_PRIMARY）。
   *  RELATED 不受此限制（它是 Lookup 参考数据，与主从关系无关）。 */
  const needsPrimaryFirst = computed(
    () => restrictPrimarySubOnly.value && !hasPrimaryBinding.value
  )

  // Filtered tables based on binding type — 严格按类型过滤，避免用户在 SUB 里看到 MAIN 再报错
  const filteredAvailableTables = computed(() => {
    const bt = bindingForm.value.bindingType
    if (bt === 'PRIMARY') {
      return getTables()
        .filter(t => t.tableType === 'MAIN')
        .map(t => ({ id: t.id, displayLabel: `${t.tableDisplayName || t.tableName} (${tableTypeLabel(t.tableType)})`, fieldDefinitions: t.fieldDefinitions }))
    }
    if (bt === 'SUB') {
      return getTables()
        .filter(t => t.tableType === 'SUB')
        .map(t => ({ id: t.id, displayLabel: `${t.tableDisplayName || t.tableName} (${tableTypeLabel(t.tableType)})`, fieldDefinitions: t.fieldDefinitions }))
    }
    // RELATED：列出本功能单元的 RELATION 表 + 管理中心已部署的关联表
    const localRelation = getTables()
      .filter(t => t.tableType === 'RELATION')
      .map(t => ({ id: t.id, displayLabel: `${t.tableDisplayName || t.tableName} (${tableTypeLabel(t.tableType)})`, fieldDefinitions: t.fieldDefinitions }))
    const remote = deployedRelationTables.value.map(r => ({
      id: toRelationTableOptionId(r.id), // negative ID to distinguish from local tables
      displayLabel: `${r.displayName || r.tableName}`,
      fieldDefinitions: r.fieldDefinitions || []
    }))
    return [...localRelation, ...remote]
  })

  const selectTablePlaceholder = computed(() => {
    if (needsPrimaryFirst.value) return t('tableBinding.primaryFirstHint')
    return t('tableBinding.selectTablePlaceholder')
  })

  const emptyTableListHint = computed(() => {
    const bt = bindingForm.value.bindingType
    if (bt === 'PRIMARY') return t('tableBinding.noMainTableAvailable')
    if (bt === 'SUB') return t('tableBinding.noSubTableAvailable')
    if (bt === 'RELATED') return t('tableBinding.noRelationTableAvailable')
    return t('tableBinding.selectBindingTypeFirst')
  })

  // Fields of the selected table
  const selectedTableFields = computed(() => {
    if (!bindingForm.value.tableId) return []
    if (bindingForm.value.tableId > 0) {
      const table = getTables().find(t => t.id === bindingForm.value.tableId)
      return table?.fieldDefinitions || []
    }
    const table = deployedRelationTables.value.find(t => toRelationTableOptionId(t.id) === bindingForm.value.tableId)
    return table?.fieldDefinitions || []
  })

  const structuralFkFieldNames = computed(() =>
    selectedTableFields.value.filter(f => (f as { isForeignKey?: boolean }).isForeignKey).map(f => f.fieldName),
  )

  function bindingLinkModeLabel(mode?: BindingLinkMode | string | null): string {
    if (mode === 'miParticipantRow') return t('tableBinding.linkModeMiParticipantRow')
    return t('tableBinding.linkModeStructuralFk')
  }

  function suggestParticipantRowField() {
    const pk = selectedTableFields.value.find(f => f.isPrimaryKey)
    if (pk?.fieldName) {
      bindingForm.value.foreignKeyField = pk.fieldName
    }
  }

  // Check if table is already bound
  function isTableBound(tableId: number): boolean {
    if (editingBinding.value?.tableId === tableId) return false
    return bindings.value.some(b => {
      if (bindingForm.value.bindingType === 'RELATED' && b.bindingType === 'RELATED') {
        return toRelationTableOptionId(b.tableId) === tableId
      }
      return b.tableId === tableId
    })
  }

  // Handle binding type change - reset table selection
  function handleBindingTypeChange() {
    bindingForm.value.tableId = undefined as unknown as number
    bindingForm.value.foreignKeyField = undefined
    if (bindingForm.value.bindingType === 'SUB') {
      bindingForm.value.subMode = bindingForm.value.subMode || 'FULL'
      bindingForm.value.bindingLinkMode = bindingForm.value.bindingLinkMode || 'structuralFk'
    } else {
      bindingForm.value.subMode = undefined
      bindingForm.value.bindingLinkMode = undefined
    }
    // RELATED type must be READONLY
    if (bindingForm.value.bindingType === 'RELATED') {
      bindingForm.value.bindingMode = 'READONLY'
    }
    if (
      bindingForm.value.bindingType === 'RELATED'
      && deployedRelationTables.value.length === 0
    ) {
      loadDeployedRelationTables()
    }
  }

  // Load deployed relation tables from admin center
  async function loadDeployedRelationTables() {
    try {
      const res = await relationTableBindingApi.getAvailableTables()
      deployedRelationTables.value = res.data || []
    } catch (e: any) {
      console.error('Failed to load deployed relation tables:', e)
      deployedRelationTables.value = []
    }
  }

  // Handle table selection change — 仅调整 bindingMode 默认值，bindingType 由用户主动选择，
  // 这样 filteredAvailableTables 已经保证了 tableType 与 bindingType 一致，不会再出现后端报错
  function handleTableSelect(_tableId?: number) {
    const bt = bindingForm.value.bindingType
    if (bt === 'PRIMARY') {
      bindingForm.value.bindingMode = 'EDITABLE'
    } else {
      bindingForm.value.bindingMode = 'READONLY'
    }
    // Re-derive the link field from the newly selected table (a field name from the previous table may
    // not exist here). Only on user table change — handleEdit sets tableId programmatically without this.
    if (bt === 'SUB') {
      if (bindingForm.value.bindingLinkMode === 'miParticipantRow') {
        suggestParticipantRowField()
      } else if (bindingForm.value.bindingLinkMode === 'structuralFk') {
        bindingForm.value.foreignKeyField = structuralFkFieldNames.value[0] || undefined
      }
    }
  }

  // Edit binding
  function handleEdit(binding: TableBinding) {
    editingBinding.value = binding
    bindingForm.value = {
      tableId: binding.tableId,
      bindingType: binding.bindingType,
      bindingMode: binding.bindingMode,
      foreignKeyField: binding.foreignKeyField,
      bindingLinkMode: binding.bindingType === 'SUB' ? (binding.bindingLinkMode || 'structuralFk') : undefined,
      sortOrder: binding.sortOrder,
      subMode: binding.bindingType === 'SUB' ? (binding.subMode || 'FULL') : undefined
    }
    showAddDialog.value = true
  }

  // Reset form
  function resetForm() {
    editingBinding.value = null
    bindingForm.value = makeEmptyBindingForm()
    formRef.value?.resetFields()
  }

  // 打开 Add 对话框前根据当前 bindings 刷新默认值，并预加载已部署的关联表（RELATED 选项可能被切换到）
  watch(showAddDialog, (open) => {
    if (open) {
      if (!editingBinding.value) {
        bindingForm.value = makeEmptyBindingForm()
      }
      if (deployedRelationTables.value.length === 0) {
        loadDeployedRelationTables()
      }
    }
  })

  /**
   * User clicked the Link Mode radio. MI Participant Row auto-fills Participant Row Field with the table
   * PK (the field that identifies each MI participant) — always, so switching from Structural FK replaces
   * the carried-over FK value. Only fires on user interaction (not on programmatic {@link handleEdit} load,
   * which must keep the persisted value). Mirrors the structuralFk reset.
   */
  function handleBindingLinkModeChange(mode: BindingLinkMode | string | number | boolean | undefined) {
    if (bindingForm.value.bindingType !== 'SUB') return
    if (mode === 'miParticipantRow') {
      suggestParticipantRowField()
    } else if (mode === 'structuralFk') {
      bindingForm.value.foreignKeyField = structuralFkFieldNames.value[0] || undefined
    }
  }

  return {
    submitting,
    showAddDialog,
    editingBinding,
    formRef,
    deployedRelationTables,
    bindingForm,
    formRules,
    hasPrimaryBinding,
    needsPrimaryFirst,
    filteredAvailableTables,
    selectTablePlaceholder,
    emptyTableListHint,
    selectedTableFields,
    structuralFkFieldNames,
    toRelationTableOptionId,
    bindingLinkModeLabel,
    isTableBound,
    handleBindingTypeChange,
    handleBindingLinkModeChange,
    loadDeployedRelationTables,
    handleTableSelect,
    handleEdit,
    resetForm,
  }
}
