import { computed, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { AssignSubTableRowResponse } from '@/api/task'
import { assignSubTableRow, assignSubTableRowByIdentity, getSubTableData, getTaskDetail } from '@/api/task'
import {
  pickHttpErrorBodyMessage,
  unwrapPortalApiPayload,
  resolveUserFacingHttpMessage
} from '@/utils/httpErrorMessage'
import { userApi } from '@/api/user'
import { extractUserIdFromCellValue, unwrapUserLikeValueToDisplayString } from '@/components/subTableAddDialogHelpers'
import type { SubTableFieldEmit, SubTableFieldProps, SubTableFieldT } from './subTableFieldTypes'
import { sameValue } from './useSubTableRowKeys'

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

  // Assignment functionality
  const assignDialogVisible = ref(false)
  const selectedAssigneeId = ref('')
  const currentAssignRow = ref<any>(null)
  const currentAssignRowIndex = ref<number | null>(null)
  const assigning = ref(false)
  const userOptions = ref<any[]>([])
  const userSearchLoading = ref(false)
  const userNameCache = ref<Record<string, string>>({})

  // Assignee column: show when assign buttons are active, OR when data already has assignee values (read-only completed tasks)
  const showAssigneeColumn = computed(() => {
    if (props.showAssignButton && props.assigneeField) return true
    if (!props.assigneeField) return false
    return rows.value.some(r =>
      r && (r.assignee_display_name || resolveRowAssigneeCell(r as Record<string, unknown>))
    )
  })

  function openAssignDialog(row: any, rowIndex: number) {
    currentAssignRow.value = row
    currentAssignRowIndex.value = rowIndex
    selectedAssigneeId.value = extractUserIdFromCellValue(row[props.assigneeField || ''] as unknown) || ''
    assignDialogVisible.value = true
  }

  function onAssignDialogOpened() {
    searchUsers('')
  }

  async function searchUsers(keyword: string) {
    userSearchLoading.value = true
    try {
      const result = await userApi.searchUsers(keyword || '')
      userOptions.value = [...result]
      // Cache user names
      result.forEach((user: any) => {
        userNameCache.value[user.id] = user.name
      })
    } catch (e) {
      console.error('Failed to search users:', e)
      userOptions.value = []
    } finally {
      userSearchLoading.value = false
    }
  }

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

  async function resolveMissingRowIdFromServer(
    taskId: string,
    localRow: Record<string, unknown>,
    rowIndex: number | null
  ): Promise<number | null> {
    try {
      const response = await getSubTableData(taskId)
      const payload = (response as Record<string, unknown>).data as Record<string, unknown> | undefined
      const rowsFromServer = Array.isArray(payload?.rows) ? (payload!.rows as Record<string, unknown>[]) : []
      if (!rowsFromServer.length) return null

      const byEmail = rowsFromServer.find(r => sameValue(r.email, localRow.email))
      const byNameAndDept = rowsFromServer.find(
        r => sameValue(r.name, localRow.name) && sameValue(r.department, localRow.department)
      )
      const byIndex =
        rowIndex != null && rowIndex >= 0 && rowIndex < rowsFromServer.length
          ? rowsFromServer[rowIndex]
          : null
      const match = byEmail || byNameAndDept || byIndex || null
      if (!match) return null

      const pk = resolveSubTableRowPk(match)
      const rowId = pk != null ? Number(pk) : NaN
      return Number.isNaN(rowId) ? null : rowId
    } catch (error: unknown) {
      return null
    }
  }

  async function resolveMissingRowIdFromTaskDetail(
    taskId: string,
    localRow: Record<string, unknown>,
    rowIndex: number | null
  ): Promise<{
    rowId: number | null
    effectiveTaskId?: string
    meetingHints?: { topic?: string; location?: string; organizerName?: string }
  }> {
    try {
      const detailRes = await getTaskDetail(taskId)
      const detail = (detailRes as Record<string, unknown>).data as Record<string, unknown> | undefined
      const effectiveTaskId =
        detail && typeof detail.taskId === 'string' && detail.taskId.trim().length > 0 ? detail.taskId : taskId
      const vars = (detail?.variables as Record<string, unknown> | undefined) || {}
      const subTables = (vars.__subTables__ as Record<string, unknown> | undefined) || {}
      const allRows: Record<string, unknown>[] = []
      Object.values(subTables).forEach(v => {
        if (Array.isArray(v)) {
          v.forEach(r => {
            if (r && typeof r === 'object') allRows.push(r as Record<string, unknown>)
          })
        }
      })
      const meetingHints = {
        topic: typeof vars.topic === 'string' ? vars.topic : undefined,
        location: typeof vars.location === 'string' ? vars.location : undefined,
        organizerName: typeof vars.organizer_name === 'string' ? vars.organizer_name : undefined
      }
      if (!allRows.length) return { rowId: null, effectiveTaskId, meetingHints }
      const byEmail = allRows.find(r => sameValue(r.email, localRow.email))
      const byNameAndDept = allRows.find(
        r => sameValue(r.name, localRow.name) && sameValue(r.department, localRow.department)
      )
      const byIndex = rowIndex != null && rowIndex >= 0 && rowIndex < allRows.length ? allRows[rowIndex] : null
      const match = byEmail || byNameAndDept || byIndex || null
      if (!match) return { rowId: null, effectiveTaskId, meetingHints }
      const pk = resolveSubTableRowPk(match)
      const rowId = pk != null ? Number(pk) : NaN
      return { rowId: Number.isNaN(rowId) ? null : rowId, effectiveTaskId, meetingHints }
    } catch {
      return { rowId: null, effectiveTaskId: taskId }
    }
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

  async function performSubTableRowAssignment(
    rowIndex: number,
    assigneeId: string,
    opts?: { fromEditDialog?: boolean },
  ): Promise<boolean> {
    const row = rows.value[rowIndex] as Record<string, unknown> | undefined
    if (!row || !props.taskId) {
      if (!opts?.fromEditDialog) {
        ElMessage.error(t('subTable.assignmentFailed'))
      }
      return false
    }

    const rowPk = resolveSubTableRowPk(row)
    const rowKeyRaw = row.rowKey
    const rowKeyForAssign =
      rowKeyRaw && typeof rowKeyRaw === 'object' && !Array.isArray(rowKeyRaw)
        ? (rowKeyRaw as Record<string, unknown>)
        : undefined

    let effectiveTaskId = props.taskId
    let meetingHints: { topic?: string; location?: string; organizerName?: string } | undefined
    let rowIdNum = rowPk != null ? Number(rowPk) : NaN
    if (props.taskId && (rowPk == null || Number.isNaN(rowIdNum))) {
      let recovered = await resolveMissingRowIdFromServer(props.taskId, row, rowIndex)
      if (recovered == null) {
        const fromDetail = await resolveMissingRowIdFromTaskDetail(props.taskId, row, rowIndex)
        recovered = fromDetail.rowId
        meetingHints = fromDetail.meetingHints
        if (fromDetail.effectiveTaskId && fromDetail.effectiveTaskId.trim()) {
          effectiveTaskId = fromDetail.effectiveTaskId
        }
      }
      if (recovered != null) {
        rowIdNum = recovered
      }
    }

    assigning.value = true
    try {
      let response: unknown
      if (rowKeyForAssign != null && Object.keys(rowKeyForAssign).length > 0) {
        response = await assignSubTableRow(props.taskId, 0, assigneeId, rowKeyForAssign)
        if (effectiveTaskId !== props.taskId) {
          response = await assignSubTableRow(effectiveTaskId, 0, assigneeId, rowKeyForAssign)
        }
      } else if (!Number.isNaN(rowIdNum)) {
        response = await assignSubTableRow(props.taskId, rowIdNum, assigneeId)
      } else {
        response = await assignSubTableRowByIdentity(props.taskId, {
          assigneeId,
          email: typeof row.email === 'string' ? String(row.email) : undefined,
          name: typeof row.name === 'string' ? String(row.name) : undefined,
          department: typeof row.department === 'string' ? String(row.department) : undefined,
          topic: meetingHints?.topic,
          location: meetingHints?.location,
          organizerName: meetingHints?.organizerName,
        })
        if (effectiveTaskId !== props.taskId) {
          response = await assignSubTableRowByIdentity(effectiveTaskId, {
            assigneeId,
            email: typeof row.email === 'string' ? String(row.email) : undefined,
            name: typeof row.name === 'string' ? String(row.name) : undefined,
            department: typeof row.department === 'string' ? String(row.department) : undefined,
            topic: meetingHints?.topic,
            location: meetingHints?.location,
            organizerName: meetingHints?.organizerName,
          })
        }
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
        if (!opts?.fromEditDialog) {
          ElMessage.success(t('subTable.assignmentSuccess'))
          assignDialogVisible.value = false
        }
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
      try {
        const probe = await getTaskDetail(effectiveTaskId || props.taskId)
        const probeData = (probe as Record<string, unknown>).data as Record<string, unknown> | undefined
        void probeData
      } catch (probeError: unknown) {
        void probeError
      }
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

  async function confirmAssignment() {
    if (!selectedAssigneeId.value) {
      ElMessage.warning(t('subTable.pleaseSelectUser'))
      return
    }
    if (currentAssignRowIndex.value == null) return
    await performSubTableRowAssignment(currentAssignRowIndex.value, selectedAssigneeId.value)
  }

  return {
    assignDialogVisible,
    selectedAssigneeId,
    currentAssignRow,
    currentAssignRowIndex,
    assigning,
    userOptions,
    userSearchLoading,
    userNameCache,
    showAssigneeColumn,
    openAssignDialog,
    onAssignDialogOpened,
    searchUsers,
    getUserDisplayName,
    resolveRowAssigneeCell,
    applyAssigneeDisplayNameToRow,
    performSubTableRowAssignment,
    confirmAssignment
  }
}
