<template>
  <div class="permissions-page">
    <div class="page-header">
      <h1>{{ t('permission.title') }}</h1>
      <el-button type="primary" @click="showApplyDialog">{{ t('permission.applyPermission') }}</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 进行中 Tab -->
      <el-tab-pane name="pending">
        <template #label>
          <span>{{ t('permission.pending') }}</span>
          <el-badge v-if="pendingCount > 0" :value="pendingCount" class="tab-badge" />
        </template>
        <div class="portal-card">
          <el-empty v-if="pendingList.length === 0 && !loadingPending" :description="t('permission.noPendingRequests')" />
          <el-table v-else :data="pendingList" stripe v-loading="loadingPending">
            <el-table-column prop="requestType" :label="t('permission.requestType')" width="140">
              <template #default="{ row }">
                <el-tag :type="getRequestTypeTag(row.requestType)" size="small">
                  {{ getRequestTypeLabel(row.requestType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.requestTarget')" min-width="200">
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
            <el-table-column :label="t('common.actions')" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="danger" size="small" text @click="cancelRequest(row)">
                  {{ t('permission.cancelRequest') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 历史记录 Tab -->
      <el-tab-pane :label="t('permission.requestHistory')" name="history">
        <div class="portal-card">
          <el-empty v-if="historyList.length === 0 && !loadingHistory" :description="t('permission.noRequests')" />
          <el-table v-else :data="historyList" stripe v-loading="loadingHistory">
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
            <el-table-column prop="reason" :label="t('permission.reason')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="status" :label="t('permission.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approverComment" :label="t('approval.comment')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="createdAt" :label="t('permission.applyTime')" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" :label="t('permission.approvedAt')" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.updatedAt) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 申请权限对话框 -->
    <el-dialog v-model="applyDialogVisible" :title="t('permission.applyPermission')" width="600px">
      <el-form :model="applyForm" label-width="100px">
        <!-- 申请类型选择 -->
        <el-form-item :label="t('permission.applyType')">
          <el-radio-group v-model="applyForm.applyType" @change="onApplyTypeChange">
            <el-radio-button value="virtualGroup">{{ t('permission.joinVirtualGroup') }}</el-radio-button>
            <el-radio-button value="businessUnit">{{ t('permission.joinBusinessUnit') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 加入虚拟组模式 -->
        <template v-if="applyForm.applyType === 'virtualGroup'">
          <el-form-item :label="t('permission.virtualGroup')" required>
            <el-select 
              v-model="applyForm.virtualGroupId" 
              :placeholder="t('permission.selectVirtualGroup')" 
              style="width: 100%;" 
              filterable
              :teleported="false"
            >
              <el-option
                v-for="group in availableVirtualGroups"
                :key="group.id"
                :label="group.name"
                :value="group.id"
              >
                <div class="group-option">
                  <span>{{ group.name }}</span>
                  <span v-if="group.description" class="group-desc"> - {{ group.description }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
        </template>

        <!-- 加入业务单元模式 -->
        <template v-else-if="applyForm.applyType === 'businessUnit'">
          <el-form-item :label="t('permission.businessUnit')" required>
            <el-select 
              v-model="applyForm.businessUnitId" 
              :placeholder="t('permission.selectBusinessUnit')" 
              style="width: 100%;" 
              filterable 
              :loading="loadingBusinessUnits"
              :teleported="false"
            >
              <el-option
                v-for="bu in applicableBusinessUnits"
                :key="bu.id"
                :label="bu.name"
                :value="bu.id"
              />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item :label="t('permission.reason')" required>
          <el-input v-model="applyForm.reason" type="textarea" :rows="3" :placeholder="t('permission.reasonPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitApply" :loading="submitting">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permissionApi, type VirtualGroupInfo, type BusinessUnit, type PermissionRequestRecord } from '@/api/permission'

const { t } = useI18n()

const activeTab = ref('pending')
const applyDialogVisible = ref(false)
const submitting = ref(false)
const loadingPending = ref(false)
const loadingHistory = ref(false)
const loadingBusinessUnits = ref(false)

// 数据
const pendingList = ref<PermissionRequestRecord[]>([])
const historyList = ref<PermissionRequestRecord[]>([])
const availableVirtualGroups = ref<VirtualGroupInfo[]>([])
const applicableBusinessUnits = ref<BusinessUnit[]>([])

// 待处理数量
const pendingCount = computed(() => pendingList.value.length)

// 申请表单
const applyForm = reactive({
  applyType: 'virtualGroup' as 'virtualGroup' | 'businessUnit',
  virtualGroupId: '',
  businessUnitId: '',
  reason: ''
})

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

const loadAvailableVirtualGroups = async () => {
  try {
    const res = await permissionApi.getAvailableVirtualGroups() as any
    // axios 拦截器返回 response.data，即 ApiResponse { success, data: [...] }
    if (res?.data && Array.isArray(res.data)) {
      availableVirtualGroups.value = res.data
    } else if (Array.isArray(res)) {
      availableVirtualGroups.value = res
    } else {
      availableVirtualGroups.value = []
    }
  } catch (e) {
    console.error('Failed to load available virtual groups:', e)
    availableVirtualGroups.value = []
  }
}

const loadApplicableBusinessUnits = async () => {
  loadingBusinessUnits.value = true
  try {
    const res = await permissionApi.getApplicableBusinessUnits() as any
    // axios 拦截器返回 response.data，即 ApiResponse { success, data: [...] }
    if (res?.data && Array.isArray(res.data)) {
      applicableBusinessUnits.value = res.data
    } else if (Array.isArray(res)) {
      applicableBusinessUnits.value = res
    } else {
      applicableBusinessUnits.value = []
    }
  } catch (e) {
    console.error('Failed to load applicable business units:', e)
    applicableBusinessUnits.value = []
  } finally {
    loadingBusinessUnits.value = false
  }
}

// 状态和类型处理
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const getStatusType = (status: string): TagType => {
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

const getRequestTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    VIRTUAL_GROUP: t('permission.virtualGroupJoin'),
    VIRTUAL_GROUP_JOIN: t('permission.virtualGroupJoin'),
    BUSINESS_UNIT: t('permission.businessUnitJoin'),
    BUSINESS_UNIT_JOIN: t('permission.businessUnitJoin'),
    ROLE_ASSIGNMENT: t('permission.roleAssignment')
  }
  return map[type] || type
}

// 获取申请目标名称
const getTargetName = (row: any) => {
  if (row.targetName) return row.targetName
  if (row.virtualGroupName) return row.virtualGroupName
  if (row.businessUnitName) return row.businessUnitName
  if (row.roleName) return row.roleName
  return '-'
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

// 取消申请
const cancelRequest = async (row: PermissionRequestRecord) => {
  try {
    await ElMessageBox.confirm(t('permission.cancelConfirm'), t('common.warning'), {
      type: 'warning'
    })
    
    await permissionApi.cancelRequest(row.id)
    ElMessage.success(t('permission.cancelSuccess'))
    loadPendingRequests()
    loadHistoryRequests()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(t('permission.cancelFailed'))
    }
  }
}

// 对话框操作
const showApplyDialog = () => {
  applyForm.applyType = 'virtualGroup'
  applyForm.virtualGroupId = ''
  applyForm.businessUnitId = ''
  applyForm.reason = ''
  applyDialogVisible.value = true
  
  loadAvailableVirtualGroups()
  loadApplicableBusinessUnits()
}

const onApplyTypeChange = () => {
  applyForm.virtualGroupId = ''
  applyForm.businessUnitId = ''
}

const submitApply = async () => {
  if (applyForm.applyType === 'virtualGroup') {
    if (!applyForm.virtualGroupId) {
      ElMessage.warning(t('permission.selectVirtualGroup'))
      return
    }
  } else if (applyForm.applyType === 'businessUnit') {
    if (!applyForm.businessUnitId) {
      ElMessage.warning(t('permission.selectBusinessUnit'))
      return
    }
  }
  
  if (!applyForm.reason.trim()) {
    ElMessage.warning(t('permission.enterReason'))
    return
  }

  submitting.value = true
  try {
    if (applyForm.applyType === 'virtualGroup') {
      await permissionApi.requestVirtualGroup({
        virtualGroupId: applyForm.virtualGroupId,
        reason: applyForm.reason
      })
      ElMessage.success(t('permission.virtualGroupRequestSuccess'))
    } else {
      await permissionApi.requestBusinessUnit({
        businessUnitId: applyForm.businessUnitId,
        reason: applyForm.reason
      })
      ElMessage.success(t('permission.businessUnitRequestSuccess'))
    }
    
    applyDialogVisible.value = false
    loadPendingRequests()
    loadHistoryRequests()
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('permission.requestFailed')
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

// 初始化
onMounted(() => {
  loadPendingRequests()
  loadHistoryRequests()
})
</script>

<style lang="scss" scoped>
.permissions-page {
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
  
  .tab-badge {
    margin-left: 6px;
  }
  
  .group-option {
    .group-desc {
      color: var(--text-secondary);
      font-size: 12px;
    }
  }
}
</style>
