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
      <el-col :span="6" v-for="metric in systemMetrics" :key="metric.name">
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
          <span>告警列表</span>
          <el-button type="primary" size="small" @click="showAlertRuleDialog = true">告警规则</el-button>
        </div>
      </template>
      <el-table :data="alerts" max-height="300">
        <el-table-column prop="level" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="alertLevelType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="告警标题" />
        <el-table-column prop="source" label="来源" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="alertStatusType(row.status)" size="small">{{ alertStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" @click="handleAlert(row, 'CONFIRMED')">确认</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" link type="primary" @click="handleAlert(row, 'RESOLVED')">处理</el-button>
            <el-button link type="info" @click="showAlertDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <el-dialog v-model="showAlertRuleDialog" title="告警规则配置" width="600px">
      <el-table :data="alertRules">
        <el-table-column prop="name" label="规则名称" />
        <el-table-column prop="metric" label="监控指标" />
        <el-table-column prop="condition" label="触发条件" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const { t } = useI18n()

const timeRange = ref('24h')
const performanceChartRef = ref<HTMLElement>()
const businessChartRef = ref<HTMLElement>()
const showAlertRuleDialog = ref(false)

const systemMetrics = ref([
  { name: 'CPU使用率', value: 45, unit: '%', percentage: 45, color: '#409EFF' },
  { name: '内存使用率', value: 68, unit: '%', percentage: 68, color: '#67C23A' },
  { name: '磁盘使用率', value: 52, unit: '%', percentage: 52, color: '#E6A23C' },
  { name: '网络带宽', value: 120, unit: 'Mbps', percentage: 30, color: '#F56C6C' }
])

const alerts = ref([
  { id: '1', level: 'WARNING', title: 'CPU使用率超过80%', source: 'Server-01', status: 'PENDING', createdAt: '2026-01-05 10:30' },
  { id: '2', level: 'ERROR', title: '数据库连接池耗尽', source: 'DB-Master', status: 'CONFIRMED', createdAt: '2026-01-05 09:15' },
  { id: '3', level: 'INFO', title: '定时任务执行完成', source: 'Scheduler', status: 'RESOLVED', createdAt: '2026-01-05 08:00' }
])

const alertRules = ref([
  { id: '1', name: 'CPU告警', metric: 'cpu_usage', condition: '> 80%', enabled: true },
  { id: '2', name: '内存告警', metric: 'memory_usage', condition: '> 90%', enabled: true },
  { id: '3', name: '磁盘告警', metric: 'disk_usage', condition: '> 85%', enabled: true }
])

const alertLevelType = (level: string) => ({ INFO: 'info', WARNING: 'warning', ERROR: 'danger', CRITICAL: 'danger' }[level] || 'info')
const alertStatusType = (status: string) => ({ PENDING: 'warning', CONFIRMED: 'primary', RESOLVED: 'success' }[status] || 'info')
const alertStatusText = (status: string) => ({ PENDING: '待处理', CONFIRMED: '已确认', RESOLVED: '已解决' }[status] || status)

const handleAlert = (alert: any, status: string) => {
  alert.status = status
  ElMessage.success('操作成功')
}

const showAlertDetail = (alert: any) => {
  ElMessage.info(`查看告警详情: ${alert.title}`)
}

onMounted(() => {
  if (performanceChartRef.value) {
    const chart = echarts.init(performanceChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['CPU', '内存', '磁盘'] },
      xAxis: { type: 'category', data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00'] },
      yAxis: { type: 'value', max: 100 },
      series: [
        { name: 'CPU', type: 'line', data: [30, 25, 45, 60, 55, 40, 35], smooth: true },
        { name: '内存', type: 'line', data: [60, 62, 65, 70, 68, 65, 63], smooth: true },
        { name: '磁盘', type: 'line', data: [50, 50, 51, 52, 52, 52, 52], smooth: true }
      ]
    })
  }
  
  if (businessChartRef.value) {
    const chart = echarts.init(businessChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['在线用户', '活跃流程', '任务处理量'] },
      xAxis: { type: 'category', data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] },
      yAxis: { type: 'value' },
      series: [
        { name: '在线用户', type: 'bar', data: [120, 132, 101, 134, 90, 50, 30] },
        { name: '活跃流程', type: 'bar', data: [80, 92, 71, 94, 60, 30, 20] },
        { name: '任务处理量', type: 'line', data: [200, 232, 181, 234, 160, 80, 50], yAxisIndex: 0 }
      ]
    })
  }
})
</script>

<style scoped lang="scss">
.metric-card {
  text-align: center;
  
  .metric-value {
    font-size: 32px;
    font-weight: bold;
  }
  
  .metric-name {
    color: #909399;
    margin: 10px 0;
  }
}

.chart-container {
  height: 300px;
}

.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
