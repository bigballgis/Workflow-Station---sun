<template>
  <div class="dashboard">
    <el-row :gutter="20" v-loading="statsLoading">
      <el-col :span="6" v-for="stat in statsCards" :key="stat.title">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-icon :size="40" :color="stat.color"><component :is="stat.icon" /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-title">{{ stat.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>用户趋势</template>
          <div class="chart-container" ref="systemChartRef" v-loading="trendsLoading"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>最近活动</template>
          <el-timeline v-loading="activitiesLoading">
            <el-timeline-item v-for="activity in activities" :key="activity.id" :timestamp="activity.createdAt">
              {{ activity.description || `${activity.username} ${activity.action} ${activity.resourceName || activity.resourceType}` }}
            </el-timeline-item>
            <el-empty v-if="!activitiesLoading && activities.length === 0" description="暂无活动记录" />
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getStats, getRecentActivities, getUserTrends, type DashboardStats, type RecentActivity, type UserTrend } from '@/api/dashboard'

const systemChartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
let refreshTimer: number | null = null

const statsLoading = ref(false)
const activitiesLoading = ref(false)
const trendsLoading = ref(false)

const stats = ref<DashboardStats | null>(null)
const activities = ref<RecentActivity[]>([])
const trends = ref<UserTrend[]>([])

const statsCards = computed(() => {
  if (!stats.value) {
    return [
      { title: '用户总数', value: '-', icon: 'User', color: '#409EFF' },
      { title: '业务单元数量', value: '-', icon: 'OfficeBuilding', color: '#67C23A' },
      { title: '角色数量', value: '-', icon: 'Key', color: '#E6A23C' },
      { title: '在线用户', value: '-', icon: 'Connection', color: '#F56C6C' }
    ]
  }
  return [
    { title: '用户总数', value: stats.value.totalUsers.toLocaleString(), icon: 'User', color: '#409EFF' },
    { title: '业务单元数量', value: stats.value.totalBusinessUnits.toLocaleString(), icon: 'OfficeBuilding', color: '#67C23A' },
    { title: '角色数量', value: stats.value.totalRoles.toLocaleString(), icon: 'Key', color: '#E6A23C' },
    { title: '在线用户', value: stats.value.onlineUsers.toLocaleString(), icon: 'Connection', color: '#F56C6C' }
  ]
})

const loadStats = async () => {
  statsLoading.value = true
  try {
    stats.value = await getStats()
  } catch (e) {
    console.error('Failed to load stats:', e)
  } finally {
    statsLoading.value = false
  }
}

const loadActivities = async () => {
  activitiesLoading.value = true
  try {
    activities.value = await getRecentActivities(10)
  } catch (e) {
    console.error('Failed to load activities:', e)
  } finally {
    activitiesLoading.value = false
  }
}

const loadTrends = async () => {
  trendsLoading.value = true
  try {
    trends.value = await getUserTrends(7)
    updateChart()
  } catch (e) {
    console.error('Failed to load trends:', e)
  } finally {
    trendsLoading.value = false
  }
}

const updateChart = () => {
  if (!chart || trends.value.length === 0) return
  
  const dates = trends.value.map(t => t.date)
  const activeUsers = trends.value.map(t => t.activeUsers)
  const newUsers = trends.value.map(t => t.newUsers)
  
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['活跃用户', '新增用户'] },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series: [
      { name: '活跃用户', type: 'line', data: activeUsers, smooth: true },
      { name: '新增用户', type: 'bar', data: newUsers }
    ]
  })
}

onMounted(async () => {
  if (systemChartRef.value) {
    chart = echarts.init(systemChartRef.value)
  }
  
  await Promise.all([loadStats(), loadActivities(), loadTrends()])
  
  // 每60秒刷新一次数据
  refreshTimer = window.setInterval(() => {
    loadStats()
    loadActivities()
  }, 60000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  chart?.dispose()
})
</script>

<style scoped lang="scss">
.stat-card {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-info {
  .stat-value {
    font-size: 28px;
    font-weight: bold;
    color: #303133;
  }
  .stat-title {
    color: #909399;
    margin-top: 5px;
  }
}

.chart-container {
  height: 300px;
}
</style>
