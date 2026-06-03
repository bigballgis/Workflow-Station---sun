<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('gateway.apis') }}</span>
      <el-button type="primary" @click="showCreateDialog">
        {{ t('gateway.createApi') }}
      </el-button>
    </div>

    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="apiCode" :label="t('gateway.apiCode')" width="160" />
        <el-table-column prop="name" :label="t('gateway.apiName')" min-width="200" />
        <el-table-column prop="domain" :label="t('gateway.domain')" width="160" />
        <el-table-column prop="basePath" :label="t('gateway.basePath')" width="200" />
        <el-table-column prop="protocol" :label="t('gateway.protocol')" width="100" />
        <el-table-column prop="status" :label="t('common.status')" width="100" />
        <el-table-column :label="t('common.operation')" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewApi(row)">{{ t('common.view') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="t('gateway.createApi')" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item :label="t('gateway.apiCode')">
          <el-input v-model="form.apiCode" />
        </el-form-item>
        <el-form-item :label="t('gateway.apiName')">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('gateway.domain')">
          <el-input v-model="form.domain" />
        </el-form-item>
        <el-form-item :label="t('gateway.basePath')">
          <el-input v-model="form.basePath" />
        </el-form-item>
        <el-form-item :label="t('gateway.protocol')">
          <el-select v-model="form.protocol">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="HTTPS" value="HTTPS" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listApis, createApi } from '@/api/gateway'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const dialogVisible = ref(false)
const form = ref({ apiCode: '', name: '', domain: '', basePath: '', protocol: 'HTTP', description: '' })

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listApis({ page: page.value - 1, size: size.value })
    tableData.value = res.content || []
    total.value = res.totalElements || 0
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  form.value = { apiCode: '', name: '', domain: '', basePath: '', protocol: 'HTTP', description: '' }
  dialogVisible.value = true
}

const handleCreate = async () => {
  try {
    await createApi(form.value)
    ElMessage.success(t('common.success'))
    dialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
  }
}

const viewApi = (row: any) => {
  ElMessage.info(`API detail: ${row.apiCode} (Phase 2)`)
}

onMounted(fetchData)
</script>

