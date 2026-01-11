<template>
  <div class="permissions-page">
    <div class="page-header">
      <h1>{{ t('permission.title') }}</h1>
      <el-button type="primary" @click="showApplyDialog">{{ t('permission.applyPermission') }}</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 我的权限 Tab -->
      <el-tab-pane :label="t('permission.myPermissions')" name="my">
        <div class="portal-card">
          <!-- 我的角色 -->
          <div class="section">
            <h3 class="section-title">
              <el-icon><User /></el-icon>
              {{ t('permission.myRoles') }}
            </h3>
            <el-empty v-if="myRoles.length === 0" :description="t('permission.noRoles')" />
            <div v-else class="role-list">
              <div v-for="role in myRoles" :key="role.roleId" class="role-item">
                <el-tag type="primary" size="large">{{ role.roleName || role.name }}</el-tag>
                <span v-if="role.organizationUnitName" class="org-name">
                  @ {{ role.organizationUnitName }}
                </span>
              </div>
            </div>
          </div>

          <el-divider />

          <!-- 我的虚拟组 -->
          <div class="section">
            <h3 class="section-title">
              <el-icon><UserFilled /></el-icon>
              {{ t('permission.myVirtualGroups') }}
            </h3>
            <el-empty v-if="myVirtualGroups.length === 0" :description="t('permission.noVirtualGroups')" />
            <div v-else class="virtual-group-list">
              <el-card v-for="group in myVirtualGroups" :key="group.groupId" class="group-card" shadow="hover">
                <template #header>
                  <div class="group-header">
                    <span class="group-name">{{ group.groupName || group.name }}</span>
                    <el-tag size="small" type="success">{{ t('permission.member') }}</el-tag>
                  </div>
                </template>
                <div v-if="group.boundRoles && group.boundRoles.length > 0" class="bound-roles">
                  <span class="label">{{ t('permission.boundRoles') }}:</span>
                  <el-tag v-for="role in group.boundRoles" :key="role.id" size="small" type="info" class="role-tag">
                    {{ role.name }}
                  </el-tag>
                </div>
                <div v-else class="no-bound-roles">
                  {{ t('permission.noBoundRoles') }}
                </div>
              </el-card>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 申请历史 Tab -->
      <el-tab-pane :label="t('permission.requestHistory')" name="history">
        <div class="portal-card">
          <el-table :data="requestList" stripe v-loading="loadingHistory">
            <el-table-column prop="requestType" :label="t('permission.requestType')" width="140">
              <template #default="{ row }">
                <el-tag :type="getRequestTypeTag(row.requestType)" size="small">
                  {{ getRequestTypeLabel(row.requestType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.requestTarget')" min-width="200">
              <template #default="{ row }">
                <template v-if="row.requestType === 'ROLE_ASSIGNMENT'">
                  <span>{{ row.roleName }}</span>
                  <span v-if="row.organizationUnitName" class="org-info">
                    @ {{ row.organizationUnitName }}
                  </span>
                </template>
                <template v-else-if="row.requestType === 'VIRTUAL_GROUP_JOIN'">
                  {{ row.virtualGroupName }}
                </template>
                <template v-else>
                  {{ row.permissions?.join(', ') || '-' }}
                </template>
              </template>
            </el-table-column>
            <el-table-column prop="reason" :label="t('permission.reason')" width="200" show-overflow-tooltip />
            <el-table-column prop="status" :label="t('permission.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" :label="t('permission.applyTime')" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
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
            <el-radio-button value="role">{{ t('permission.applyRole') }}</el-radio-button>
            <el-radio-button value="virtualGroup">{{ t('permission.joinVirtualGroup') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 申请角色模式 -->
        <template v-if="applyForm.applyType === 'role'">
          <el-form-item :label="t('permission.organization')" required>
            <el-select v-model="applyForm.organizationUnitId" :placeholder="t('permission.selectOrganization')" style="width: 100%;" filterable>
              <el-option
                v-for="dept in departments"
                :key="dept.id"
                :label="dept.name"
                :value="dept.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('permission.role')" required>
            <el-select v-model="applyForm.roleId" :placeholder="t('permission.selectRole')" style="width: 100%;" filterable>
              <el-option
                v-for="role in availableRoles"
                :key="role.id"
                :label="role.name"
                :value="role.id"
              >
                <span>{{ role.name }}</span>
                <span v-if="role.description" class="role-desc"> - {{ role.description }}</span>
              </el-option>
            </el-select>
          </el-form-item>
        </template>

        <!-- 加入虚拟组模式 -->
        <template v-else>
          <el-form-item :label="t('permission.virtualGroup')" required>
            <el-select v-model="applyForm.virtualGroupId" :placeholder="t('permission.selectVirtualGroup')" style="width: 100%;" filterable @change="onVirtualGroupChange">
              <el-option
                v-for="group in availableVirtualGroups"
                :key="group.id"
                :label="group.name"
                :value="group.id"
              >
                <span>{{ group.name }}</span>
                <span v-if="group.description" class="group-desc"> - {{ group.description }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item v-if="selectedGroupBoundRoles.length > 0" :label="t('permission.boundRoles')">
            <div class="bound-roles-preview">
              <el-tag v-for="role in selectedGroupBoundRoles" :key="role.id" size="small" type="info" class="role-tag">
                {{ role.name }}
              </el-tag>
            </div>
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
import { ElMessage } from 'element-plus'
import { User, UserFilled } from '@element-plus/icons-vue'
import { permissionApi, type RoleInfo, type VirtualGroupInfo, type OrganizationUnit, type UserRoleAssignment, type UserVirtualGroupMembership, type PermissionRequestRecord } from '@/api/permission'

const { t } = useI18n()

const activeTab = ref('my')
const applyDialogVisible = ref(false)
const submitting = ref(false)
const loadingHistory = ref(false)

// 数据
const myRoles = ref<UserRoleAssignment[]>([])
const myVirtualGroups = ref<UserVirtualGroupMembership[]>([])
const requestList = ref<PermissionRequestRecord[]>([])
const availableRoles = ref<RoleInfo[]>([])
const availableVirtualGroups = ref<VirtualGroupInfo[]>([])
const departments = ref<OrganizationUnit[]>([])

// 申请表单
const applyForm = reactive({
  applyType: 'role' as 'role' | 'virtualGroup',
  roleId: '',
  organizationUnitId: '',
  virtualGroupId: '',
  reason: ''
})

// 选中的虚拟组绑定的角色
const selectedGroupBoundRoles = computed(() => {
  if (!applyForm.virtualGroupId) return []
  const group = availableVirtualGroups.value.find(g => g.id === applyForm.virtualGroupId)
  return group?.boundRoles || []
})

// 加载数据
const loadMyRoles = async () => {
  try {
    const res = await permissionApi.getMyRoles()
    myRoles.value = res.data?.data || res.data || []
  } catch (e) {
    console.error('Failed to load my roles:', e)
    myRoles.value = []
  }
}

const loadMyVirtualGroups = async () => {
  try {
    const res = await permissionApi.getMyVirtualGroups()
    myVirtualGroups.value = res.data?.data || res.data || []
  } catch (e) {
    console.error('Failed to load my virtual groups:', e)
    myVirtualGroups.value = []
  }
}

const loadRequestHistory = async () => {
  loadingHistory.value = true
  try {
    const res = await permissionApi.getRequestHistory({ page: 0, size: 50 })
    const data = res.data?.data || res.data
    requestList.value = data?.content || data || []
  } catch (e) {
    console.error('Failed to load request history:', e)
    requestList.value = []
  } finally {
    loadingHistory.value = false
  }
}

const loadAvailableRoles = async () => {
  try {
    const res = await permissionApi.getAvailableRoles()
    availableRoles.value = res.data?.data || res.data || []
  } catch (e) {
    console.error('Failed to load available roles:', e)
    availableRoles.value = []
  }
}

const loadAvailableVirtualGroups = async () => {
  try {
    const res = await permissionApi.getAvailableVirtualGroups()
    availableVirtualGroups.value = res.data?.data || res.data || []
  } catch (e) {
    console.error('Failed to load available virtual groups:', e)
    availableVirtualGroups.value = []
  }
}

const loadDepartments = async () => {
  try {
    const res = await permissionApi.getDepartments()
    departments.value = res.data?.data || res.data || []
  } catch (e) {
    console.error('Failed to load departments:', e)
    departments.value = []
  }
}

// 状态和类型处理
const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: t('permission.pending'),
    APPROVED: t('permission.approved'),
    REJECTED: t('permission.rejected')
  }
  return map[status] || status
}

const getRequestTypeTag = (type: string) => {
  const map: Record<string, string> = {
    ROLE_ASSIGNMENT: 'primary',
    VIRTUAL_GROUP_JOIN: 'success',
    FUNCTION: 'info',
    DATA: 'warning',
    TEMPORARY: 'danger'
  }
  return map[type] || 'info'
}

const getRequestTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    ROLE_ASSIGNMENT: t('permission.roleAssignment'),
    VIRTUAL_GROUP_JOIN: t('permission.virtualGroupJoin'),
    FUNCTION: t('permission.function'),
    DATA: t('permission.data'),
    TEMPORARY: t('permission.temporary')
  }
  return map[type] || type
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

// 对话框操作
const showApplyDialog = () => {
  applyForm.applyType = 'role'
  applyForm.roleId = ''
  applyForm.organizationUnitId = ''
  applyForm.virtualGroupId = ''
  applyForm.reason = ''
  applyDialogVisible.value = true
  
  // 加载可选数据
  loadAvailableRoles()
  loadAvailableVirtualGroups()
  loadDepartments()
}

const onApplyTypeChange = () => {
  applyForm.roleId = ''
  applyForm.organizationUnitId = ''
  applyForm.virtualGroupId = ''
}

const onVirtualGroupChange = () => {
  // 选择虚拟组时自动显示绑定的角色
}

const submitApply = async () => {
  if (applyForm.applyType === 'role') {
    if (!applyForm.organizationUnitId) {
      ElMessage.warning(t('permission.selectOrganization'))
      return
    }
    if (!applyForm.roleId) {
      ElMessage.warning(t('permission.selectRole'))
      return
    }
  } else {
    if (!applyForm.virtualGroupId) {
      ElMessage.warning(t('permission.selectVirtualGroup'))
      return
    }
  }
  
  if (!applyForm.reason.trim()) {
    ElMessage.warning(t('permission.enterReason'))
    return
  }

  submitting.value = true
  try {
    if (applyForm.applyType === 'role') {
      await permissionApi.requestRole({
        roleId: applyForm.roleId,
        organizationUnitId: applyForm.organizationUnitId,
        reason: applyForm.reason
      })
      ElMessage.success(t('permission.roleRequestSuccess'))
    } else {
      await permissionApi.requestVirtualGroup({
        virtualGroupId: applyForm.virtualGroupId,
        reason: applyForm.reason
      })
      ElMessage.success(t('permission.virtualGroupRequestSuccess'))
    }
    
    applyDialogVisible.value = false
    
    // 刷新数据
    loadMyRoles()
    loadMyVirtualGroups()
    loadRequestHistory()
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('permission.requestFailed')
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

// 初始化
onMounted(() => {
  loadMyRoles()
  loadMyVirtualGroups()
  loadRequestHistory()
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
  
  .section {
    margin-bottom: 20px;
    
    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      font-weight: 500;
      color: var(--text-primary);
      margin-bottom: 16px;
    }
  }
  
  .role-list {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    
    .role-item {
      display: flex;
      align-items: center;
      gap: 8px;
      
      .org-name {
        color: var(--text-secondary);
        font-size: 13px;
      }
    }
  }
  
  .virtual-group-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 16px;
    
    .group-card {
      .group-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        
        .group-name {
          font-weight: 500;
        }
      }
      
      .bound-roles {
        .label {
          color: var(--text-secondary);
          font-size: 13px;
          margin-right: 8px;
        }
        
        .role-tag {
          margin-right: 6px;
          margin-bottom: 4px;
        }
      }
      
      .no-bound-roles {
        color: var(--text-secondary);
        font-size: 13px;
      }
    }
  }
  
  .org-info {
    color: var(--text-secondary);
    font-size: 13px;
    margin-left: 8px;
  }
  
  .role-desc, .group-desc {
    color: var(--text-secondary);
    font-size: 12px;
  }
  
  .bound-roles-preview {
    .role-tag {
      margin-right: 6px;
      margin-bottom: 4px;
    }
  }
}
</style>
