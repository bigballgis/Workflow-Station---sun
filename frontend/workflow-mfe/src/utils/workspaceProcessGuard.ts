import type { UserInfo } from '@/api/auth'

/**
 * 与门户后端「存在 UBR 时 JWT 须含完整工作台（BU + 角色）」对齐的纯前端启发式。
 * 无 UBR 的用户通常无 activeBusinessUnitId / activeRoleId，不应拦截发起。
 */
export function needsWorkspaceContextForProcessStart(u: UserInfo | null | undefined): boolean {
  if (!u) return false
  if (u.workspaceSwitcherVisible) return true
  const bu = u.activeBusinessUnitId?.trim()
  const role = u.activeRoleId?.trim()
  if (bu || role) return true
  return false
}

export function hasCompleteWorkspaceContext(u: UserInfo | null | undefined): boolean {
  const bu = u?.activeBusinessUnitId?.trim()
  const role = u?.activeRoleId?.trim()
  return !!bu && !!role
}

export function isProcessStartBlockedByWorkspace(u: UserInfo | null | undefined): boolean {
  return needsWorkspaceContextForProcessStart(u) && !hasCompleteWorkspaceContext(u)
}
