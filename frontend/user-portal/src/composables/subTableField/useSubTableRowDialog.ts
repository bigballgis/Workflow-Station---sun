import { computed, ref, watch, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  extractUserIdFromCellValue,
  mergeFormRowWithSeed,
  normalizeSubTableColumns,
} from '@/components/subTableAddDialogHelpers'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'
import {
  applyFkPresentationToDialogColumns,
  buildRowAddContext,
  finalizeSubTableRowOnSave,
  prepareSubTableAddRow,
  toFieldFkMetas,
  type AllocatePrimaryKeysFn,
} from '@/utils/subTableRowRuntime'
import { unwrapPortalApiPayload, resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import { processApi } from '@/api/process'
import type { SubTableFieldEmit, SubTableFieldProps, SubTableFieldT } from './subTableFieldTypes'

/** Add/Edit row dialog state machine + FK/PK runtime bridge (prepare / finalize on save). */
export function useSubTableRowDialog(
  props: SubTableFieldProps,
  rows: Ref<any[]>,
  emit: SubTableFieldEmit,
  t: SubTableFieldT,
  deps: {
    applyAssigneeDisplayNameToRow: (row: Record<string, any>, previousAssigneeId?: string) => Record<string, any>
    performSubTableRowAssignment: (rowIndex: number, assigneeId: string, opts?: { fromEditDialog?: boolean }) => Promise<boolean>
  },
) {
  const { applyAssigneeDisplayNameToRow, performSubTableRowAssignment } = deps

  // Dialog state
  const dialogVisible = ref(false)
  const dialogMode = ref<'add' | 'edit'>('add')
  const editingRowIndex = ref<number | null>(null)
  const dialogInitialData = ref<Record<string, any> | undefined>(undefined)

  const dialogSourceColumns = computed(() =>
    (props.dialogColumns?.length ? props.dialogColumns : props.columns),
  )

  const editableColumns = computed(() =>
    normalizeSubTableColumns(
      dialogSourceColumns.value.filter(col => col.type !== 'linkForm'),
      rows.value,
    ),
  )

  const dialogAddColumns = ref<DialogColumn[] | null>(null)

  const listViewColumnsForAudit = computed(() =>
    normalizeSubTableColumns(
      props.columns.filter(col => col.type !== 'linkForm'),
      rows.value,
    ),
  )

  const subTableDialogColumns = computed(() => {
    if (dialogAddColumns.value) return dialogAddColumns.value
    const base = editableColumns.value
    const fkMetas = toFieldFkMetas(props.fieldDefinitions)
    if (!fkMetas.length) return base
    return applyFkPresentationToDialogColumns(base, fkMetas, props.fieldDefinitions).visibleColumns
  })

  function handleAdd() {
    void openAddRowDialog()
  }

  function createAllocatePrimaryKeysFn(): AllocatePrimaryKeysFn | undefined {
    if (!props.functionUnitId || props.tableId == null) return undefined
    return async (payload) => {
      const res = await processApi.allocatePrimaryKeys(props.functionUnitId!, payload, props.taskId)
      const body = unwrapPortalApiPayload(res) as { values?: string[] }
      return body?.values ?? []
    }
  }

  async function openAddRowDialog() {
    if (!props.editable) return
    const rowAddContext = buildRowAddContext(
      props.primaryFormData ?? {},
      props.subTableBindingsForContext ?? props.linkedSubTableBindings,
      props.parentRow,
      props.parentTableId,
    )
    try {
      const result = await prepareSubTableAddRow({
        columns: editableColumns.value,
        fieldDefinitions: props.fieldDefinitions,
        rowAddContext,
        tableId: props.tableId,
        tableDisplayName: props.title,
        primaryTableDisplayName: props.primaryTableDisplayName,
        primaryTableId: props.primaryTableId,
        parentTablesById: props.parentTablesById,
        functionUnitId: props.functionUnitId,
        autoEnsurePrimaryRecord: props.primaryFormData != null,
        deferPkAllocationUntilSave: true,
        bindingLinkMode: props.bindingLinkMode,
        bindingForeignKeyField: props.bindingForeignKeyField,
        primaryKeyFields: props.primaryKeyFields,
        miParticipantRowId: props.miParticipantRowId,
        miParentParticipantRow: props.miParentParticipantRow,
        miParentTableId: props.miParentTableId,
        allocatePrimaryKeys: createAllocatePrimaryKeysFn(),
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
      dialogMode.value = 'add'
      dialogInitialData.value = result.initialRow as Record<string, any>
      editingRowIndex.value = null
      dialogVisible.value = true
    } catch (e) {
      ElMessage.error(resolveUserFacingHttpMessage(e, t('common.operationFailed')))
    }
  }

  watch(dialogVisible, (open) => {
    if (!open) dialogAddColumns.value = null
  })

  function openEditDialog(i: number) {
    dialogMode.value = 'edit'
    editingRowIndex.value = i
    dialogInitialData.value = { ...rows.value[i] }
    dialogVisible.value = true
  }

  async function handleDialogSave(rowData: Record<string, any>) {
    // Add: prefer-filled seed merge keeps PK/FK when empty inputs omit them.
    // Edit: dialog values are authoritative — including intentional clears ('' / null).
    // Re-running mergeFormRowWithSeed against the pre-edit snapshot restores cleared fields.
    let savedRow =
      dialogMode.value === 'edit'
        ? ({ ...(dialogInitialData.value || {}), ...rowData } as Record<string, any>)
        : mergeFormRowWithSeed(dialogInitialData.value, rowData)
    if (dialogMode.value === 'add') {
      const allocate = createAllocatePrimaryKeysFn()
      if (allocate && props.tableId != null && props.fieldDefinitions?.length) {
        const rowAddContext = buildRowAddContext(
          props.primaryFormData ?? {},
          props.subTableBindingsForContext ?? props.linkedSubTableBindings,
          props.parentRow,
          props.parentTableId,
        )
        const result = await finalizeSubTableRowOnSave({
          row: savedRow,
          fieldDefinitions: props.fieldDefinitions,
          rowAddContext,
          tableId: Number(props.tableId),
          allocatePrimaryKeys: allocate,
          functionUnitId: props.functionUnitId,
          parentTablesById: props.parentTablesById,
          primaryTableId: props.primaryTableId,
          primaryTableDisplayName: props.primaryTableDisplayName,
          tableDisplayName: props.title,
          autoEnsurePrimaryRecord: props.primaryFormData != null,
          bindingLinkMode: props.bindingLinkMode,
          bindingForeignKeyField: props.bindingForeignKeyField,
          primaryKeyFields: props.primaryKeyFields,
          miParticipantRowId: props.miParticipantRowId,
          miParentParticipantRow: props.miParentParticipantRow,
          miParentTableId: props.miParentTableId,
          t,
        })
        if (!result.ok) {
          throw new Error(result.message)
        }
        savedRow = result.row
        if (result.primaryFormDataPatch && Object.keys(result.primaryFormDataPatch).length > 0) {
          emit('update:primaryFormData', result.primaryFormDataPatch)
        }
      }
      rows.value.push(savedRow)
    } else if (dialogMode.value === 'edit' && editingRowIndex.value !== null) {
      const idx = editingRowIndex.value
      const prevRow = rows.value[idx] as Record<string, any> | undefined
      const af = props.assigneeField
      const prevAssigneeId = af && prevRow ? extractUserIdFromCellValue(prevRow[af]) : ''
      if (af) {
        savedRow = applyAssigneeDisplayNameToRow(savedRow, prevAssigneeId)
      }
      // updated_at / updated_by already refreshed by the dialog save funnel (useSubTableDialogForm)
      rows.value[idx] = savedRow
      emit('update:modelValue', [...rows.value])

      const newAssigneeId = af ? extractUserIdFromCellValue(savedRow[af]) : ''
      if (
        props.canAssign &&
        props.showAssignButton &&
        props.taskId &&
        af &&
        newAssigneeId &&
        newAssigneeId !== prevAssigneeId
      ) {
        await performSubTableRowAssignment(idx, newAssigneeId, { fromEditDialog: true })
      }
      return
    }
    emit('update:modelValue', [...rows.value])
  }

  async function deleteRow(i: number) {
    await ElMessageBox.confirm(t('subTable.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    rows.value.splice(i, 1)
    emit('update:modelValue', [...rows.value])
  }

  return {
    dialogVisible,
    dialogMode,
    editingRowIndex,
    dialogInitialData,
    subTableDialogColumns,
    listViewColumnsForAudit,
    handleAdd,
    openEditDialog,
    handleDialogSave,
    deleteRow
  }
}
