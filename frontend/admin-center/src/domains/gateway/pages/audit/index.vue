<template>
  <div class="gateway-page">
    <div class="page-header">
      <h2>{{ t('gateway.audit') }}</h2>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="operation" :label="t('gateway.operation')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.operation === 'PUBLISH' ? 'success' : row.operation === 'ROLLBACK' ? 'danger' : 'info'">
            {{ row.operation }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="result" :label="t('gateway.result')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
            {{ t(`gateway.${row.result}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="releaseId" :label="t('gateway.releaseNo')" width="120" />
      <el-table-column prop="runtimeRevision" :label="t('gateway.runtimeRevision')" width="200" />
      <el-table-column prop="operator" :label="t('gateway.operator')" width="120" />
      <el-table-column prop="createdAt" :label="t('common.createTime')" width="180" />
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="fetchData"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listAuditLogs } from '@/domains/gateway/api/gateway'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listAuditLogs({ page: page.value - 1, size: size.value })
    tableData.value = res.data.content || []
    total.value = res.data.totalElements || 0
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
</style>
