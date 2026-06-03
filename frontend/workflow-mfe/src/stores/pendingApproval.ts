import { defineStore } from 'pinia'
import { ref } from 'vue'
import { permissionApi } from '@/api/permission'

/**
 * 侧边栏「用户档案设置」旁展示的待审批权限申请数量（与 /permissions Pending Approval 一致）。
 */
export const usePendingApprovalStore = defineStore('pendingApproval', () => {
  const count = ref(0)

  const fetchPendingCount = async () => {
    try {
      const approverRes = (await permissionApi.isApprover()) as {
        data?: { isApprover?: boolean }
        isApprover?: boolean
      }
      const isApprover = approverRes?.data?.isApprover ?? approverRes?.isApprover ?? false
      if (!isApprover) {
        count.value = 0
        return
      }
      const res = (await permissionApi.getPendingApprovals({ page: 0, size: 1 })) as {
        data?: { totalElements?: number }
        totalElements?: number
      }
      const total = res?.data?.totalElements ?? res?.totalElements ?? 0
      count.value = typeof total === 'number' ? total : 0
    } catch {
      count.value = 0
    }
  }

  return { count, fetchPendingCount }
})
