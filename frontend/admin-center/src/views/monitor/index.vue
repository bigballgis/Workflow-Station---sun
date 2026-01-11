<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.monitor') }}</span>
      <el-button-group>
        <el-button :type="timeRange === '1h' ? 'primary' : ''" @click="timeRange = '1h'">1小时</el-button>
        <el-button :type="timeRange === '24h' ? 'primary' : ''" @click="timeRange = '24h'">24小时</el-button>
        <el-button :type="timeRange === '7d' ? 'primary' : ''" @click="timeRange = '7d'">7天</el-button>
      </el-button-group>
    </div>
    
    <el-row :gutter="20">
      <el-col :span="6" v-for="metric in systemMetricCards" :key="metric.name">
        <el-card shadow="hover">
          <div class="metric-card">
            <div class="metric-value" :style="{ color: metric.color }">{{ metric.value }}{{ metric.unit }}</div>
            <div class="metric-name">{{ metric.name }}</div>
            <el-progress :percentage="metric.percentage" :color="metric.color" :show-text="false" />
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>系统性能趋势</template>
          <div class="chart-container" ref="performanceChartRef"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>业务指标</template>
          <div class="chart-container" ref="businessChartRef"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px">
      <template #header>
        <div class="alert-header">
          <span>告警列表 <el-badge :value="alerts.length" :hidden="alerts.length === 0" /></span>
          <el-button type="primary" size="small" @click="showAlertRuleDialog = true">告警规则</el-button>
        </div>
      </template>
      <el-table :data="alerts" max-height="300" v-loading="alertsLoading">
        <el-table-column prop="severity" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="alertLevelType(row.severity)" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="告警信息" show-overflow-tooltip />
        <el-table-column prop="metricName" label="指标" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="alertStatusType(row.status)" size="small">{{ alertStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" @click="handleAcknowledge(row)">确认</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" link type="primary" @click="handleResolve(row)">处理</el-button>
            <el-button link type="info" @click="showAlertDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <el-dialog v-model="showAlertRuleDialog" title="告警规则配置" width="700px">
      <el-table :data="alertRules" v-loading="rulesLoading">
        <el-table-column prop="name" label="规则名称" />
        <el-table-column prop="metricName" label="监控指标" />
        <el-table-column label="触发条件" width="150">
          <template #default="{ row }">{{ row.operator }} {{ row.threshold }}</template>
        </el-table-column>
        <el-table-column prop="severity" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="alertLevelType(row.severity)" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="alertDetailVisible" title="告警详情" width="500px">
      <el-descriptions :column="2" border v-if="currentAlert">
        <el-descriptions-item label="告警级别">
          <el-tag :type="alertLevelType(currentAlert.severity)">{{ currentAlert.severity }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="alertStatusType(currentAlert.status)">{{ alertStatusText(currentAlert.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="指标类型">{{ currentAlert.metricType }}</el-descriptions-item>
        <el-descriptions-item label="指标名称">{{ currentAlert.metricName }}</el-descriptions-item>
        <el-descriptions-item label="当前值">{{ currentAlert.currentValue }}</el-descriptions-item>
        <el-descriptions-item label="阈值">{{ currentAlert.threshold }}</el-descriptions-item>
        <el-descriptions-item label="告警信息" :span="2">{{ currentAlert.message }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ currentAlert.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  getAllMetrics,
  getActiveAlerts,
  getEnabledRules,
  acknowledgeAlert,
  resolveAlert,
  type Alert,
  type AlertRule,
  type AllMetrics
} from '@/api/monitor'

const { t } = useI18n()

const timeRange = ref('24h')
const performanceChartRef = ref<HTMLElement>()
const businessChartRef = ref<HTMLElement>()
const showAlertRuleDialog = ref(false)
const alertDetailVisible = ref(false)
const currentAlert = ref<Alert | null>(null)

const metricsLoading = ref(false)
const alertsLoading = ref(false)
const rulesLoading = ref(false)

const metrics = ref<AllMetrics | null>(null)
const alerts = ref<Alert[]>([])
const alertRules = ref<AlertRule[]>([])

let performanceChart: echarts.ECharts | null = null
let businessChart: echarts.ECharts | null = null
let refreshTimer: number | null = null

const systemMetricCards = computed(() => {
  if (!metrics.value?.system) {
    return [
      { name: 'CPU使用率', value: 0, unit: '%', percentage: 0, color: '#409EFF' },
      { name: '内存使用率', value: 0, unit: '%', percentage: 0, color: '#67C23A' },
      { name: '磁盘使用率', value: 0, unit: '%', percentage: 0, color: '#E6A23C' },
      { name: '网络流量', value: 0, unit: 'MB/s', percentage: 0, color: '#F56C6C' }
    ]
  }
  const sys = metrics.value.system
  const cpuUsage = sys.cpuUsage ?? 0
  const memoryUsage = sys.memoryUsage ?? 0
  const diskUsage = sys.diskUsage ?? 0
  const networkIn = sys.networkIn ?? 0
  const networkOut = sys.networkOut ?? 0
  return [
    { name: 'CPU使用率', value: cpuUsage.toFixed(1), unit: '%', percentage: cpuUsage, color: cpuUsage > 80 ? '#F56C6C' : '#409EFF' },
    { name: '内存使用率', value: memoryUsage.toFixed(1), unit: '%', percentage: memoryUsage, color: memoryUsage > 80 ? '#F56C6C' : '#67C23A' },
    { name: '磁盘使用率', value: diskUsage.toFixed(1), unit: '%', percentage: diskUsage, color: diskUsage > 80 ? '#F56C6C' : '#E6A23C' },
    { name: '网络流量', value: ((networkIn + networkOut) / 1024 / 1024).toFixed(1), unit: 'MB/s', percentage: Math.min(((networkIn + networkOut) / 1024 / 1024 / 100) * 100, 100), color: '#909399' }
  ]
})

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const alertLevelType = (level: string): TagType => ({ INFO: 'info', WARNING: 'warning', ERROR: 'danger', CRITICAL: 'danger' }[level] as TagType || 'info')
const alertStatusType = (status: string): TagType => ({ PENDING: 'warning', CONFIRMED: 'primary', RESOLVED: 'success' }[status] as TagType || 'info')
const alertStatusText = (status: string) => ({ PENDING: '待处理', CONFIRMED: '已确认', RESOLVED: '已解决' }[status] || status)

const loadMetrics = async () => {
  metricsLoading.value = true
  try {
    metrics.value = await getAllMetrics()
    updateCharts()
  } catch (e) {
    console.error('Failed to load metrics:', e)
  } finally {
    metricsLoading.value = false
  }
}

const loadAlerts = async () => {
  alertsLoading.value = true
  try {
    alerts.value = await getActiveAlerts()
  } catch (e) {
    console.error('Failed to load alerts:', e)
  } finally {
    alertsLoading.value = false
  }
}

const loadAlertRules = async () => {
  rulesLoading.value = true
  try {
    alertRules.value = await getEnabledRules()
  } catch (e) {
    console.error('Failed to load alert rules:', e)
  } finally {
    rulesLoading.value = false
  }
}

const handleAcknowledge = async (alert: Alert) => {
  try {
    await acknowledgeAlert(alert.id)
    ElMessage.success('告警已确认')
    loadAlerts()
  } catch (e) {
    console.error('Failed to acknowledge alert:', e)
  }
}

const handleResolve = async (alert: Alert) => {
  try {
    await resolveAlert(alert.id)
    ElMessage.success('告警已处理')
    loadAlerts()
  } catch (e) {
    console.error('Failed to resolve alert:', e)
  }
}

const showAlertDetail = (alert: Alert) => {
  currentAlert.value = alert
  alertDetailVisible.value = true
}

const updateCharts = () => {
  if (!metrics.value) return

  if (performanceChart && metrics.value.system) {
    const sys = metrics.value.system
    performanceChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['CPU', '内存', '磁盘'] },
      xAxis: { type: 'category', data: ['当前'] },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [
        { name: 'CPU', type: 'bar', data: [sys.cpuUsage], itemStyle: { color: '#409EFF' } },
        { name: '内存', type: 'bar', data: [sys.memoryUsage], itemStyle: { color: '#67C23A' } },
        { name: '磁盘', type: 'bar', data: [sys.diskUsage], itemStyle: { color: '#E6A23C' } }
      ]
    })
  }

  if (businessChart && metrics.value.business) {
    const biz = metrics.value.business
    businessChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', left: 'left' },
      series: [{
        name: '业务指标',
        type: 'pie',
        radius: '60%',
        data: [
          { value: biz.onlineUsers, name: `在线用户 (${biz.onlineUsers})` },
          { value: biz.activeProcesses, name: `活跃流程 (${biz.activeProcesses})` },
          { value: biz.pendingTasks, name: `待办任务 (${biz.pendingTasks})` },
          { value: biz.completedTasksToday, name: `今日完成 (${biz.completedTasksToday})` }
        ],
        emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
      }]
    })
  }
}

const initCharts = () => {
  if (performanceChartRef.value) {
    performanceChart = echarts.init(performanceChartRef.value)
  }
  if (businessChartRef.value) {
    businessChart = echarts.init(businessChartRef.value)
  }
}

onMounted(async () => {
  initCharts()
  await Promise.all([loadMetrics(), loadAlerts(), loadAlertRules()])
  refreshTimer = window.setInterval(() => {
    loadMetrics()
    loadAlerts()
  }, 30000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  performanceChart?.dispose()
  businessChart?.dispose()
})
</script>

<style scoped lang="scss">
.metric-card {
  text-align: center;
  .metric-value { font-size: 32px; font-weight: bold; }
  .metric-name { color: #909399; margin: 10px 0; }
}
.chart-container { height: 300px; }
.alert-header { display: flex; justify-content: space-between; align-items: center; }
</style>
