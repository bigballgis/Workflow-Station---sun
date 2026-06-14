import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { useI18n } from 'vue-i18n'
import { permissionApi, type PermissionRequestRecord } from '@/api/permission'

type TFn = ReturnType<typeof useI18n>['t']

export interface UseApprovalsDeps {
  /** 审批后刷新「我的申请」进行中列表 */
  loadPendingRequests: () => void
  /** 审批后刷新「我的申请」已完成列表 */
  loadHistoryRequests: () => void
  /** 刷新全局待审批计数（pendingApproval store） */
  fetchPendingCount: () => Promise<unknown> | unknown
}

/** 审批侧：审批人身份、待审批/审批历史列表、批准/拒绝对话框与操作。 */
export function useApprovals(t: TFn, deps: UseApprovalsDeps) {
  const approvalTab = ref('pendingApproval')
  const isApprover = ref(false)
  const approverPendingList = ref<PermissionRequestRecord[]>([])
  const approverHistoryList = ref<PermissionRequestRecord[]>([])
  const loadingApproverPending = ref(false)
  const loadingApproverHistory = ref(false)
  const approveDialogVisible = ref(false)
  const rejectDialogVisible = ref(false)
  const currentApproverRequest = ref<PermissionRequestRecord | null>(null)
  const approveComment = ref('')
  const rejectComment = ref('')
  const submittingApproval = ref(false)

  const approvalPendingCount = computed(() => approverPendingList.value.length)

  const checkApproverStatus = async () => {
    try {
      const res = (await permissionApi.isApprover()) as { data?: { isApprover?: boolean }; isApprover?: boolean }
      isApprover.value = res?.data?.isApprover ?? res?.isApprover ?? false
    } catch (e) {
      console.error('Failed to check approver status:', e)
      isApprover.value = false
    }
  }

  const loadApproverPending = async () => {
    if (!isApprover.value) return
    loadingApproverPending.value = true
    try {
      const res = (await permissionApi.getPendingApprovals({ page: 0, size: 100 })) as any
      if (res?.data?.content) {
        approverPendingList.value = res.data.content
      } else if (res?.content) {
        approverPendingList.value = res.content
      } else if (Array.isArray(res)) {
        approverPendingList.value = res
      } else {
        approverPendingList.value = []
      }
    } catch (e) {
      console.error('Failed to load pending approvals:', e)
      approverPendingList.value = []
    } finally {
      loadingApproverPending.value = false
    }
  }

  const loadApproverHistory = async () => {
    if (!isApprover.value) return
    loadingApproverHistory.value = true
    try {
      const res = (await permissionApi.getApprovalHistory({ page: 0, size: 100 })) as any
      if (res?.data?.content) {
        approverHistoryList.value = res.data.content
      } else if (res?.content) {
        approverHistoryList.value = res.content
      } else if (Array.isArray(res)) {
        approverHistoryList.value = res
      } else {
        approverHistoryList.value = []
      }
    } catch (e) {
      console.error('Failed to load approval history:', e)
      approverHistoryList.value = []
    } finally {
      loadingApproverHistory.value = false
    }
  }

  const onApprovalTabChange = (tab: string | number) => {
    if (String(tab) === 'approvalHistory') {
      loadApproverHistory()
    }
  }

  const showApproveDialog = (row: PermissionRequestRecord) => {
    currentApproverRequest.value = row
    approveComment.value = ''
    approveDialogVisible.value = true
  }

  const showRejectDialog = (row: PermissionRequestRecord) => {
    currentApproverRequest.value = row
    rejectComment.value = ''
    rejectDialogVisible.value = true
  }

  const handleApprove = async () => {
    if (!currentApproverRequest.value) return
    submittingApproval.value = true
    try {
      await permissionApi.approveRequest(currentApproverRequest.value.id, approveComment.value || undefined)
      ElMessage.success(t('approval.approveSuccess'))
      approveDialogVisible.value = false
      await loadApproverPending()
      await deps.fetchPendingCount()
      approverHistoryList.value = []
      deps.loadPendingRequests()
      deps.loadHistoryRequests()
    } catch (e: any) {
      const msg = e.response?.data?.message || e.message || t('approval.approveFailed')
      ElMessage.error(msg)
    } finally {
      submittingApproval.value = false
    }
  }

  const handleReject = async () => {
    if (!currentApproverRequest.value) return
    if (!rejectComment.value.trim()) {
      ElMessage.warning(t('approval.rejectReasonRequired'))
      return
    }
    submittingApproval.value = true
    try {
      await permissionApi.rejectRequest(currentApproverRequest.value.id, rejectComment.value)
      ElMessage.success(t('approval.rejectSuccess'))
      rejectDialogVisible.value = false
      await loadApproverPending()
      await deps.fetchPendingCount()
      approverHistoryList.value = []
      deps.loadPendingRequests()
      deps.loadHistoryRequests()
    } catch (e: any) {
      const msg = e.response?.data?.message || e.message || t('approval.rejectFailed')
      ElMessage.error(msg)
    } finally {
      submittingApproval.value = false
    }
  }

  return {
    approvalTab,
    isApprover,
    approverPendingList,
    approverHistoryList,
    loadingApproverPending,
    loadingApproverHistory,
    approveDialogVisible,
    rejectDialogVisible,
    currentApproverRequest,
    approveComment,
    rejectComment,
    submittingApproval,
    approvalPendingCount,
    checkApproverStatus,
    loadApproverPending,
    loadApproverHistory,
    onApprovalTabChange,
    showApproveDialog,
    showRejectDialog,
    handleApprove,
    handleReject
  }
}
