<template>
  <div class="approvals-page">
    <div class="page-header">
      <h1>{{ t('approval.title') }}</h1>
    </div>

    <div class="portal-card">
      <el-alert 
        v-if="!isApprover && !loading" 
        type="warning" 
        :title="t('approval.noPermission')"
        :description="t('approval.noPermissionDesc')"
        show-icon
        :closable="false"
      />
      
      <template v-else>
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane :label="t('approval.pendingList')" name="pending">
            <el-empty v-if="pendingList.length === 0 && !loading" :description="t('approval.noPendingApprovals')" />
            
            <el-table v-else :data="pendingList" stripe v-loading="loading">
              <el-table-column prop="applicantId" :label="t('approval.applicant')" width="150">
                <template #default="{ row }">
                  {{ getApplicantDisplay(row) }}
                </template>
              </el-table-column>
              <el-table-column prop="requestType" :label="t('permission.requestType')" width="140">
                <template #default="{ row }">
                  <el-tag :type="getRequestTypeTag(row.requestType)" size="small">
                    {{ getRequestTypeLabel(row.requestType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="t('permission.requestTarget')" min-width="180">
                <template #default="{ row }">
                  {{ getTargetName(row) }}
                </template>
              </el-table-column>
              <el-table-column prop="reason" :label="t('permission.reason')" min-width="200" show-overflow-tooltip />
              <el-table-column prop="createdAt" :label="t('permission.applyTime')" width="160">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column :label="t('common.actions')" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button type="success" size="small" @click="showApproveDialog(row)">
                    {{ t('approval.approve') }}
                  </el-button>
                  <el-button type="danger" size="small" @click="showRejectDialog(row)">
                    {{ t('approval.reject') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          
          <el-tab-pane :label="t('approval.historyList')" name="history">
            <el-empty v-if="historyList.length === 0 && !historyLoading" :description="t('approval.noApprovalHistory')" />
            
            <el-table v-else :data="historyList" stripe v-loading="historyLoading">
              <el-table-column prop="applicantId" :label="t('approval.applicant')" width="150">
                <template #default="{ row }">
                  {{ getApplicantDisplay(row) }}
                </template>
              </el-table-column>
              <el-table-column prop="requestType" :label="t('permission.requestType')" width="140">
                <template #default="{ row }">
                  <el-tag :type="getRequestTypeTag(row.requestType)" size="small">
                    {{ getRequestTypeLabel(row.requestType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="t('permission.requestTarget')" min-width="180">
                <template #default="{ row }">
                  {{ getTargetName(row) }}
                </template>
              </el-table-column>
              <el-table-column prop="status" :label="t('permission.status')" width="100">
                <template #default="{ row }">
                  <el-tag :type="getStatusTag(row.status)" size="small">
                    {{ getStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="approverComment" :label="t('approval.comment')" min-width="150" show-overflow-tooltip />
              <el-table-column prop="approvedAt" :label="t('approval.processedAt')" width="160">
                <template #default="{ row }">
                  {{ formatDateTime(row.approvedAt) }}
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <!-- 批准对话框 -->
    <el-dialog v-model="approveDialogVisible" :title="t('approval.approveTitle')" width="500px">
      <div class="approval-info">
        <p><strong>{{ t('approval.applicant') }}:</strong> {{ getApplicantDisplay(currentRequest) }}</p>
        <p><strong>{{ t('permission.requestType') }}:</strong> {{ getRequestTypeLabel(currentRequest?.requestType) }}</p>
        <p><strong>{{ t('permission.requestTarget') }}:</strong> {{ getTargetName(currentRequest) }}</p>
        <p><strong>{{ t('permission.reason') }}:</strong> {{ currentRequest?.reason }}</p>
      </div>
      <el-form-item :label="t('approval.comment')">
        <el-input v-model="approveComment" type="textarea" :rows="3" :placeholder="t('approval.commentPlaceholder')" />
      </el-form-item>
      <template #footer>
        <el-button @click="approveDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="success" @click="handleApprove" :loading="submitting">{{ t('approval.approve') }}</el-button>
      </template>
    </el-dialog>

    <!-- 拒绝对话框 -->
    <el-dialog v-model="rejectDialogVisible" :title="t('approval.rejectTitle')" width="500px">
      <div class="approval-info">
        <p><strong>{{ t('approval.applicant') }}:</strong> {{ getApplicantDisplay(currentRequest) }}</p>
        <p><strong>{{ t('permission.requestType') }}:</strong> {{ getRequestTypeLabel(currentRequest?.requestType) }}</p>
        <p><strong>{{ t('permission.requestTarget') }}:</strong> {{ getTargetName(currentRequest) }}</p>
        <p><strong>{{ t('permission.reason') }}:</strong> {{ currentRequest?.reason }}</p>
      </div>
      <el-form-item :label="t('approval.rejectReason')" required>
        <el-input v-model="rejectComment" type="textarea" :rows="3" :placeholder="t('approval.rejectReasonPlaceholder')" />
      </el-form-item>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" @click="handleReject" :loading="submitting">{{ t('approval.reject') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { permissionApi, type PermissionRequestRecord } from '@/api/permission'

const { t } = useI18n()

const loading = ref(false)
const historyLoading = ref(false)
const submitting = ref(false)
const isApprover = ref(false)
const activeTab = ref('pending')
const pendingList = ref<PermissionRequestRecord[]>([])
const historyList = ref<PermissionRequestRecord[]>([])

const approveDialogVisible = ref(false)
const rejectDialogVisible = ref(false)
const currentRequest = ref<PermissionRequestRecord | null>(null)
const approveComment = ref('')
const rejectComment = ref('')

// 检查审批权限
const checkApproverStatus = async () => {
  try {
    const res = await permissionApi.isApprover() as any
    isApprover.value = res?.data?.isApprover || res?.isApprover || false
  } catch (e) {
    console.error('Failed to check approver status:', e)
    isApprover.value = false
  }
}

// 加载待审批列表
const loadPendingApprovals = async () => {
  if (!isApprover.value) return
  
  loading.value = true
  try {
    const res = await permissionApi.getPendingApprovals({ page: 0, size: 100 }) as any
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
    console.error('Failed to load pending approvals:', e)
    pendingList.value = []
  } finally {
    loading.value = false
  }
}

// 加载审批历史
const loadApprovalHistory = async () => {
  if (!isApprover.value) return
  
  historyLoading.value = true
  try {
    const res = await permissionApi.getApprovalHistory({ page: 0, size: 100 }) as any
    if (res?.data?.content) {
      historyList.value = res.data.content
    } else if (res?.content) {
      historyList.value = res.content
    } else if (Array.isArray(res)) {
      historyList.value = res
    } else {
      historyList.value = []
    }
  } catch (e) {
    console.error('Failed to load approval history:', e)
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

// Tab切换处理
const handleTabChange = (tab: string | number) => {
  if (tab === 'history' && historyList.value.length === 0) {
    loadApprovalHistory()
  }
}

// 显示批准对话框
const showApproveDialog = (row: PermissionRequestRecord) => {
  currentRequest.value = row
  approveComment.value = ''
  approveDialogVisible.value = true
}

// 显示拒绝对话框
const showRejectDialog = (row: PermissionRequestRecord) => {
  currentRequest.value = row
  rejectComment.value = ''
  rejectDialogVisible.value = true
}

// 处理批准
const handleApprove = async () => {
  if (!currentRequest.value) return
  
  submitting.value = true
  try {
    await permissionApi.approveRequest(currentRequest.value.id, approveComment.value || undefined)
    ElMessage.success(t('approval.approveSuccess'))
    approveDialogVisible.value = false
    loadPendingApprovals()
    // 刷新历史列表
    historyList.value = []
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('approval.approveFailed')
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

// 处理拒绝
const handleReject = async () => {
  if (!currentRequest.value) return
  
  if (!rejectComment.value.trim()) {
    ElMessage.warning(t('approval.rejectReasonRequired'))
    return
  }
  
  submitting.value = true
  try {
    await permissionApi.rejectRequest(currentRequest.value.id, rejectComment.value)
    ElMessage.success(t('approval.rejectSuccess'))
    rejectDialogVisible.value = false
    loadPendingApprovals()
    // 刷新历史列表
    historyList.value = []
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('approval.rejectFailed')
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

// 辅助函数
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const getRequestTypeTag = (type: string): TagType => {
  const map: Record<string, TagType> = {
    VIRTUAL_GROUP: 'success',
    VIRTUAL_GROUP_JOIN: 'success',
    BUSINESS_UNIT: 'primary',
    BUSINESS_UNIT_JOIN: 'primary',
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
    ROLE_ASSIGNMENT: t('permission.roleAssignment')
  }
  return map[type] || type
}

const getTargetName = (row: any) => {
  if (!row) return '-'
  if (row.targetName) return row.targetName
  if (row.virtualGroupName) return row.virtualGroupName
  if (row.businessUnitName) return row.businessUnitName
  if (row.roleName) return row.roleName
  return '-'
}

const getApplicantDisplay = (row: any) => {
  if (!row) return '-'
  return row.applicantName || row.applicantUsername || row.applicantId || '-'
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

const getStatusTag = (status: string): TagType => {
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

// 初始化
onMounted(async () => {
  await checkApproverStatus()
  if (isApprover.value) {
    loadPendingApprovals()
  }
})
</script>

<style lang="scss" scoped>
.approvals-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  .approval-info {
    margin-bottom: 20px;
    padding: 15px;
    background: var(--bg-secondary);
    border-radius: 8px;
    
    p {
      margin: 8px 0;
      
      strong {
        color: var(--text-secondary);
        margin-right: 8px;
      }
    }
  }
}
</style>
