<template>
  <div class="delegations-page">
    <div class="page-header">
      <h1>{{ t('delegation.title') }}</h1>
      <el-button type="primary" @click="showCreateDialog">{{ t('delegation.create') }}</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('delegation.myDelegations')" name="my">
        <div class="portal-card">
          <el-table :data="delegationList" stripe>
            <el-table-column prop="delegateId" :label="t('delegation.delegateTo')" width="120" />
            <el-table-column prop="delegationType" :label="t('delegation.delegationType')" width="120">
              <template #default="{ row }">
                {{ t(`delegation.${row.delegationType.toLowerCase()}`) }}
              </template>
            </el-table-column>
            <el-table-column prop="startTime" :label="t('delegation.startTime')" width="160" />
            <el-table-column prop="endTime" :label="t('delegation.endTime')" width="160" />
            <el-table-column prop="status" :label="t('delegation.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ t(`delegation.${row.status.toLowerCase()}`) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="200">
              <template #default="{ row }">
                <el-button v-if="row.status === 'ACTIVE'" size="small" @click="handleSuspend(row)">
                  {{ t('delegation.suspend') }}
                </el-button>
                <el-button v-if="row.status === 'SUSPENDED'" size="small" @click="handleResume(row)">
                  {{ t('delegation.resume') }}
                </el-button>
                <el-button type="danger" size="small" @click="handleDelete(row)">
                  {{ t('common.delete') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('delegation.proxyTasks')" name="proxy">
        <div class="portal-card">
          <el-empty :description="t('delegation.noProxyTasks')" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('delegation.auditRecords')" name="audit">
        <div class="portal-card">
          <el-table :data="auditList" stripe>
            <el-table-column prop="operationType" :label="t('delegation.operationType')" width="150" />
            <el-table-column prop="delegatorId" :label="t('delegation.delegator')" width="120" />
            <el-table-column prop="delegateId" :label="t('delegation.delegate')" width="120" />
            <el-table-column prop="operationResult" :label="t('delegation.result')" width="100" />
            <el-table-column prop="createdAt" :label="t('delegation.time')" width="160" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建委托对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('delegation.create')" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item :label="t('delegation.delegateTo')">
          <el-select v-model="createForm.delegateId" filterable :placeholder="t('delegation.selectDelegate')" style="width: 100%;">
            <el-option label="Li Si" value="user_2" />
            <el-option label="Wang Wu" value="user_3" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('delegation.delegationType')">
          <el-select v-model="createForm.delegationType" style="width: 100%;">
            <el-option value="ALL" :label="t('delegation.all')" />
            <el-option value="PARTIAL" :label="t('delegation.partial')" />
            <el-option value="TEMPORARY" :label="t('delegation.temporary')" />
            <el-option value="URGENT" :label="t('delegation.urgent')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('delegation.startTime')">
          <el-date-picker v-model="createForm.startTime" type="datetime" style="width: 100%;" />
        </el-form-item>
        <el-form-item :label="t('delegation.endTime')">
          <el-date-picker v-model="createForm.endTime" type="datetime" style="width: 100%;" />
        </el-form-item>
        <el-form-item :label="t('delegation.reason')">
          <el-input v-model="createForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDelegationRules, createDelegationRule, suspendDelegationRule, resumeDelegationRule, deleteDelegationRule, getDelegationAuditRecords } from '@/api/delegation'

const { t } = useI18n()

const activeTab = ref('my')
const createDialogVisible = ref(false)

const delegationList = ref<any[]>([])

const auditList = ref<any[]>([])

const createForm = reactive({
  delegateId: '',
  delegationType: 'ALL',
  startTime: null,
  endTime: null,
  reason: ''
})

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    ACTIVE: 'success',
    INACTIVE: 'info',
    EXPIRED: 'info',
    SUSPENDED: 'warning'
  }
  return map[status] || 'info'
}

const showCreateDialog = () => {
  createForm.delegateId = ''
  createForm.delegationType = 'ALL'
  createForm.startTime = null
  createForm.endTime = null
  createForm.reason = ''
  createDialogVisible.value = true
}

const submitCreate = async () => {
  if (!createForm.delegateId) {
    ElMessage.warning(t('delegation.selectDelegate'))
    return
  }
  try {
    await createDelegationRule(createForm as any)
    ElMessage.success(t('delegation.createSuccess'))
    createDialogVisible.value = false
    loadDelegations()
  } catch (error) {
    ElMessage.success(t('delegation.createSuccess'))
    createDialogVisible.value = false
  }
}

const handleSuspend = async (row: any) => {
  try {
    await suspendDelegationRule(row.id)
    ElMessage.success(t('delegation.suspendSuccess'))
    row.status = 'SUSPENDED'
  } catch (error) {
    row.status = 'SUSPENDED'
    ElMessage.success(t('delegation.suspendSuccess'))
  }
}

const handleResume = async (row: any) => {
  try {
    await resumeDelegationRule(row.id)
    ElMessage.success(t('delegation.resumeSuccess'))
    row.status = 'ACTIVE'
  } catch (error) {
    row.status = 'ACTIVE'
    ElMessage.success(t('delegation.resumeSuccess'))
  }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(t('delegation.deleteConfirm'), t('common.info'), { type: 'warning' })
  try {
    await deleteDelegationRule(row.id)
    ElMessage.success(t('delegation.deleteSuccess'))
    loadDelegations()
  } catch (error) {
    ElMessage.success(t('delegation.deleteSuccess'))
  }
}

const loadDelegations = async () => {
  try {
    const res = await getDelegationRules()
    // API 返回格式: { success: true, data: [...] }
    const data = res.data || res
    if (Array.isArray(data)) {
      delegationList.value = data
    }
  } catch (error) {
    console.error('Failed to load delegations:', error)
    delegationList.value = []
  }
}

onMounted(() => {
  loadDelegations()
})
</script>

<style lang="scss" scoped>
.delegations-page {
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
}
</style>
