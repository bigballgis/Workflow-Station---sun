import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { useI18n } from 'vue-i18n'
import { permissionApi, type PermissionRequestRecord } from '@/api/permission'

type TFn = ReturnType<typeof useI18n>['t']

/** 「我的申请」：进行中 / 已完成列表，以及取消申请。 */
export function useMyRequests(t: TFn) {
  const myRequestTab = ref('inProgress')
  const loadingPending = ref(false)
  const loadingHistory = ref(false)
  const pendingList = ref<PermissionRequestRecord[]>([])
  const historyList = ref<PermissionRequestRecord[]>([])

  const pendingCount = computed(() => pendingList.value.length)

  // 加载待处理申请
  const loadPendingRequests = async () => {
    loadingPending.value = true
    try {
      const res = await permissionApi.getRequestHistory({ status: 'PENDING', page: 0, size: 100 }) as any
      if (res?.data?.content) {
        pendingList.value = res.data.content
      } else if (res?.content) {
        pendingList.value = res.content
      } else if (Array.isArray(res)) {
        pendingList.value = res
      } else {
        pendingList.value = []
      }
    } catch (e) {
      console.error('Failed to load pending requests:', e)
      pendingList.value = []
    } finally {
      loadingPending.value = false
    }
  }

  // 加载历史记录（已批准和已拒绝）
  const loadHistoryRequests = async () => {
    loadingHistory.value = true
    try {
      const res = await permissionApi.getRequestHistory({ page: 0, size: 50 }) as any
      let allRequests: any[] = []
      if (res?.data?.content) {
        allRequests = res.data.content
      } else if (res?.content) {
        allRequests = res.content
      } else if (Array.isArray(res)) {
        allRequests = res
      }
      // 过滤出已完成的申请（APPROVED, REJECTED, CANCELLED）
      historyList.value = allRequests.filter(
        (r: any) => r.status !== 'PENDING'
      )
    } catch (e) {
      console.error('Failed to load history requests:', e)
      historyList.value = []
    } finally {
      loadingHistory.value = false
    }
  }

  // 取消申请
  const cancelRequest = async (row: PermissionRequestRecord) => {
    try {
      await ElMessageBox.confirm(t('permission.cancelConfirm'), t('common.warning'), {
        type: 'warning'
      })

      await permissionApi.cancelRequest(row.id)
      ElMessage.success(t('permission.cancelSuccess'))
      // Keep UI consistent immediately even if history API is paginated/filtered.
      const cancelledRecord: PermissionRequestRecord = {
        ...row,
        status: 'CANCELLED',
        updatedAt: new Date().toISOString()
      }
      pendingList.value = pendingList.value.filter(item => item.id !== row.id)
      historyList.value = [cancelledRecord, ...historyList.value.filter(item => item.id !== row.id)]
      // Refresh pending from server, but keep cancelled item visible in history list.
      loadPendingRequests()
    } catch (e: any) {
      if (e !== 'cancel') {
        ElMessage.error(t('permission.cancelFailed'))
      }
    }
  }

  return {
    myRequestTab,
    loadingPending,
    loadingHistory,
    pendingList,
    historyList,
    pendingCount,
    loadPendingRequests,
    loadHistoryRequests,
    cancelRequest
  }
}
