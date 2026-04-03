/**
 * 解析门户 GET /my-permissions（getMyPermissionView）载荷，供个人中心与顶栏下拉共用。
 */

export interface PortalPermissionLists {
  businessUnits: { id: string; name: string }[]
  virtualGroups: { groupId: string; groupName: string }[]
  roles: { id: string; name: string; type?: string }[]
}

export function parseMyPermissionViewPayload(data: Record<string, unknown>): PortalPermissionLists {
  const businessUnits = (data.businessUnits as { id: string; name: string }[]) || []
  const vgs = (data.virtualGroups as { groupId?: string; groupName?: string; id?: string; name?: string }[]) || []
  const virtualGroups = vgs.map((vg) => ({
    groupId: vg.groupId || vg.id || '',
    groupName: vg.groupName || vg.name || ''
  }))

  const roles: { id: string; name: string; type?: string }[] = []
  const bounded = data.buBoundedRoles as { role: { id: string; name: string } }[] | undefined
  if (bounded) {
    bounded.forEach((row) => {
      if (row.role) {
        roles.push({ id: row.role.id, name: row.role.name, type: 'BU_BOUNDED' })
      }
    })
  }
  const unbounded = data.buUnboundedRoles as { id: string; name: string }[] | undefined
  if (unbounded) {
    unbounded.forEach((r) => {
      roles.push({ id: r.id, name: r.name, type: 'BU_UNBOUNDED' })
    })
  }

  return { businessUnits, virtualGroups, roles }
}
