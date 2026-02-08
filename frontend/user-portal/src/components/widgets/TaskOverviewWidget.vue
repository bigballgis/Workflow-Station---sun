<template>
  <div class="task-overview-widget">
    <div class="stat-grid">
      <div class="stat-item pending">
        <div class="stat-value">{{ stats.pending }}</div>
        <div class="stat-label">{{ $t('dashboard.pendingTasks') }}</div>
      </div>
      <div class="stat-item overdue">
        <div class="stat-value">{{ stats.overdue }}</div>
        <div class="stat-label">{{ $t('dashboard.overdueTasks') }}</div>
      </div>
      <div class="stat-item completed">
        <div class="stat-value">{{ stats.completedToday }}</div>
        <div class="stat-label">{{ $t('dashboard.completedToday') }}</div>
      </div>
      <div class="stat-item urgent">
        <div class="stat-value">{{ stats.urgent }}</div>
        <div class="stat-label">{{ $t('dashboard.urgentTasks') }}</div>
      </div>
    </div>
    <div class="task-list" v-if="recentTasks.length > 0">
      <div class="list-header">
        <span>{{ $t('task.title') }}</span>
        <el-link type="primary" @click="goToTasks">{{ $t('common.more') }}</el-link>
      </div>
      <div
        v-for="task in recentTasks"
        :key="task.id"
        class="task-item"
        @click="goToTask(task.id)"
      >
        <span class="task-name">{{ task.name }}</span>
        <el-tag :type="getPriorityType(task.priority)" size="small">
          {{ task.priority }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useDashboardStore } from '@/stores/dashboard'

const router = useRouter()
const dashboardStore = useDashboardStore()

const stats = ref({
  pending: 0,
  overdue: 0,
  completedToday: 0,
  urgent: 0
})

const recentTasks = ref<Array<{ id: string; name: string; priority: string }>>([])

const getPriorityType = (priority: string) => {
  const types: Record<string, string> = {
    '紧急': 'danger',
    'URGENT': 'danger',
    'Urgent': 'danger',
    '高': 'warning',
    'HIGH': 'warning',
    'High': 'warning',
    '普通': 'info',
    'NORMAL': 'info',
    'Normal': 'info',
    '低': 'success',
    'LOW': 'success',
    'Low': 'success'
  }
  return types[priority] || 'info'
}

const goToTasks = () => {
  router.push('/tasks')
}

const goToTask = (id: string) => {
  router.push(`/tasks/${id}`)
}

onMounted(async () => {
  await dashboardStore.fetchOverview()
  stats.value = {
    pending: dashboardStore.overview?.pendingTasks || 0,
    overdue: dashboardStore.overview?.overdueTasks || 0,
    completedToday: dashboardStore.overview?.completedToday || 0,
    urgent: dashboardStore.overview?.urgentTasks || 0
  }
})
</script>

<style scoped lang="scss">
.task-overview-widget {
  height: 100%;
  display: flex;
  flex-direction: column;

  .stat-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    margin-bottom: 16px;

    .stat-item {
      padding: 12px;
      border-radius: 8px;
      text-align: center;

      &.pending {
        background: #e6f7ff;
        .stat-value { color: #1890ff; }
      }

      &.overdue {
        background: #fff2f0;
        .stat-value { color: #DB0011; }
      }

      &.completed {
        background: #f6ffed;
        .stat-value { color: #00A651; }
      }

      &.urgent {
        background: #fff7e6;
        .stat-value { color: #FF6600; }
      }

      .stat-value {
        font-size: 24px;
        font-weight: 700;
      }

      .stat-label {
        font-size: 12px;
        color: #909399;
        margin-top: 4px;
      }
    }
  }

  .task-list {
    flex: 1;
    overflow: auto;

    .list-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
      font-weight: 600;
      color: #303133;
    }

    .task-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 0;
      border-bottom: 1px solid #f0f0f0;
      cursor: pointer;

      &:hover {
        background: #fafafa;
      }

      .task-name {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 13px;
      }
    }
  }
}
</style>
