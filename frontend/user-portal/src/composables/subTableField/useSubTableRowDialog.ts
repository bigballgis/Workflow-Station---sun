import { computed, ref, watch, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  extractUserIdFromCellValue,
  mergeFormRowWithSeed,
  normalizeSubTableColumns,
} from '@/components/subTableAddDialogHelpers'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'
import {
  applyFieldPermissionsToDialogColumns,
  applyFkPresentationToDialogColumns,
  buildRowAddContext,
  finalizeSubTableRowOnSave,
  prepareSubTableAddRow,
  toFieldFkMetas,
  type AllocatePrimaryKeysFn,
} from '@/utils/subTableRowRuntime'
import { unwrapPortalApiPayload, resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import { processApi } from '@/api/process'
import { isAssignmentConfigured } from '@/utils/miAssignmentConfig'
import { sameSubTableRow } from '@/composables/tasks/shared'
import type { SubTableFieldEmit, SubTableFieldProps, SubTableFieldT } from './subTableFieldTypes'

/** Add/Edit row dialog state machine + FK/PK runtime bridge (prepare / finalize on save). */
export function useSubTableRowDialog(
  props: SubTableFieldProps,
  rows: Ref<any[]>,
  emit: SubTableFieldEmit,
  t: SubTableFieldT,
  deps: {
    applyAssigneeDisplayNameToRow: (row: Record<string, any>, previousAssigneeId?: string) => Record<string, any>
    performSubTableRowAssignment: (rowIndex: number, assigneeId: string) => Promise<boolean>
  },
) {
  const { applyAssigneeDisplayNameToRow, performSubTableRowAssignment } = deps

  // Dialog state
  const dialogVisible = ref(false)
  const dialogMode = ref<'add' | 'edit'>('add')
  const editingRowIndex = ref<number | null>(null)
  /** 打开编辑弹窗时那一行的快照——用来在保存时按身份找回它，而不是相信下标。 */
  const editingRowSnapshot = ref<Record<string, any> | null>(null)
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
    const permissioned = (cols: DialogColumn[]) =>
      applyFieldPermissionsToDialogColumns(cols, props.bindingId, props.fieldPermissions)
    if (dialogAddColumns.value) return permissioned(dialogAddColumns.value)
    const base = editableColumns.value
    const fkMetas = toFieldFkMetas(props.fieldDefinitions)
    if (!fkMetas.length) return permissioned(base)
    return permissioned(
      applyFkPresentationToDialogColumns(base, fkMetas, props.fieldDefinitions).visibleColumns,
    )
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
      editingRowSnapshot.value = null
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
    // 记住这一行**本身**，而不只是它的下标：下标会因为删除 / 重新排序 / 重新 hydrate 而指向别人。
    // 实测（task 9c46d613）：两行 age 都是 kk，删掉一行后另一行的值变成了 u —— 保存写回时
    // `rows.value[idx]` 已经不是当初打开的那一行了。
    editingRowSnapshot.value = { ...rows.value[i] }
    dialogInitialData.value = { ...rows.value[i] }
    dialogVisible.value = true
  }

  /**
   * 当初打开编辑弹窗的那一行现在在哪。
   *
   * <p>优先按设计器主键匹配（{@link sameSubTableRow}）；匹配不到才退回下标，
   * 且下标必须仍然指向「同一行」——否则宁可不写，也不要覆盖别人的行。
   */
  function resolveEditingRowIndex(): number {
    const snap = editingRowSnapshot.value
    const idx = editingRowIndex.value
    if (snap) {
      const pk = props.primaryKeyFields ?? null
      const byIdentity = rows.value.findIndex(r => sameSubTableRow(r, snap, pk))
      if (byIdentity >= 0) return byIdentity
    }
    if (idx != null && idx >= 0 && idx < rows.value.length) {
      // 没有可用身份时才按下标写回（例如全新行还没分配主键）。
      if (!snap || sameSubTableRow(rows.value[idx], snap, props.primaryKeyFields ?? null)) return idx
    }
    return -1
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
          parentTableId: props.parentTableId,
          t,
        })
        if (!result.ok) {
          throw new Error(result.message)
        }
        savedRow = result.row
        if (result.primaryFormDataPatch && Object.keys(result.primaryFormDataPatch).length > 0) {
          emit('update:primaryFormData', result.primaryFormDataPatch)
        }
        // Nested sub-table: hand the parent row's freshly allocated PK back to the host dialog
        // so the parent saves with the same key this child's FK now points at.
        if (result.parentRowPatch && Object.keys(result.parentRowPatch).length > 0) {
          emit('update:parentRow', result.parentRowPatch)
        }
      }
      rows.value.push(savedRow)
    } else if (dialogMode.value === 'edit' && editingRowIndex.value !== null) {
      const idx = resolveEditingRowIndex()
      if (idx < 0) {
        // 当初编辑的那一行已经不在了（被删掉 / 被别的保存改写）。按下标硬写会改到别人的行，
        // 那正是「删掉一个 kk，另一个 kk 变成 u」的成因 —— 宁可提示重试。
        ElMessage.warning(t('common.operationFailed'))
        dialogVisible.value = false
        return
      }
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
      // Sub-tables with a BPMN assignmentConfig (JSON-row assignment model, e.g. ACQ Transaction)
      // persist the assignee via the normal row edit + task form submit — never through the
      // legacy per-row engine endpoint below, which assumes a physical table + numeric rowId and
      // 400s ("Task is not configured with sub-table information") on JSON-only sub-tables.
      if (
        props.canAssign &&
        props.showAssignButton &&
        props.taskId &&
        af &&
        newAssigneeId &&
        newAssigneeId !== prevAssigneeId &&
        !isAssignmentConfigured(props.assignmentConfig)
      ) {
        await performSubTableRowAssignment(idx, newAssigneeId)
      }
      return
    }
    emit('update:modelValue', [...rows.value])
  }

  /**
   * Layer for a confirm raised from this grid.
   *
   * Element Plus assigns MessageBox a z-index from its own global counter, which knows nothing
   * about the hand-rolled overlays this app draws — the Link Form modal sits at 5000. A People
   * grid inside that modal therefore raised its delete confirm UNDERNEATH it: the row never
   * disappeared and the page looked frozen behind an invisible modal.
   *
   * Measure the overlays actually on screen and go above the highest, same rule
   * SubTableAddDialog follows for itself.
   */
  function confirmZIndex(): number {
    const selectors = '.el-overlay, .el-dialog__wrapper, .el-popper, .link-form-modal-overlay'
    let highest = 0
    for (const el of document.querySelectorAll<HTMLElement>(selectors)) {
      if (el.offsetParent === null && getComputedStyle(el).position !== 'fixed') continue
      const z = Number.parseInt(getComputedStyle(el).zIndex || '', 10)
      if (Number.isFinite(z)) highest = Math.max(highest, z)
    }
    // 0 → nothing stacked; let Element Plus pick as before.
    return highest > 0 ? highest + 2 : 0
  }

  /**
   * Raise the confirm to `z`. `zIndex` is NOT part of ElMessageBoxOptions — it lives on the
   * box's internal state and is ignored when passed in — so the layer is applied through a
   * `customClass` plus a rule injected for it, and both are cleaned up once the box closes.
   */
  const CONFIRM_LAYER_CLASS = 'sub-table-confirm-above-modal'

  function applyConfirmLayer(z: number): () => void {
    const style = document.createElement('style')
    // `customClass` lands on `.el-message-box`, but the element that actually forms the
    // stacking context is its `.el-overlay.is-message-box` ancestor (with an
    // `.el-overlay-message-box` wrapper in between). Raise that ancestor — styling only the
    // box itself leaves the whole overlay behind the modal it was opened from.
    style.textContent = `.el-overlay.is-message-box:has(.${CONFIRM_LAYER_CLASS}) { z-index: ${z} !important; }`
    document.head.appendChild(style)
    return () => style.remove()
  }

  /**
   * @param i   el-table 的渲染下标（仅在拿不到行身份时作为兜底）
   * @param row 被点击的那一行本身。**优先按身份定位**：下标来自渲染序号，
   *            确认框是异步的，用户确认期间数组可能已经变化（另一处保存、重新 hydrate），
   *            此时 splice(i,1) 会删掉别人的行。实测（task 9c46d613）两行 age 同为 `kk`，
   *            删掉其中一行后另一行的值变成了 `u` —— 删错了行、值也就跟着错位。
   */
  async function deleteRow(i: number, row?: Record<string, any>) {
    const z = confirmZIndex()
    const cleanup = z > 0 ? applyConfirmLayer(z) : null
    try {
      await ElMessageBox.confirm(t('subTable.deleteConfirm'), t('common.confirm'), {
        type: 'warning',
        ...(z > 0 ? { customClass: CONFIRM_LAYER_CLASS } : {}),
      })
    } finally {
      cleanup?.()
    }
    let idx = -1
    if (row) {
      // 先按引用找（同一个对象），再按设计器主键找。
      idx = rows.value.indexOf(row)
      if (idx < 0) {
        idx = rows.value.findIndex(r => sameSubTableRow(r, row, props.primaryKeyFields ?? null))
      }
    }
    if (idx < 0) {
      // 拿不到身份时才退回下标，且下标必须仍在范围内。
      if (i < 0 || i >= rows.value.length) return
      idx = i
    }
    rows.value.splice(idx, 1)
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
