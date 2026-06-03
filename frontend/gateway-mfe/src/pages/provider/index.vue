<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('gateway.providerConfig') }}</span>
    </div>
    <el-card class="search-card">
      <el-alert :title="t('gateway.providerInfo')" type="info" show-icon :closable="false" />
    </el-card>
    <el-card class="table-card">
      <el-table :data="envs" v-loading="loading" stripe>
        <el-table-column prop="envCode" :label="t('gateway.environmentCode')" width="100" />
        <el-table-column prop="name" :label="t('gateway.environmentName')" />
        <el-table-column :label="t('gateway.gatewayProvider')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.gatewayProvider === 'KONG' ? 'success' : row.gatewayProvider === 'APISIX' ? 'primary' : 'info'">{{ row.gatewayProvider }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="adminEndpoint" :label="t('gateway.adminEndpoint')" min-width="280" />
        <el-table-column :label="t('gateway.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showProviderDialog(row)">{{ t('gateway.switchProvider') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div class="filter-bar" style="margin-top: 20px;">
      <span class="text-muted">{{ t('gateway.supportedProviders') }}:</span>
      <el-tag v-for="p in providers" :key="p">{{ p }}</el-tag>
    </div>

    <el-dialog v-model="dialogVisible" :title="t('gateway.switchProvider')" width="450px">
      <el-form :model="providerForm" label-width="120px">
        <el-form-item :label="t('gateway.gatewayProvider')" required>
          <el-select v-model="providerForm.gatewayProvider">
            <el-option v-for="p in providers" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('gateway.adminEndpoint')" required>
          <el-input v-model="providerForm.adminEndpoint" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">{{ t('gateway.cancel') }}</el-button><el-button type="primary" @click="handleProviderSave">{{ t('gateway.save') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listProviders, updateEnvProvider } from '@/api/gateway'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const loading = ref(false), envs = ref<any[]>([]), providers = ref<string[]>([])
const dialogVisible = ref(false), providerForm = ref<any>({}), selectedEnvId = ref<number | null>(null)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await listProviders()
    providers.value = res || ['KONG', 'APISIX', 'ENVOY']
    // List environments (reuse listEnvironments API)
    const { listEnvironments } = await import('@/api/gateway')
    const envRes: any = await listEnvironments()
    envs.value = envRes?.content || envRes || []
  } catch (e: any) { ElMessage.error(e.message || t('common.error')) }
  finally { loading.value = false }
}

const showProviderDialog = (row: any) => {
  selectedEnvId.value = row.id
  providerForm.value = { gatewayProvider: row.gatewayProvider, adminEndpoint: row.adminEndpoint }
  dialogVisible.value = true
}

const handleProviderSave = async () => {
  if (!selectedEnvId.value) return
  try {
    await updateEnvProvider(selectedEnvId.value, providerForm.value)
    ElMessage.success(t('gateway.providerUpdated'))
    dialogVisible.value = false
    fetchData()
  } catch (e: any) { ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error')) }
}

onMounted(fetchData)
</script>
