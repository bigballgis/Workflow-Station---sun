<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('gateway.mySubscriptions') }}</span>
      <el-button type="primary" @click="showRequestDialog"><el-icon><Plus /></el-icon>{{ t('gateway.requestSubscription') }}</el-button>
    </div>
    <el-card class="search-card">
      <el-tabs v-model="activeTab" @tab-change="fetchData">
        <el-tab-pane :label="t('gateway.myRequests')" name="requests" />
        <el-tab-pane :label="t('gateway.activeSubscriptions')" name="active" />
      </el-tabs>
    </el-card>
    <el-card class="table-card">
      <el-table v-if="activeTab === 'requests'" :data="requests" v-loading="loading" stripe>
        <el-table-column prop="id" :label="t('gateway.requestId')" width="80" />
        <el-table-column prop="applicationId" :label="t('gateway.appId')" width="100" />
        <el-table-column :label="t('gateway.status')" width="100"><template #default="{ row }"><el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="justification" :label="t('gateway.justification')" />
        <el-table-column prop="createdAt" :label="t('gateway.createdAt')" width="180" />
      </el-table>
      <el-table v-else :data="activeSubs" v-loading="loading" stripe>
        <el-table-column prop="id" :label="t('gateway.subscriptionId')" width="80" />
        <el-table-column prop="applicationId" :label="t('gateway.appId')" width="100" />
        <el-table-column prop="apiVersionId" :label="t('gateway.apiVersion')" width="120" />
        <el-table-column prop="environmentId" :label="t('gateway.envId')" width="100" />
        <el-table-column :label="t('gateway.status')" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column :label="t('gateway.actions')" width="100">
          <template #default="{ row }"><el-button link type="danger" @click="handleRevoke(row.id)">{{ t('gateway.revoke') }}</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="t('gateway.requestSubscription')" width="550px">
      <el-form :model="reqForm" label-width="120px">
        <el-form-item :label="t('gateway.application')" required><el-input v-model.number="reqForm.applicationId" /></el-form-item>
        <el-form-item :label="t('gateway.environment')" required><el-input v-model.number="reqForm.environmentId" /></el-form-item>
        <el-form-item :label="t('gateway.apiVersions')" required><el-input v-model="reqForm.apiVersionsStr" :placeholder="t('gateway.apiVersionsHint')" /></el-form-item>
        <el-form-item :label="t('gateway.justification')"><el-input v-model="reqForm.justification" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">{{ t('gateway.cancel') }}</el-button><el-button type="primary" @click="handleRequest">{{ t('gateway.submit') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listMySubscriptionRequests, listAppSubscriptions, createSubscriptionRequest, revokeSubscription } from '@/api/gateway'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()
const loading = ref(false), activeTab = ref('requests'), requests = ref<any[]>([]), activeSubs = ref<any[]>([])
const dialogVisible = ref(false), reqForm = ref<any>({})

const fetchData = async () => {
  loading.value = true
  try {
    if (activeTab.value === 'requests') {
      const res: any = await listMySubscriptionRequests({ page: 0, size: 50 })
      requests.value = res.content || []
    } else {
      // List active subscriptions (requires appId — for demo, fetch from first request)
      if (requests.value.length > 0) {
        const appId = requests.value[0].applicationId
        if (appId) {
          const res: any = await listAppSubscriptions(appId)
          activeSubs.value = Array.isArray(res) ? res : []
        }
      }
    }
  } catch (e: any) { ElMessage.error(e.message || t('common.error')) }
  finally { loading.value = false }
}

const showRequestDialog = () => {
  reqForm.value = { applicationId: null, environmentId: null, apiVersionsStr: '', justification: '' }
  dialogVisible.value = true
}

const handleRequest = async () => {
  try {
    const ids = reqForm.value.apiVersionsStr.split(',').map((s: string) => Number(s.trim())).filter((n: number) => !isNaN(n))
    await createSubscriptionRequest({
      applicationId: reqForm.value.applicationId,
      environmentId: reqForm.value.environmentId,
      apiVersionIds: ids,
      justification: reqForm.value.justification || ''
    })
    ElMessage.success(t('gateway.requestSubmitted'))
    dialogVisible.value = false
    fetchData()
  } catch (e: any) { ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error')) }
}

const handleRevoke = async (id: number) => {
  try {
    await ElMessageBox.confirm(t('gateway.confirmRevoke'), t('gateway.warning'), { type: 'warning' })
    await revokeSubscription(id)
    ElMessage.success(t('gateway.revoked'))
    fetchData()
  } catch { /* cancelled */ }
}

onMounted(fetchData)
</script>
