import { get } from './request'

/** 与 task-assignment/roles/bu-bounded 返回的 Role 实体字段对齐 */
export interface BuBoundedRole {
  id: string
  code: string
  name: string
  type: string
  description?: string
  status?: string
}

export const taskAssignmentApi = {
  getEligibleRoleIds: (businessUnitId: string) =>
    get<string[]>(`/task-assignment/business-units/${businessUnitId}/eligible-roles`),

  getBuBoundedRoles: () => get<BuBoundedRole[]>(`/task-assignment/roles/bu-bounded`)
}

/** 某业务单元下可分配的 BU_BOUNDED 角色：准入角色 ID ∩ 全量 BU 绑定型角色 */
export async function listAssignableBuBoundedRoles(businessUnitId: string): Promise<BuBoundedRole[]> {
  const [eligibleIds, buBounded] = await Promise.all([
    taskAssignmentApi.getEligibleRoleIds(businessUnitId),
    taskAssignmentApi.getBuBoundedRoles()
  ])
  const eligible = new Set(eligibleIds)
  return buBounded.filter((r) => eligible.has(r.id))
}
