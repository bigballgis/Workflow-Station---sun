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
  /**
   * 准入角色查询。任务分配链路统一用 BU code（见 48e2ec21 code 化重构），
   * 故入参为 BU code、返回值为角色 code 列表。
   */
  getEligibleRoleCodes: (businessUnitCode: string) =>
    get<string[]>(`/task-assignment/business-units/${businessUnitCode}/eligible-roles`),

  getBuBoundedRoles: () => get<BuBoundedRole[]>(`/task-assignment/roles/bu-bounded`)
}

/**
 * 某业务单元下可分配的 BU_BOUNDED 角色：准入角色 ∩ 全量 BU 绑定型角色。
 * 任务分配链路统一用 code：入参为 BU code，eligible-roles 返回的也是 role code，故按 code 求交。
 */
export async function listAssignableBuBoundedRoles(businessUnitCode: string): Promise<BuBoundedRole[]> {
  const [eligibleCodes, buBounded] = await Promise.all([
    taskAssignmentApi.getEligibleRoleCodes(businessUnitCode),
    taskAssignmentApi.getBuBoundedRoles()
  ])
  const eligible = new Set(eligibleCodes)
  return buBounded.filter((r) => eligible.has(r.code))
}
