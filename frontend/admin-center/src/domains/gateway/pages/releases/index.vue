<template>
  <div class="gateway-page">
    <div class="page-header">
      <h2>{{ t('gateway.releases') }}</h2>
      <el-button type="primary" @click="showCreateDialog">
        {{ t('gateway.createRelease') }}
      </el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="releaseNo" :label="t('gateway.releaseNo')" width="180" />
      <el-table-column :label="t('gateway.state')" width="120">
        <template #default="{ row }">
          <el-tag :type="stateTagType(row.state)">{{ t(`gateway.${row.state}`) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('gateway.approvalStatus')" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.approvalStatus" :type="approvalTagType(row.approvalStatus)" size="small">
            {{ t(`gateway.${row.approvalStatus}`) }}
          </el-tag>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('gateway.description')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdBy" :label="t('gateway.operator')" width="120" />
      <el-table-column prop="createdAt" :label="t('common.createTime')" width="180" />
      <el-table-column :label="t('common.operation')" width="340">
        <template #default="{ row }">
          <el-button v-if="row.state === 'DRAFT'" link type="primary" @click="handleSubmitTesting(row)">
            {{ t('gateway.submitTesting') }}
          </el-button>
          <el-button v-if="row.state === 'TESTING'" link type="success" @click="handlePublish(row)">
            {{ t('gateway.publish') }}
          </el-button>
          <el-button v-if="row.state === 'PUBLISHED'" link type="warning" @click="showPromoteDialog(row)">
            {{ t('gateway.promote') }}
          </el-button>
          <el-button v-if="row.state === 'PUBLISHED'" link type="danger" @click="showRollbackDialog(row)">
            {{ t('gateway.rollback') }}
          </el-button>
          <el-button v-if="row.needApproval" link type="primary" @click="showRequestApproval(row)">
            {{ t('gateway.requestApproval') }}
          </el-button>
          <el-button v-if="row.approvalStatus === 'PENDING'" link type="success" @click="handleApprove(row)">
            {{ t('gateway.approve') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="fetchData"
    />

    <el-dialog v-model="dialogVisible" :title="t('gateway.createRelease')" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item :label="t('gateway.environment')">
          <el-select v-model="form.environmentId" placeholder="Select environment">
            <el-option label="DEV" :value="1" />
            <el-option label="SIT" :value="2" />
            <el-option label="UAT" :value="3" />
            <el-option label="PROD" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('gateway.description')">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rollbackDialogVisible" :title="t('gateway.rollback')" width="400px">
      <el-input v-model="rollbackReason" type="textarea" :placeholder="t('gateway.description')" />
      <template #footer>
        <el-button @click="rollbackDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" @click="handleRollback">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="promoteDialogVisible" :title="t('gateway.promote')" width="400px">
      <el-form :model="promoteForm" label-width="120px">
        <el-form-item :label="t('gateway.targetEnvironment')">
          <el-select v-model="promoteForm.targetEnvironmentCode" placeholder="Select target environment">
            <el-option label="SIT" value="SIT" />
            <el-option label="UAT" value="UAT" />
            <el-option label="PROD" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('gateway.description')">
          <el-input v-model="promoteForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="promoteDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handlePromote">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  listReleases, createRelease, submitTesting, publishRelease, rollbackRelease,
  promoteRelease, requestApproval, approveRelease
} from '@/domains/gateway/api/gateway'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const dialogVisible = ref(false)
const rollbackDialogVisible = ref(false)
const promoteDialogVisible = ref(false)
const rollbackReason = ref('')
const currentRollbackRow = ref<any>(null)
const currentPromoteRow = ref<any>(null)
const form = ref({ environmentId: 1, apiVersionIds: [1], description: '' })
const promoteForm = ref({ targetEnvironmentCode: 'SIT', description: '' })

const stateTagType = (state: string) => {
  const map: Record<string, string> = {
    DRAFT: 'info', TESTING: 'warning', PUBLISHED: 'success',
    ROLLED_BACK: 'danger', PROMOTED: ''
  }
  return map[state] || 'info'
}

const approvalTagType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'warning', APPROVED: 'success', DENIED: 'danger'
  }
  return map[status] || 'info'
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listReleases({ page: page.value - 1, size: size.value })
    tableData.value = (res.data.content || []).map((item: any) => ({
      ...item,
      needApproval: false, // determined by envCode or explicit check
      approvalStatus: null
    }))
    total.value = res.data.totalElements || 0
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  form.value = { environmentId: 1, apiVersionIds: [1], description: '' }
  dialogVisible.value = true
}

const handleCreate = async () => {
  try {
    await createRelease(form.value)
    ElMessage.success(t('common.success'))
    dialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
  }
}

const handleSubmitTesting = async (row: any) => {
  try {
    await submitTesting(row.id)
    ElMessage.success(t('common.success'))
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || t('gateway.invalidState'))
  }
}

const handlePublish = async (row: any) => {
  try {
    await ElMessageBox.confirm(t('gateway.publish') + '?', t('common.confirm'), { type: 'warning' })
    await publishRelease(row.id)
    ElMessage.success(t('common.success'))
    fetchData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
    }
  }
}

const showPromoteDialog = (row: any) => {
  currentPromoteRow.value = row
  promoteForm.value = { targetEnvironmentCode: 'SIT', description: '' }
  promoteDialogVisible.value = true
}

const handlePromote = async () => {
  try {
    await promoteRelease(currentPromoteRow.value.id, promoteForm.value)
    ElMessage.success(t('common.success'))
    promoteDialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
  }
}

const showRollbackDialog = (row: any) => {
  currentRollbackRow.value = row
  rollbackReason.value = ''
  rollbackDialogVisible.value = true
}

const handleRollback = async () => {
  try {
    await rollbackRelease(currentRollbackRow.value.id, {
      targetReleaseId: currentRollbackRow.value.id,
      reason: rollbackReason.value
    })
    ElMessage.success(t('common.success'))
    rollbackDialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
  }
}

const showRequestApproval = async (row: any) => {
  try {
    await ElMessageBox.prompt(t('gateway.approvalComment'), t('gateway.requestApproval'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel')
    }).then(async ({ value }) => {
      await requestApproval(row.id, { approverRole: 'GATEWAY_ADMIN', comment: value })
      ElMessage.success(t('common.success'))
      fetchData()
    })
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
    }
  }
}

const handleApprove = async (row: any) => {
  try {
    await ElMessageBox.confirm(t('gateway.approveConfirm'), t('common.confirm'), {
      confirmButtonText: t('gateway.approve'),
      cancelButtonText: t('gateway.deny'),
      distinguishCancelAndClose: true,
      type: 'warning'
    }).then(async () => {
      await approveRelease(row.id, { approved: true, comment: t('gateway.approved') })
    }).catch(async (action) => {
      if (action === 'cancel') {
        await approveRelease(row.id, { approved: false, comment: t('gateway.denied') })
      }
    })
    ElMessage.success(t('common.success'))
    fetchData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
    }
  }
}

onMounted(fetchData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.text-muted { color: #909399; }
</style>
