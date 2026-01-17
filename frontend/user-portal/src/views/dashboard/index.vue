<template>
  <div class="dashboard-page">
    <div class="page-header">
      <h1>{{ t('dashboard.title') }}</h1>
    </div>

    <el-row :gutter="20">
      <!-- 任务概览 -->
      <el-col :span="12">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">{{ t('dashboard.taskOverview') }}</span>
          </div>
          <el-row :gutter="16">
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number">{{ taskOverview.pendingCount }}</div>
                <div class="stat-label">{{ t('dashboard.pendingTasks') }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number error">{{ taskOverview.overdueCount }}</div>
                <div class="stat-label">{{ t('dashboard.overdueTasks') }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number success">{{ taskOverview.completedTodayCount }}</div>
                <div class="stat-label">{{ t('dashboard.completedToday') }}</div>
              </div>
            </el-col>
          </el-row>
          <el-divider />
          <el-row :gutter="16">
            <el-col :span="8">
              <div class="stat-item small">
                <div class="stat-value">{{ taskOverview.urgentCount }}</div>
                <div class="stat-label">{{ t('dashboard.urgentTasks') }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item small">
                <div class="stat-value">{{ taskOverview.highPriorityCount }}</div>
                <div class="stat-label">{{ t('dashboard.highPriorityTasks') }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item small">
                <div class="stat-value">{{ taskOverview.avgProcessingHours }}h</div>
                <div class="stat-label">{{ t('dashboard.avgProcessingTime') }}</div>
              </div>
            </el-col>
          </el-row>
        </div>
      </el-col>

      <!-- 流程概览 -->
      <el-col :span="12">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">{{ t('dashboard.processOverview') }}</span>
          </div>
          <el-row :gutter="16">
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number">{{ processOverview.initiatedCount }}</div>
                <div class="stat-label">{{ t('dashboard.initiatedProcesses') }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number warning">{{ processOverview.inProgressCount }}</div>
                <div class="stat-label">{{ t('dashboard.inProgressProcesses') }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number success">{{ processOverview.completedThisMonthCount }}</div>
                <div class="stat-label">{{ t('dashboard.completedThisMonth') }}</div>
              </div>
            </el-col>
          </el-row>
          <el-divider />
          <div class="approval-rate">
            <span>{{ t('dashboard.approvalRate') }}</span>
            <el-progress
              :percentage="Math.round(processOverview.approvalRate * 100)"
              :stroke-width="10"
              color="var(--success-green)"
            />
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 快捷操作 -->
      <el-col :span="8">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">{{ t('dashboard.quickActions') }}</span>
          </div>
          <div class="quick-actions">
            <div class="action-item" @click="$router.push('/processes')">
              <el-icon :size="24" color="var(--hsbc-red)"><Plus /></el-icon>
              <span>{{ t('menu.processes') }}</span>
            </div>
            <div class="action-item" @click="$router.push('/tasks')">
              <el-icon :size="24" color="var(--info-blue)"><List /></el-icon>
              <span>{{ t('menu.tasks') }}</span>
            </div>
            <div class="action-item" @click="$router.push('/my-applications')">
              <el-icon :size="24" color="var(--success-green)"><Document /></el-icon>
              <span>{{ t('menu.myApplications') }}</span>
            </div>
            <div class="action-item" @click="$router.push('/delegations')">
              <el-icon :size="24" color="var(--warning-orange)"><Share /></el-icon>
              <span>{{ t('menu.delegations') }}</span>
            </div>
            <div class="action-item" @click="$router.push('/permissions')">
              <el-icon :size="24" color="#722ed1"><Key /></el-icon>
              <span>{{ t('menu.permissions') }}</span>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 个人绩效 -->
      <el-col :span="8">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">{{ t('dashboard.performance') }}</span>
          </div>
          <div class="performance-scores">
            <div class="score-item">
              <span class="score-label">{{ t('dashboard.efficiencyScore') }}</span>
              <el-progress
                :percentage="Math.round(performanceOverview.efficiencyScore)"
                :stroke-width="8"
                color="var(--hsbc-red)"
              />
            </div>
            <div class="score-item">
              <span class="score-label">{{ t('dashboard.qualityScore') }}</span>
              <el-progress
                :percentage="Math.round(performanceOverview.qualityScore)"
                :stroke-width="8"
                color="var(--success-green)"
              />
            </div>
            <div class="score-item">
              <span class="score-label">{{ t('dashboard.collaborationScore') }}</span>
              <el-progress
                :percentage="Math.round(performanceOverview.collaborationScore)"
                :stroke-width="8"
                color="var(--info-blue)"
              />
            </div>
          </div>
          <div class="rank-info">
            <span>{{ t('dashboard.monthlyRank') }}:</span>
            <span class="rank-value">
              {{ t('dashboard.rankFormat', { rank: performanceOverview.monthlyRank, total: performanceOverview.totalUsers }) }}
            </span>
          </div>
        </div>
      </el-col>

      <!-- Recent Tasks -->
      <el-col :span="8">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">{{ t('dashboard.recentTasks') }}</span>
            <el-button type="primary" link @click="$router.push('/tasks')">{{ t('dashboard.viewAll') }}</el-button>
          </div>
          <div class="recent-tasks">
            <div v-for="task in recentTasks" :key="task.taskId" class="task-item">
              <div class="task-info">
                <span class="task-name">{{ task.taskName }}</span>
                <span class="task-process">{{ task.processDefinitionName }}</span>
              </div>
              <el-tag
                :class="['priority-tag', task.priority.toLowerCase()]"
                size="small"
              >
                {{ t(`task.${task.priority.toLowerCase()}`) }}
              </el-tag>
            </div>
            <el-empty v-if="recentTasks.length === 0" :description="t('dashboard.noTasks')" />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, List, Document, Share, Key } from '@element-plus/icons-vue'
import { getDashboardOverview, TaskOverview, ProcessOverview, PerformanceOverview } from '@/api/dashboard'

const { t } = useI18n()

const taskOverview = ref<TaskOverview>({
  pendingCount: 0,
  overdueCount: 0,
  completedTodayCount: 0,
  avgProcessingHours: 0,
  urgentCount: 0,
  highPriorityCount: 0
})

const processOverview = ref<ProcessOverview>({
  initiatedCount: 0,
  inProgressCount: 0,
  completedThisMonthCount: 0,
  approvalRate: 0,
  typeDistribution: {}
})

const performanceOverview = ref<PerformanceOverview>({
  efficiencyScore: 0,
  qualityScore: 0,
  collaborationScore: 0,
  monthlyRank: 0,
  totalUsers: 0
})

const recentTasks = ref<any[]>([])

const loadDashboardData = async () => {
  try {
    const res = await getDashboardOverview()
    // API 返回格式: { success: true, data: { taskOverview, processOverview, performanceOverview, recentTasks } }
    const data = res.data || res
    if (data) {
      taskOverview.value = data.taskOverview || taskOverview.value
      processOverview.value = data.processOverview || processOverview.value
      performanceOverview.value = data.performanceOverview || performanceOverview.value
      recentTasks.value = data.recentTasks || []
    }
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
  }
}

onMounted(() => {
  loadDashboardData()
})
</script>

<style lang="scss" scoped>
.dashboard-page {
  .page-header {
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  .stat-item {
    text-align: center;
    padding: 16px 0;
    
    &.small {
      padding: 8px 0;
      
      .stat-value {
        font-size: 20px;
        font-weight: 600;
        color: var(--text-primary);
      }
    }
    
    .stat-label {
      font-size: 14px;
      color: var(--text-secondary);
      margin-top: 8px;
    }
  }
  
  .approval-rate {
    display: flex;
    align-items: center;
    gap: 16px;
    
    span {
      white-space: nowrap;
      color: var(--text-secondary);
    }
    
    .el-progress {
      flex: 1;
    }
  }
  
  .quick-actions {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    
    .action-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 16px;
      border-radius: 8px;
      cursor: pointer;
      transition: background-color 0.2s;
      
      &:hover {
        background-color: var(--background-light);
      }
      
      span {
        font-size: 12px;
        color: var(--text-secondary);
      }
    }
  }
  
  .performance-scores {
    .score-item {
      margin-bottom: 16px;
      
      .score-label {
        display: block;
        font-size: 14px;
        color: var(--text-secondary);
        margin-bottom: 8px;
      }
    }
  }
  
  .rank-info {
    display: flex;
    justify-content: space-between;
    padding-top: 16px;
    border-top: 1px solid var(--border-color);
    font-size: 14px;
    color: var(--text-secondary);
    
    .rank-value {
      color: var(--hsbc-red);
      font-weight: 500;
    }
  }
  
  .recent-tasks {
    .task-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 0;
      border-bottom: 1px solid var(--border-color);
      
      &:last-child {
        border-bottom: none;
      }
      
      .task-info {
        display: flex;
        flex-direction: column;
        gap: 4px;
        
        .task-name {
          font-size: 14px;
          color: var(--text-primary);
        }
        
        .task-process {
          font-size: 12px;
          color: var(--text-secondary);
        }
      }
    }
  }
}
</style>
