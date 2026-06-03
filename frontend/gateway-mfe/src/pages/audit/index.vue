<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('gateway.audit') }}</span>
    </div>

    <el-card class="table-card">
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listAuditLogs } from '@/api/gateway'

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
    tableData.value = res.content || []
    total.value = res.totalElements || 0
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

