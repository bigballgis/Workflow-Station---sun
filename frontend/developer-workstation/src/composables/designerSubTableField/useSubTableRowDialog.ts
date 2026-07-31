import { computed, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mergeFormRowWithSeed, type DialogColumn } from '@/components/designer/subTableAddDialogHelpers'
import {
  alignUploadFieldsToColumns,
  normalizeUploadFieldsInRow,
} from '@/components/designer/uploadFieldUtils'
import { collectUploadRulesFromTree } from '@/utils/formDesigner'
import type { PreviewSubTableDialogHost } from '@/components/designer/previewSubTableDialog'
import { functionUnitApi } from '@/api/functionUnit'
import {
  applyFkPresentationToDialogColumns,
  buildRowAddContext,
  prepareSubTableAddRow,
  toFieldFkMetas,
} from '@/utils/subTableRowRuntime'
import type { ColumnConfig, SubTableFieldEmit, SubTableFieldProps } from './types'

interface UseSubTableRowDialogOptions {
  props: SubTableFieldProps
  emit: SubTableFieldEmit
  displayColumns: ComputedRef<ColumnConfig[]>
  tableData: Ref<any[]>
  total: Ref<number>
  previewDialogHost: PreviewSubTableDialogHost | null
  setLinkFormDialogVisible: (value: boolean) => void
  rememberUploadNamesForRow: (rowIndex: number, rowData: Record<string, any>) => void
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * 子表行的添加/编辑弹层编排与增删改：根据是否存在 formRule 选择 form-create
 * 或简单弹层，preview 走 FormDesigner 顶层弹层；保存时归一上传字段并 emit。
 */
export function useSubTableRowDialog(options: UseSubTableRowDialogOptions) {
  const {
    props,
    emit,
    displayColumns,
    tableData,
    total,
    previewDialogHost,
    setLinkFormDialogVisible,
    rememberUploadNamesForRow,
    t,
  } = options

  // Dialog state - 使用两个独立的 dialog 来避免状态冲突
  const formDialogVisible = ref(false)
  const simpleDialogVisible = ref(false)
  const dialogMode = ref<'add' | 'edit'>('add')
  const editingRowIndex = ref<number | null>(null)
  const dialogInitialData = ref<Record<string, any> | undefined>(undefined)
  const dialogAddColumns = ref<DialogColumn[] | null>(null)

  // 是否使用 form-create 对话框（当有 formRule 时优先使用）
  const hasFormRule = computed(() => props.formRule && props.formRule.length > 0)

  function toDialogColumn(col: ColumnConfig): DialogColumn {
    // 将旧的 'input' type 映射到 'text'
    const type = col.type === 'input' ? 'text' : (col.type as DialogColumn['type'])
    return {
      field: col.field,
      label: col.label,
      type,
      required: col.required,
      ...(col.rules?.length ? { rules: col.rules } : {}),
      placeholder: col.placeholder,
      minWidth: col.minWidth,
      options: col.options,
      props: col.props,
      readonly: (col as { readonly?: boolean }).readonly,
      ...(col.sourceRule ? { sourceRule: col.sourceRule } : {}),
      ...((col as { defaultValue?: unknown }).defaultValue !== undefined
        ? { defaultValue: (col as { defaultValue?: unknown }).defaultValue }
        : {}),
    }
  }

  // 将 ColumnConfig 转换为 DialogColumn（兼容 SubTableAddDialog 的类型）
  const dialogColumns = computed<DialogColumn[]>(() => {
    const source = dialogAddColumns.value ?? displayColumns.value
    return source.map(toDialogColumn)
  })

  // 添加/编辑行 — preview 走 FormDesigner 顶层弹层，避免嵌在 Preview Dialog 内被遮罩挡住
  async function openRowDialog(mode: 'add' | 'edit', index?: number) {
    dialogMode.value = mode
    editingRowIndex.value = mode === 'edit' && index != null ? index : null
    dialogAddColumns.value = null

    if (mode === 'add') {
      const fkMetas = toFieldFkMetas(props.config.fieldDefinitions)
      const baseCols = fkMetas.length
        ? applyFkPresentationToDialogColumns(
            displayColumns.value.map(toDialogColumn),
            fkMetas,
            props.config.fieldDefinitions,
          ).visibleColumns
        : undefined

      const rowAddContext = buildRowAddContext(
        props.primaryFormData ?? {},
        props.previewTableBindings,
      )
      try {
        const result = await prepareSubTableAddRow({
          columns: baseCols ?? dialogColumns.value,
          fieldDefinitions: props.config.fieldDefinitions,
          rowAddContext,
          tableId: props.config.tableId,
          tableDisplayName: props.config.title,
          primaryTableDisplayName: props.primaryTableDisplayName,
          primaryTableId: props.primaryTableId,
          parentTablesById: props.parentTablesById,
          functionUnitId: props.functionUnitId != null ? String(props.functionUnitId) : undefined,
          autoEnsurePrimaryRecord: props.primaryFormData != null,
          bindingLinkMode: props.config.bindingLinkMode,
          bindingForeignKeyField: props.config.bindingForeignKeyField,
          allocatePrimaryKeys:
            props.functionUnitId != null && props.config.tableId != null
              ? async (payload) => {
                  const res = await functionUnitApi.allocatePrimaryKeys(props.functionUnitId!, payload)
                  return res?.data?.values ?? []
                }
              : undefined,
          t,
        })
        if (!result.ok) {
          ElMessage.warning(result.message)
          return
        }
        if (result.primaryFormDataPatch && Object.keys(result.primaryFormDataPatch).length > 0) {
          emit('update:primaryFormData', result.primaryFormDataPatch)
        }
        dialogAddColumns.value = result.dialogColumns
        dialogInitialData.value = result.initialRow as Record<string, any>
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : t('common.error')
        ElMessage.error(message || t('common.error'))
        return
      }
    } else {
      dialogInitialData.value =
        index != null ? { ...tableData.value[index] } : undefined
    }

    if (previewDialogHost) {
      setLinkFormDialogVisible(false)
      previewDialogHost.openRowDialog({
        mode,
        title: props.config.title || t('subTable.defaultTitle'),
        initialData: dialogInitialData.value,
        formRule: props.formRule,
        formOption: props.formOption,
        columns: dialogColumns.value,
        assignmentConfig: props.assignmentConfig,
        onSave: (rowData) => handleDialogSave(rowData),
      })
      return
    }

    setLinkFormDialogVisible(false)
    formDialogVisible.value = false
    simpleDialogVisible.value = false
    window.setTimeout(() => {
      if (hasFormRule.value) {
        formDialogVisible.value = true
      } else {
        simpleDialogVisible.value = true
      }
    }, 0)
  }

  function handleAdd() {
    void openRowDialog('add')
  }

  // 编辑行 — 打开 Dialog 并预填数据
  function openEditDialog(index: number) {
    void openRowDialog('edit', index)
  }

  // Dialog 保存回调
  function handleDialogSave(rowData: Record<string, any>) {
    const savedRow = mergeFormRowWithSeed(dialogInitialData.value, rowData)
    if (hasFormRule.value && props.formRule?.length) {
      const uploadRuleFields = collectUploadRulesFromTree(props.formRule).map((r) => r.field)
      alignUploadFieldsToColumns(savedRow, displayColumns.value, uploadRuleFields)
    }
    normalizeUploadFieldsInRow(savedRow, displayColumns.value)
    if (dialogMode.value === 'add') {
      const rowIndex = tableData.value.length
      tableData.value.push(savedRow)
      rememberUploadNamesForRow(rowIndex, savedRow)
      emit('add', savedRow)
    } else if (dialogMode.value === 'edit' && editingRowIndex.value !== null) {
      tableData.value[editingRowIndex.value] = savedRow
      rememberUploadNamesForRow(editingRowIndex.value, savedRow)
      emit('edit', savedRow, editingRowIndex.value)
    }
    total.value = tableData.value.length
    emit('update:modelValue', [...tableData.value])
    formDialogVisible.value = false
    simpleDialogVisible.value = false
  }

  // 删除行
  async function handleDelete(index: number) {
    await ElMessageBox.confirm(t('subTable.deleteConfirm'), t('common.confirmTitle'), { type: 'warning' })
    const deletedRow = tableData.value[index]
    tableData.value.splice(index, 1)
    total.value = tableData.value.length
    emit('update:modelValue', [...tableData.value])
    emit('delete', deletedRow, index)
    ElMessage.success(t('common.deleteSuccess'))
  }

  return {
    formDialogVisible,
    simpleDialogVisible,
    dialogMode,
    dialogInitialData,
    hasFormRule,
    dialogColumns,
    openRowDialog,
    handleAdd,
    openEditDialog,
    handleDialogSave,
    handleDelete,
  }
}
