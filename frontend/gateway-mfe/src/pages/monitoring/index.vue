<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('gateway.monitoring') }}</span>
    </div>
    <el-card class="search-card">
      <div class="search-form">
        <el-select v-model="envCode" @change="fetchOverview" style="width: 140px">
          <el-option label="DEV" value="DEV" />
          <el-option label="SIT" value="SIT" />
          <el-option label="UAT" value="UAT" />
          <el-option label="PROD" value="PROD" />
        </el-select>
        <el-select v-model="period" @change="fetchOverview" style="width: 120px">
          <el-option label="1h" value="1h" />
          <el-option label="6h" value="6h" />
          <el-option label="24h" value="24h" />
          <el-option label="7d" value="7d" />
        </el-select>
      </div>
    </el-card>

    <el-row :gutter="16" v-if="overview">
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">{{ t('gateway.qps') }}</div>
          <div class="metric-value">{{ formatNum(overview.qps) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">{{ t('gateway.p50Latency') }}</div>
          <div class="metric-value">{{ formatNum(overview.p50LatencyMs) }} ms</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">{{ t('gateway.p95Latency') }}</div>
          <div class="metric-value">{{ formatNum(overview.p95LatencyMs) }} ms</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">{{ t('gateway.errorRate') }}</div>
          <div class="metric-value" :class="{ 'text-danger': overview.errorRate > 0.05 }">
            {{ (overview.errorRate * 100).toFixed(2) }}%
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div v-else-if="!loading" class="empty-state">
      {{ t('gateway.noData') }}
    </div>

    <el-card class="table-card api-metrics-section" v-if="overview">
      <h3>{{ t('gateway.apiMetrics') }}</h3>
      <el-table :data="apiMetrics" v-loading="apiLoading" stripe>
        <el-table-column prop="apiDefinitionId" :label="t('gateway.apiId')" width="80" />
        <el-table-column prop="qps" :label="t('gateway.qps')" width="120" />
        <el-table-column prop="p50LatencyMs" :label="t('gateway.p50Latency')" width="120" />
        <el-table-column prop="p95LatencyMs" :label="t('gateway.p95Latency')" width="120" />
        <el-table-column :label="t('gateway.errorRate')" width="120">
          <template #default="{ row }">
            {{ ((row.errorRate || 0) * 100).toFixed(2) }}%
          </template>
        </el-table-column>
        <el-table-column prop="periodEnd" :label="t('gateway.timeWindow')" width="180" />
      </el-table>
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="apiPage"
          v-model:page-size="apiSize"
          :total="apiTotal"
          layout="total, prev, pager, next"
          small
          @current-change="fetchApiMetrics"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getMonitoringOverview, getApiMetrics } from '@/api/gateway'

const { t } = useI18n()

const loading = ref(false)
const apiLoading = ref(false)
const envCode = ref('DEV')
const period = ref('1h')
const overview = ref<any>(null)
const apiMetrics = ref<any[]>([])
const apiPage = ref(1)
const apiSize = ref(10)
const apiTotal = ref(0)

const formatNum = (n: number) => (n != null ? n.toFixed(2) : '0.00')

const fetchOverview = async () => {
  loading.value = true
  try {
    const res = await getMonitoringOverview({ environmentCode: envCode.value, period: period.value })
    overview.value = res
  } catch (e: any) {
    overview.value = null
  } finally {
    loading.value = false
  }
  fetchApiMetrics()
}

const fetchApiMetrics = async () => {
  apiLoading.value = true
  try {
    const res = await getApiMetrics(0, {
      environmentCode: envCode.value,
      period: period.value,
      page: apiPage.value - 1,
      size: apiSize.value
    })
    apiMetrics.value = res.content || []
    apiTotal.value = res.totalElements || 0
  } catch {
    apiMetrics.value = []
  } finally {
    apiLoading.value = false
  }
}

onMounted(fetchOverview)
</script>

<style scoped>
.metric-card { text-align: center; }
.metric-label { font-size: 13px; color: #909399; margin-bottom: 8px; }
.metric-value { font-size: 28px; font-weight: 700; color: #303133; }
.text-danger { color: #f56c6c; }
.empty-state { text-align: center; padding: 60px 0; color: #909399; }
.api-metrics-section { margin-top: 32px; }
.api-metrics-section h3 { margin-bottom: 12px; }
</style>
