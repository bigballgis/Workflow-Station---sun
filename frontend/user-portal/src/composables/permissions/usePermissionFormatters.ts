import { getStoredUser } from '@/api/auth'
import type { useI18n } from 'vue-i18n'
import type { PermissionRequestRecord, RemovalAssignmentRow } from '@/api/permission'

type TFn = ReturnType<typeof useI18n>['t']

// 状态和类型处理
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/**
 * 纯展示/格式化辅助：状态与类型标签、目标名称、时间格式化、受益人/提交人显示等。
 * 无内部状态，仅依赖 i18n 的 `t`。
 */
export function usePermissionFormatters(t: TFn) {
  const getApplicantDisplay = (row: PermissionRequestRecord | null | undefined) => {
    if (!row) return '-'
    return row.applicantName || row.applicantUsername || row.applicantId || '-'
  }

  const getSubmitterDisplay = (row: PermissionRequestRecord | null | undefined) => {
    if (!row?.submittedByUserId) return '—'
    if (row.submittedByUserId === row.applicantId) return t('permission.selfBeneficiary')
    return row.submittedByUsername || row.submittedByUserId
  }

  const rowRemovalKey = (businessUnitId: string, roleId: string) => `${businessUnitId}::${roleId}`

  const removalRowLabel = (a: RemovalAssignmentRow) =>
    t('permission.removalRowLabel', {
      bu: a.businessUnitName || a.businessUnitId,
      role: a.roleName || a.roleId
    })

  const beneficiaryOptionLabel = (u: { userId: string; username: string; displayName?: string }) => {
    const name = u.displayName || u.username || u.userId
    return `${u.username || u.userId}${name !== u.username ? ` · ${name}` : ''}`
  }

  const canCancelAsBeneficiary = (row: PermissionRequestRecord) => {
    const me = getStoredUser()?.userId
    return !!(me && row.applicantId === me)
  }

  const getStatusType = (status: string): TagType => {
    const map: Record<string, TagType> = {
      PENDING: 'warning',
      APPROVED: 'success',
      REJECTED: 'danger',
      CANCELLED: 'info'
    }
    return map[status] || 'info'
  }

  const getStatusLabel = (status: string) => {
    const map: Record<string, string> = {
      PENDING: t('permission.pending'),
      APPROVED: t('permission.approved'),
      REJECTED: t('permission.rejected'),
      CANCELLED: t('permission.cancelled')
    }
    return map[status] || status
  }

  const getRequestTypeTag = (type: string): TagType => {
    const map: Record<string, TagType> = {
      VIRTUAL_GROUP: 'success',
      VIRTUAL_GROUP_JOIN: 'success',
      BUSINESS_UNIT: 'primary',
      BUSINESS_UNIT_JOIN: 'primary',
      BUSINESS_UNIT_ROLE: 'primary',
      BUSINESS_UNIT_ROLE_REMOVAL: 'warning',
      BUSINESS_UNIT_EXIT: 'danger',
      ROLE_ASSIGNMENT: 'info'
    }
    return map[type] || 'info'
  }

  const getRequestTypeLabel = (type: string | undefined) => {
    if (!type) return '-'
    const map: Record<string, string> = {
      VIRTUAL_GROUP: t('permission.virtualGroupJoin'),
      VIRTUAL_GROUP_JOIN: t('permission.virtualGroupJoin'),
      BUSINESS_UNIT: t('permission.businessUnitJoin'),
      BUSINESS_UNIT_JOIN: t('permission.businessUnitJoin'),
      BUSINESS_UNIT_ROLE: t('permission.businessUnitRole'),
      BUSINESS_UNIT_ROLE_REMOVAL: t('permission.businessUnitRoleRemoval'),
      BUSINESS_UNIT_EXIT: t('permission.businessUnitExit'),
      ROLE_ASSIGNMENT: t('permission.roleAssignment')
    }
    return map[type] || type
  }

  /** 「我的申请」列表接口返回 PermissionRequestListItem：仅有 targetId/targetName，无 businessUnit* 扁平字段 */
  const meaningfulListTargetName = (row: any): string | undefined => {
    const n = row?.targetName
    if (typeof n !== 'string') return undefined
    const tn = n.trim()
    if (tn && tn !== '-') return tn
    return undefined
  }

  // 获取申请目标名称
  const getTargetName = (row: any) => {
    if (!row) return '-'
    const listTn = meaningfulListTargetName(row)
    const listTid =
      row.targetId != null && String(row.targetId).trim() !== '' ? String(row.targetId).trim() : undefined

    if (row.requestType === 'BUSINESS_UNIT_EXIT') {
      return row.businessUnitName || listTn || row.businessUnitId || listTid || '-'
    }
    if (row.requestType === 'BUSINESS_UNIT_ROLE_REMOVAL') {
      const bu = row.businessUnitName || listTn || row.businessUnitId || listTid || ''
      const role =
        row.roleName ||
        row.roleId ||
        (Array.isArray(row.roleNames)
          ? row.roleNames.find((x: unknown) => x != null && String(x).trim() !== '')
          : undefined)
      const roleStr = role != null ? String(role).trim() : ''
      const joined = [bu, roleStr].filter(Boolean).join(' / ')
      return joined || '-'
    }
    if (listTn) return listTn
    if (row.targetName) return row.targetName
    if (row.virtualGroupName) return row.virtualGroupName
    if (row.businessUnitName) return row.businessUnitName
    if (row.roleName) return row.roleName
    return listTid || '-'
  }

  const isBuJoinMembershipRequest = (row: PermissionRequestRecord | null | undefined) => {
    const type = row?.requestType
    return type === 'BUSINESS_UNIT_JOIN' || type === 'BUSINESS_UNIT_ROLE'
  }

  const getRequestedRoleName = (row: PermissionRequestRecord | null | undefined) => {
    if (!row) return '-'
    const fromList = Array.isArray(row.roleNames)
      ? row.roleNames.find((name) => name != null && String(name).trim() !== '')
      : undefined
    const name = fromList != null ? String(fromList).trim() : ''
    return name || '-'
  }

  const getMembershipTypeLabel = (row: PermissionRequestRecord | null | undefined) => {
    if (!isBuJoinMembershipRequest(row)) return '-'
    return row?.membershipType === 'LEADER' ? t('permission.leader') : t('permission.member')
  }

  const formatDateTime = (dateStr: string) => {
    if (!dateStr) return '-'
    try {
      const date = new Date(dateStr)
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      })
    } catch {
      return dateStr
    }
  }

  return {
    getApplicantDisplay,
    getSubmitterDisplay,
    rowRemovalKey,
    removalRowLabel,
    beneficiaryOptionLabel,
    canCancelAsBeneficiary,
    getStatusType,
    getStatusLabel,
    getRequestTypeTag,
    getRequestTypeLabel,
    getTargetName,
    isBuJoinMembershipRequest,
    getRequestedRoleName,
    getMembershipTypeLabel,
    formatDateTime
  }
}
