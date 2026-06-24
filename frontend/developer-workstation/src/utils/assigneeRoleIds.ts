/** Parse BPMN extension roleIds (comma-separated) with legacy roleId fallback. */
export function parseRoleIdsFromExt(ext: { roleIds?: string; roleId?: string }): string[] {
  const raw = ext.roleIds?.trim()
  if (raw) {
    return raw.split(',').map(s => s.trim()).filter(Boolean)
  }
  const single = ext.roleId?.trim()
  return single ? [single] : []
}

export function serializeRoleIds(ids: string[]): string {
  return ids.map(s => s.trim()).filter(Boolean).join(',')
}

export type RoleIdsFilterContext = {
  assigneeType: string
  businessUnitId?: string
  /** Roles eligible under the selected BU (FIXED_BU_ROLE / BU_ROLE) */
  eligibleRoleIds: Iterable<string>
  /** BU-bounded roles for initiator hierarchy types */
  boundedRoleIds: Iterable<string>
}

function toIdSet(ids: Iterable<string>): Set<string> {
  return ids instanceof Set ? ids : new Set(ids)
}

/** Strip role ids outside the catalog allowed for the current assignee type + BU. */
export function filterRoleIdsForAssigneeType(
  ids: string[],
  ctx: RoleIdsFilterContext,
): string[] {
  const normalized = ids.map(id => id.trim()).filter(Boolean)
  const needsBuForRole =
    ctx.assigneeType === 'FIXED_BU_ROLE' || ctx.assigneeType === 'BU_ROLE'
  const needsInitiatorHierarchy =
    ctx.assigneeType === 'INITIATOR_BU_ROLE'
    || ctx.assigneeType === 'INITIATOR_PARENT_BU_ROLE'

  if (needsBuForRole && ctx.businessUnitId) {
    const allowed = toIdSet(ctx.eligibleRoleIds)
    return normalized.filter(id => allowed.has(id))
  }
  if (needsInitiatorHierarchy) {
    const allowed = toIdSet(ctx.boundedRoleIds)
    return normalized.filter(id => allowed.has(id))
  }
  return normalized
}

/**
 * Returns sanitized ids when persisted values include out-of-catalog roles; otherwise null.
 */
export function sanitizePersistedRoleIds(
  roleIds: string[],
  ctx: RoleIdsFilterContext,
  opts: { needsMultiRoleSelect: boolean },
): string[] | null {
  if (!opts.needsMultiRoleSelect) {
    return null
  }
  const needsBuForRole =
    ctx.assigneeType === 'FIXED_BU_ROLE' || ctx.assigneeType === 'BU_ROLE'
  if (needsBuForRole && !ctx.businessUnitId) {
    return null
  }
  const valid = filterRoleIdsForAssigneeType(roleIds, ctx)
  return valid.length !== roleIds.length ? valid : null
}
