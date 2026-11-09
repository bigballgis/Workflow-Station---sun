/**
 * 解析门户 GET /my-permissions 载荷，供个人中心与顶栏下拉共用。
 * 门户不展示虚拟组；BU—角色以 UBR（buBoundedRoles）为准。
 */

export interface PortalBuBoundedRow {
  role: { id: string; name: string; code?: string; type?: string }
  activatedBusinessUnits: { id: string; name: string }[]
}

export interface PortalPermissionLists {
  businessUnits: { id: string; name: string }[]
  buBoundedRoles: PortalBuBoundedRow[]
}

/** @deprecated 门户不返回虚拟组，恒为空。保留字段避免旧代码解构报错 */
export interface PortalPermissionListsLegacy extends PortalPermissionLists {
  virtualGroups: { groupId: string; groupName: string }[]
  /** 扁平角色列表（仅兼容旧逻辑） */
  roles: { id: string; name: string; type?: string }[]
}

export function parseMyPermissionViewPayload(
  data: Record<string, unknown>
): PortalPermissionListsLegacy {
  const businessUnits = (data.businessUnits as { id: string; name: string }[]) || []
  const buBoundedRoles = (data.buBoundedRoles as PortalBuBoundedRow[]) || []

  const roles: { id: string; name: string; type?: string }[] = []
  buBoundedRoles.forEach((row) => {
    if (row.role) {
      roles.push({ id: row.role.id, name: row.role.name, type: 'BU_BOUNDED' })
    }
  })

  return {
    businessUnits,
    buBoundedRoles,
    virtualGroups: [],
    roles
  }
}
