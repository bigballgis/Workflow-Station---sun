import { computed, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { AssignSubTableRowResponse } from '@/api/task'
import { assignSubTableRow } from '@/api/task'
import {
  pickHttpErrorBodyMessage,
  unwrapPortalApiPayload,
  resolveUserFacingHttpMessage
} from '@/utils/httpErrorMessage'
import { extractUserIdFromCellValue, unwrapUserLikeValueToDisplayString } from '@/components/subTableAddDialogHelpers'
import type { SubTableFieldEmit, SubTableFieldProps, SubTableFieldT } from './subTableFieldTypes'

export function formatAssigneeDisplayLabel(raw: unknown): string {
  if (raw == null || raw === '') return ''
  if (typeof raw === 'string' || typeof raw === 'number') return String(raw)
  return unwrapUserLikeValueToDisplayString(raw)
}

export function resolveDisplayNameFromAssigneeCell(raw: unknown): string | undefined {
  if (raw == null) return undefined
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    const s = unwrapUserLikeValueToDisplayString(raw)
    if (s && s !== '-') return s
  }
  return undefined
}

/** MI sub-table row assignment: user picker dialog, row PK recovery and assignment API flow. */
export function useSubTableAssignment(
  props: SubTableFieldProps,
  rows: Ref<any[]>,
  emit: SubTableFieldEmit,
  t: SubTableFieldT,
  deps: { resolveSubTableRowPk: (row: Record<string, unknown> | null | undefined) => string | number | null },
) {
  const { resolveSubTableRowPk } = deps

  // Assignment functionality.
  // Row assignment is driven solely by the row Edit dialog's assignee field — there is no
  // standalone user-picker dialog, so no picker visibility / selection / user-search state here.
  const assigning = ref(false)
  const userNameCache = ref<Record<string, string>>({})

  // Assignee column: show when assign buttons are active, OR when data already has assignee values (read-only completed tasks)
  const showAssigneeColumn = computed(() => {
    if (props.showAssignButton && props.assigneeField) return true
    if (!props.assigneeField) return false
    return rows.value.some(r =>
      r && (r.assignee_display_name || resolveRowAssigneeCell(r as Record<string, unknown>))
    )
  })

  function getUserDisplayName(userId: unknown): string {
    if (userId == null || userId === '') return ''
    if (typeof userId === 'object' && !Array.isArray(userId)) {
      return unwrapUserLikeValueToDisplayString(userId)
    }
    const sid = String(userId)
    if (userNameCache.value[sid]) return userNameCache.value[sid]
    return sid.startsWith('user-') ? sid.substring(5) : sid
  }

  /** Resolve assignee cell when BPMN assigneeField differs from designer column (e.g. assignee_user_id vs assignee). */
  function resolveRowAssigneeCell(row: Record<string, unknown> | null | undefined): unknown {
    if (!row) return undefined
    const af = props.assigneeField
    if (af && row[af] != null && String(row[af]).trim() !== '') return row[af]
    for (const key of ['assignee', 'assignee_user_id', 'assignee_id']) {
      const raw = row[key]
      if (raw == null) continue
      if (typeof raw === 'string' && raw.trim() === '') continue
      return raw
    }
    return undefined
  }

  /** Keep {@code assignee_display_name} aligned with the assignee field (Edit dialog / lookup object / cache). */
  function applyAssigneeDisplayNameToRow(
    row: Record<string, any>,
    previousAssigneeId?: string,
  ): Record<string, any> {
    const af = props.assigneeField
    if (!af) return row
    const raw = row[af]
    const sid = extractUserIdFromCellValue(raw)
    if (!sid) {
      const next = { ...row }
      delete next.assignee_display_name
      return next
    }
    const sidChanged = previousAssigneeId !== undefined && sid !== previousAssigneeId
    let displayName = resolveDisplayNameFromAssigneeCell(raw)
    if (!displayName && !sidChanged) {
      const existing = row.assignee_display_name
      if (existing != null && String(existing).trim() !== '') {
        displayName = String(existing).trim()
      }
    }
    if (!displayName) {
      displayName = userNameCache.value[sid]
    }
    if (displayName) {
      userNameCache.value = { ...userNameCache.value, [sid]: displayName }
      const next = { ...row, assignee_display_name: displayName }
      if (af) {
        next[af] = sid
      }
      return next
    }
    if (sidChanged) {
      const next = { ...row }
      delete next.assignee_display_name
      return next
    }
    return row
  }

  function applyAssignmentResultToRow(rowIndex: number, result: AssignSubTableRowResponse) {
    if (!props.assigneeField) return
    const targetRow = rows.value[rowIndex]
    if (!targetRow) return
    targetRow[props.assigneeField] = result.assigneeId
    const rawDisplay = result.assigneeName ?? result.assigneeId
    const displayName =
      typeof rawDisplay === 'string' || typeof rawDisplay === 'number'
        ? String(rawDisplay)
        : unwrapUserLikeValueToDisplayString(rawDisplay)
    targetRow.assignee_display_name = displayName
    userNameCache.value[String(result.assigneeId)] = displayName
    emit('update:modelValue', [...rows.value])
    emit('assignmentChanged')
  }

  /**
   * Assign a row to a user. Invoked from the row Edit dialog's save funnel — the dialog owns the
   * success feedback, so this only surfaces failures.
   */
  async function performSubTableRowAssignment(
    rowIndex: number,
    assigneeId: string,
  ): Promise<boolean> {
    const row = rows.value[rowIndex] as Record<string, unknown> | undefined
    if (!row || !props.taskId) return false

    const rowPk = resolveSubTableRowPk(row)
    const rowKeyRaw = row.rowKey
    let rowKeyForAssign =
      rowKeyRaw && typeof rowKeyRaw === 'object' && !Array.isArray(rowKeyRaw)
        ? (rowKeyRaw as Record<string, unknown>)
        : undefined

    const rowIdNum = rowPk != null ? Number(rowPk) : NaN

    // Designer single-column PK that isn't numeric (e.g. 'ACQ-DC-PW-TRANS-000018'): the row is
    // already identified, just not by a bigint path rowId. Send it as a composite rowKey.
    if (rowKeyForAssign == null && rowPk != null && Number.isNaN(rowIdNum)) {
      const pks = props.primaryKeyFields
      if (Array.isArray(pks) && pks.length === 1) {
        rowKeyForAssign = { [pks[0]!]: rowPk }
      }
    }

    // Assignment requires the row's primary key to target the update. Without it there is no
    // legacy identity-lookup fallback — surface the gap so the table design / row data is fixed.
    if (rowKeyForAssign == null && Number.isNaN(rowIdNum)) {
      ElMessage.error(t('subTable.assignmentMissingRowKey'))
      return false
    }

    assigning.value = true
    try {
      let response: unknown
      if (rowKeyForAssign != null && Object.keys(rowKeyForAssign).length > 0) {
        response = await assignSubTableRow(props.taskId, 0, assigneeId, rowKeyForAssign)
      } else {
        response = await assignSubTableRow(props.taskId, rowIdNum, assigneeId)
      }

      const result = unwrapPortalApiPayload<AssignSubTableRowResponse>(response)
      const assigneePresent =
        result != null &&
        result.assigneeId != null &&
        String(result.assigneeId).trim().length > 0
      const ok =
        result != null &&
        result.success !== false &&
        (result.success === true || assigneePresent)

      if (ok && result) {
        applyAssignmentResultToRow(rowIndex, result)
        return true
      }

      const r = result as Record<string, unknown> | null
      const hint =
        (r && typeof r.errorMessage === 'string' && r.errorMessage.trim()) ||
        (r && typeof r.message === 'string' && r.message.trim()) ||
        t('subTable.assignmentFailed')
      ElMessage.error(hint)
      return false
    } catch (error: unknown) {
      console.error('Failed to assign sub-table row:', error)
      const ax = error as { response?: { status?: number; data?: unknown }; message?: string }
      const msg =
        pickHttpErrorBodyMessage(ax.response?.data) ||
        resolveUserFacingHttpMessage(error, t) ||
        (typeof ax.message === 'string' && ax.message.trim().length > 0 ? ax.message.trim() : undefined) ||
        t('subTable.assignmentFailed')
      ElMessage.error(msg)
      return false
    } finally {
      assigning.value = false
    }
  }

  return {
    assigning,
    userNameCache,
    showAssigneeColumn,
    getUserDisplayName,
    resolveRowAssigneeCell,
    applyAssigneeDisplayNameToRow,
    performSubTableRowAssignment,
  }
}
