<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6" v-for="stat in stats" :key="stat.title">
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
          <template #header>系统概览</template>
          <div class="chart-container" ref="systemChartRef"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>最近活动</template>
          <el-timeline>
            <el-timeline-item v-for="activity in activities" :key="activity.id" :timestamp="activity.time">
              {{ activity.content }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'

const systemChartRef = ref<HTMLElement>()

const stats = [
  { title: '用户总数', value: '1,234', icon: 'User', color: '#409EFF' },
  { title: '部门数量', value: '56', icon: 'OfficeBuilding', color: '#67C23A' },
  { title: '角色数量', value: '28', icon: 'Key', color: '#E6A23C' },
  { title: '在线用户', value: '89', icon: 'Connection', color: '#F56C6C' }
]

const activities = [
  { id: 1, content: '管理员创建了新用户 zhangsan', time: '2026-01-05 10:30' },
  { id: 2, content: '系统配置已更新', time: '2026-01-05 09:15' },
  { id: 3, content: '新角色 "审批员" 已创建', time: '2026-01-04 16:45' },
  { id: 4, content: '部门 "技术部" 结构调整', time: '2026-01-04 14:20' }
]

onMounted(() => {
  if (systemChartRef.value) {
    const chart = echarts.init(systemChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] },
      yAxis: { type: 'value' },
      series: [
        { name: '活跃用户', type: 'line', data: [120, 132, 101, 134, 90, 230, 210], smooth: true },
        { name: '新增用户', type: 'bar', data: [20, 32, 11, 34, 10, 30, 21] }
      ]
    })
  }
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
