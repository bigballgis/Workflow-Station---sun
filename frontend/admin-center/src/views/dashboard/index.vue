<template>
  <div class="dashboard">
    <el-row :gutter="20" v-loading="statsLoading">
      <el-col :span="6" v-for="stat in statsCards" :key="stat.titleKey">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-icon :size="40" :color="stat.color"><component :is="stat.icon" /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-title">{{ t(stat.titleKey) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>{{ t('dashboard.userTrends') }}</template>
          <div class="chart-container" ref="systemChartRef" v-loading="trendsLoading"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>{{ t('dashboard.recentActivities') }}</template>
          <el-timeline v-loading="activitiesLoading">
            <el-timeline-item v-for="activity in activities" :key="activity.id" :timestamp="activity.createdAt">
              {{ activity.description || `${activity.username} ${activity.action} ${activity.resourceName || activity.resourceType}` }}
            </el-timeline-item>
            <el-empty v-if="!activitiesLoading && activities.length === 0" :description="t('dashboard.noActivities')" />
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useDashboard } from '@/composables/modules/useDashboard'

const { t } = useI18n()

const {
  systemChartRef, statsLoading, activitiesLoading, trendsLoading,
  statsCards, activities,
} = useDashboard()
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
