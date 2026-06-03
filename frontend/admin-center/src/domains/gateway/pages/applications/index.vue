<template>
  <div class="gateway-page">
    <div class="page-header">
      <h2>{{ t('gateway.applications') }}</h2>
      <el-button type="primary" @click="showCreateDialog">
        {{ t('gateway.createApp') }}
      </el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="appCode" :label="t('gateway.appCode')" width="160" />
      <el-table-column prop="name" :label="t('gateway.appName')" min-width="200" />
      <el-table-column prop="owner" :label="t('gateway.owner')" width="140" />
      <el-table-column prop="status" :label="t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="viewApp(row)">{{ t('common.view') }}</el-button>
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

    <el-dialog v-model="dialogVisible" :title="t('gateway.createApp')" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item :label="t('gateway.appCode')">
          <el-input v-model="form.appCode" />
        </el-form-item>
        <el-form-item :label="t('gateway.appName')">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('gateway.owner')">
          <el-input v-model="form.owner" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listApps, createApp } from '@/domains/gateway/api/gateway'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const dialogVisible = ref(false)
const form = ref({ appCode: '', name: '', owner: '', description: '' })

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listApps({ page: page.value - 1, size: size.value })
    tableData.value = res.data.content || []
    total.value = res.data.totalElements || 0
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  form.value = { appCode: '', name: '', owner: '', description: '' }
  dialogVisible.value = true
}

const handleCreate = async () => {
  try {
    await createApp(form.value)
    ElMessage.success(t('common.success'))
    dialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
  }
}

const viewApp = (row: any) => {
  ElMessage.info(`App detail: ${row.appCode} (Phase 2)`)
}

onMounted(fetchData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
</style>
