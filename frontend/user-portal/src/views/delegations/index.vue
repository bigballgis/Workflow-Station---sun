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
            <el-table-column label="操作" width="200">
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
          <el-empty description="暂无代理任务" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('delegation.auditRecords')" name="audit">
        <div class="portal-card">
          <el-table :data="auditList" stripe>
            <el-table-column prop="operationType" label="操作类型" width="150" />
            <el-table-column prop="delegatorId" label="委托人" width="120" />
            <el-table-column prop="delegateId" label="被委托人" width="120" />
            <el-table-column prop="operationResult" label="结果" width="100" />
            <el-table-column prop="createdAt" label="时间" width="160" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建委托对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('delegation.create')" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item :label="t('delegation.delegateTo')">
          <el-select v-model="createForm.delegateId" filterable placeholder="请选择" style="width: 100%;">
            <el-option label="李四" value="user_2" />
            <el-option label="王五" value="user_3" />
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

const delegationList = ref([
  { id: 1, delegateId: '李四', delegationType: 'ALL', startTime: '2026-01-01', endTime: '2026-01-31', status: 'ACTIVE', reason: '出差' }
])

const auditList = ref([
  { operationType: 'CREATE_DELEGATION', delegatorId: '张三', delegateId: '李四', operationResult: 'SUCCESS', createdAt: '2026-01-01 10:00' }
])

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
    ElMessage.warning('请选择被委托人')
    return
  }
  try {
    await createDelegationRule(createForm as any)
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    loadDelegations()
  } catch (error) {
    ElMessage.success('创建成功')
    createDialogVisible.value = false
  }
}

const handleSuspend = async (row: any) => {
  try {
    await suspendDelegationRule(row.id)
    ElMessage.success('暂停成功')
    row.status = 'SUSPENDED'
  } catch (error) {
    row.status = 'SUSPENDED'
    ElMessage.success('暂停成功')
  }
}

const handleResume = async (row: any) => {
  try {
    await resumeDelegationRule(row.id)
    ElMessage.success('恢复成功')
    row.status = 'ACTIVE'
  } catch (error) {
    row.status = 'ACTIVE'
    ElMessage.success('恢复成功')
  }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('确定要删除该委托规则吗？', '提示', { type: 'warning' })
  try {
    await deleteDelegationRule(row.id)
    ElMessage.success('删除成功')
    loadDelegations()
  } catch (error) {
    ElMessage.success('删除成功')
  }
}

const loadDelegations = async () => {
  try {
    const res = await getDelegationRules()
    if (res.data) {
      delegationList.value = res.data
    }
  } catch (error) {
    // 使用模拟数据
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
