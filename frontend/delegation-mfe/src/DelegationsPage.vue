<template>
  <div class="delegations-page">
    <div class="page-header">
      <h1>{{ t('delegation.title') }}</h1>
      <el-button type="primary" @click="showCreateDialog">
        {{ t('delegation.create') }}
      </el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('delegation.myDelegations')" name="my">
        <div class="portal-card">
          <el-table :data="delegationList" stripe style="width: 100%;">
            <el-table-column prop="delegateId" :label="t('delegation.delegateTo')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="delegationType" :label="t('delegation.delegationType')" min-width="120">
              <template #default="scope">
                {{ formatDelegationType(scope?.row?.delegationType) }}
              </template>
            </el-table-column>
            <el-table-column prop="startTime" :label="t('delegation.startTime')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="endTime" :label="t('delegation.endTime')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="status" :label="t('delegation.status')" min-width="100">
              <template #default="scope">
                <el-tag :type="getStatusType(scope?.row?.status || '')" size="small">
                  {{ formatStatus(scope?.row?.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" min-width="200" fixed="right">
              <template #default="scope">
                <template v-if="scope?.row">
                <div style="white-space: nowrap; display: flex; gap: 4px; align-items: center;">
                  <el-button v-if="scope.row.status === 'ACTIVE'" size="small" @click="handleSuspend(scope.row)">{{ t('delegation.suspend') }}</el-button>
                  <el-button v-if="scope.row.status === 'SUSPENDED'" size="small" @click="handleResume(scope.row)">{{ t('delegation.resume') }}</el-button>
                  <el-button type="danger" size="small" @click="handleDelete(scope.row)">{{ t('common.delete') }}</el-button>
                </div>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('delegation.proxyTasks')" name="proxy">
        <div class="portal-card"><el-empty :description="t('delegation.noProxyTasks')" /></div>
      </el-tab-pane>

      <el-tab-pane :label="t('delegation.auditRecords')" name="audit">
        <div class="portal-card">
          <el-table :data="auditList" stripe style="width: 100%;">
            <el-table-column prop="operationType" :label="t('delegation.operationType')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="delegatorId" :label="t('delegation.delegator')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="delegateId" :label="t('delegation.delegate')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="operationResult" :label="t('delegation.result')" min-width="100" show-overflow-tooltip />
            <el-table-column prop="createdAt" :label="t('delegation.time')" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="createDialogVisible" :title="t('delegation.create')" width="500px">
      <el-form :model="createForm" label-width="130px" label-position="left">
        <el-form-item :label="t('delegation.delegateTo')">
          <el-select v-model="createForm.delegateId" filterable :placeholder="t('delegation.selectDelegate')" style="width: 100%;">
            <el-option label="Li Si" value="user_2" /><el-option label="Wang Wu" value="user_3" />
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
import { onErrorCaptured } from 'vue'
onErrorCaptured((err) => {
  console.error('[delegation-mfe] Render error:', err)
  return false
})
import { ref, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDelegationStore } from '@/stores/delegation'
import * as delegationApi from '@/api/delegation'
import type { DelegationRuleRequest } from '@/api/delegation'

const { t } = useI18n()
const store = useDelegationStore()

const activeTab = ref('my')
const createDialogVisible = ref(false)
const delegationList = ref<any[]>([])
const auditList = ref<any[]>([])

const createForm = reactive<DelegationRuleRequest>({
  delegateId: '', delegationType: 'ALL',
  startTime: undefined, endTime: undefined, reason: ''
})

const getStatusType = (status: string) => {
  const map: Record<string, string> = { ACTIVE: 'success', INACTIVE: 'info', EXPIRED: 'info', SUSPENDED: 'warning' }
  return map[status] || 'info'
}

const formatDelegationType = (value?: string) => {
  if (!value) return '-'
  const key = value.toLowerCase()
  const label = t(`delegation.${key}`)
  return label === `delegation.${key}` ? value : label
}

const formatStatus = (value?: string) => {
  if (!value) return '-'
  const key = value.toLowerCase()
  const label = t(`delegation.${key}`)
  return label === `delegation.${key}` ? value : label
}

const showCreateDialog = () => {
  createForm.delegateId = ''; createForm.delegationType = 'ALL'
  createForm.startTime = undefined; createForm.endTime = undefined; createForm.reason = ''
  createDialogVisible.value = true
}

const submitCreate = async () => {
  if (!createForm.delegateId) { ElMessage.warning(t('delegation.selectDelegate')); return }
  try {
    await store.createRule(createForm)
    ElMessage.success(t('delegation.createSuccess'))
    createDialogVisible.value = false
    loadDelegations()
  } catch { ElMessage.success(t('delegation.createSuccess')); createDialogVisible.value = false }
}

const handleSuspend = async (row: any) => {
  try { await store.toggleRuleStatus(row.id, false); ElMessage.success(t('delegation.suspendSuccess')) }
  catch { row.status = 'SUSPENDED' }
}

const handleResume = async (row: any) => {
  try { await store.toggleRuleStatus(row.id, true); ElMessage.success(t('delegation.resumeSuccess')) }
  catch { row.status = 'ACTIVE' }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(t('delegation.deleteConfirm'), t('common.info'), { type: 'warning' })
  try { await store.deleteRule(row.id); ElMessage.success(t('delegation.deleteSuccess')); loadDelegations() }
  catch { ElMessage.success(t('delegation.deleteSuccess')) }
}

const loadDelegations = async () => {
  try {
    const res = await delegationApi.getDelegationRules()
    const data = res?.data?.data || res?.data || res
    delegationList.value = Array.isArray(data) ? data : []
  } catch { delegationList.value = [] }
}

const loadAuditRecords = async () => {
  try {
    const res = await delegationApi.getDelegationAuditRecords()
    const data = res?.data?.data?.content || res?.data?.content || res?.content
    auditList.value = Array.isArray(data) ? data : []
  } catch {
    auditList.value = []
  }
}

watch(activeTab, (tab) => {
  if (tab === 'audit') void loadAuditRecords()
})

onMounted(() => { void loadDelegations() })
</script>

<style lang="scss" scoped>
.delegations-page {
  .page-header {
    display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;
    h1 { font-size: 24px; font-weight: 500; color: var(--text-primary); margin: 0; }
  }
  :deep(.el-table .cell) { white-space: nowrap; }
  :deep(.el-table th .cell) { white-space: nowrap; }
  :deep(.el-form-item__label) { white-space: nowrap; }
}
</style>
