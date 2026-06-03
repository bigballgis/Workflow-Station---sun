<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('gateway.driftDetection') }}</span>
      <el-button type="primary" @click="handleSync">
        {{ t('gateway.triggerSync') }}
      </el-button>
    </div>

    <el-card class="search-card">
      <div class="search-form">
        <el-select v-model="filterEnvId" :placeholder="t('gateway.environment')" clearable @change="fetchData" style="width: 200px">
          <el-option label="DEV" :value="1" />
          <el-option label="SIT" :value="2" />
          <el-option label="UAT" :value="3" />
          <el-option label="PROD" :value="4" />
        </el-select>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" :label="t('gateway.reportId')" width="80" />
        <el-table-column :label="t('gateway.environment')" width="100">
          <template #default="{ row }"><el-tag>{{ envLabel(row.environmentId) }}</el-tag></template>
        </el-table-column>
        <el-table-column :label="t('gateway.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('gateway.missing')" width="90" align="center">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.missingCount > 0 }">{{ row.missingCount }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('gateway.extra')" width="90" align="center">
          <template #default="{ row }">
            <span :class="{ 'text-warning': row.extraCount > 0 }">{{ row.extraCount }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('gateway.mismatch')" width="90" align="center">
          <template #default="{ row }">
            <span :class="{ 'text-warning': row.mismatchCount > 0 }">{{ row.mismatchCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('common.createTime')" width="180" />
        <el-table-column :label="t('common.operation')" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">{{ t('gateway.viewDetail') }}</el-button>
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

    <el-dialog v-model="detailVisible" :title="t('gateway.driftReport')" width="700px">
      <div v-if="detailReport" class="drift-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('gateway.reportId')">{{ detailReport.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('gateway.environment')">{{ envLabel(detailReport.environmentId) }}</el-descriptions-item>
          <el-descriptions-item :label="t('gateway.status')">{{ detailReport.status }}</el-descriptions-item>
          <el-descriptions-item :label="t('gateway.syncMode')">{{ detailReport.syncMode }}</el-descriptions-item>
          <el-descriptions-item :label="t('gateway.missing')">
            <el-tag type="danger">{{ detailReport.missingCount }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('gateway.extra')">
            <el-tag type="warning">{{ detailReport.extraCount }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('gateway.mismatch')">
            <el-tag type="warning">{{ detailReport.mismatchCount }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.createTime')">{{ detailReport.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="driftItems.missing.length" class="drift-section">
          <h4>{{ t('gateway.missing') }} ({{ driftItems.missing.length }})</h4>
          <el-table :data="driftItems.missing" size="small" max-height="200">
            <el-table-column prop="upstreamRef" label="Upstream Ref" />
            <el-table-column prop="version" label="Version" />
          </el-table>
        </div>
        <div v-if="driftItems.extra.length" class="drift-section">
          <h4>{{ t('gateway.extra') }} ({{ driftItems.extra.length }})</h4>
          <el-table :data="driftItems.extra" size="small" max-height="200">
            <el-table-column prop="upstreamRef" label="Upstream Ref" />
            <el-table-column prop="version" label="Version" />
          </el-table>
        </div>
        <div v-if="driftItems.mismatch.length" class="drift-section">
          <h4>{{ t('gateway.mismatch') }} ({{ driftItems.mismatch.length }})</h4>
          <el-table :data="driftItems.mismatch" size="small" max-height="200">
            <el-table-column label="Field">
              <template #default="{ row }">
                {{ row.desired?.upstreamRef }} v{{ row.desired?.version }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listDriftReports, triggerDriftSync, getDriftReport } from '@/api/gateway'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterEnvId = ref<number | undefined>()
const detailVisible = ref(false)
const detailReport = ref<any>(null)

const driftItems = computed(() => {
  if (!detailReport.value?.reportJson) return { missing: [], extra: [], mismatch: [] }
  const rj = detailReport.value.reportJson
  return {
    missing: rj.missing || [],
    extra: rj.extra || [],
    mismatch: rj.mismatch || []
  }
})

const envLabel = (id: number) => ({ 1: 'DEV', 2: 'SIT', 3: 'UAT', 4: 'PROD' }[id] || `#${id}`)

const fetchData = async () => {
  loading.value = true
  try {
    const params: any = { page: page.value - 1, size: size.value }
    if (filterEnvId.value) params.environmentId = filterEnvId.value
    const res = await listDriftReports(params)
    tableData.value = res.content || []
    total.value = res.totalElements || 0
  } finally {
    loading.value = false
  }
}

const handleSync = async () => {
  const envCode = filterEnvId.value
    ? ({ 1: 'DEV', 2: 'SIT', 3: 'UAT', 4: 'PROD' }[filterEnvId.value])
    : 'DEV'
  try {
    await triggerDriftSync({ environmentCode: envCode })
    ElMessage.success(t('common.success'))
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
  }
}

const showDetail = async (row: any) => {
  try {
    const res = await getDriftReport(row.id)
    detailReport.value = res.data
    detailVisible.value = true
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || t('common.failed'))
  }
}

onMounted(fetchData)
</script>

<style scoped>
.text-danger { color: #f56c6c; font-weight: bold; }
.text-warning { color: #e6a23c; font-weight: bold; }
.drift-detail { max-height: 70vh; overflow-y: auto; }
.drift-section { margin-top: 20px; }
.drift-section h4 { margin: 0 0 8px 0; }
</style>
