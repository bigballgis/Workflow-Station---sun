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
                <div class="stat-number">
                  <el-icon
                    v-if="loading"
                    class="is-loading dashboard-inline-loading"
                  >
                    <Loading />
                  </el-icon>
                  <router-link
                    v-else
                    to="/tasks"
                    class="stat-link"
                  >
                    {{ taskOverview.pendingCount }}
                  </router-link>
                </div>
                <div class="stat-label">
                  {{ t('dashboard.pendingTasks') }}
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number error">
                  <el-icon
                    v-if="loading"
                    class="is-loading dashboard-inline-loading"
                  >
                    <Loading />
                  </el-icon>
                  <template v-else>{{ taskOverview.overdueCount }}</template>
                </div>
                <div class="stat-label">
                  {{ t('dashboard.overdueTasks') }}
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number success">
                  <el-icon
                    v-if="loading"
                    class="is-loading dashboard-inline-loading"
                  >
                    <Loading />
                  </el-icon>
                  <router-link
                    v-else
                    to="/tasks/completed"
                    class="stat-link"
                  >
                    {{ taskOverview.completedTodayCount }}
                  </router-link>
                </div>
                <div class="stat-label">
                  {{ t('dashboard.completedToday') }}
                </div>
              </div>
            </el-col>
          </el-row>
          <el-divider />
          <div class="card-header">
            <span
              class="card-title team-title-link"
              @click="openTeamRequestsDialog"
            >{{ t('dashboard.teamTaskOverview') }}</span>
          </div>
          <el-row :gutter="16">
            <el-col :span="8">
              <div class="stat-item small">
                <div class="stat-value">
                  <el-icon
                    v-if="loading"
                    class="is-loading dashboard-inline-loading-sm"
                  >
                    <Loading />
                  </el-icon>
                  <template v-else>{{ taskOverview.teamPendingCount }}</template>
                </div>
                <div class="stat-label">
                  {{ t('dashboard.teamPendingTasks') }}
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item small">
                <div class="stat-value error">
                  <el-icon
                    v-if="loading"
                    class="is-loading dashboard-inline-loading-sm"
                  >
                    <Loading />
                  </el-icon>
                  <template v-else>{{ taskOverview.teamOverdueCount }}</template>
                </div>
                <div class="stat-label">
                  {{ t('dashboard.teamOverdueTasks') }}
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item small">
                <div class="stat-value success">
                  <el-icon
                    v-if="loading"
                    class="is-loading dashboard-inline-loading-sm"
                  >
                    <Loading />
                  </el-icon>
                  <template v-else>{{ taskOverview.teamCompletedTodayCount }}</template>
                </div>
                <div class="stat-label">
                  {{ t('dashboard.teamCompletedToday') }}
                </div>
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
                <div class="stat-number">
                  <el-icon
                    v-if="loading"
                    class="is-loading dashboard-inline-loading"
                  >
                    <Loading />
                  </el-icon>
                  <template v-else>{{ processOverview.initiatedCount }}</template>
                </div>
                <div class="stat-label">
                  {{ t('dashboard.initiatedProcesses') }}
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number warning">
                  <el-icon
                    v-if="loading"
                    class="is-loading dashboard-inline-loading"
                  >
                    <Loading />
                  </el-icon>
                  <template v-else>{{ processOverview.inProgressCount }}</template>
                </div>
                <div class="stat-label">
                  {{ t('dashboard.inProgressProcesses') }}
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <div class="stat-number success">
                  <el-icon
                    v-if="loading"
                    class="is-loading dashboard-inline-loading"
                  >
                    <Loading />
                  </el-icon>
                  <template v-else>{{ processOverview.completedThisMonthCount }}</template>
                </div>
                <div class="stat-label">
                  {{ t('dashboard.completedThisMonth') }}
                </div>
              </div>
            </el-col>
          </el-row>
          <el-divider />
          <div class="approval-rate">
            <span>{{ t('dashboard.approvalRate') }}</span>
            <el-progress
              :percentage="loading ? 0 : Math.round(processOverview.approvalRate * 100)"
              :stroke-width="10"
              color="var(--success-green)"
            />
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row
      :gutter="20"
      style="margin-top: 20px;"
    >
      <!-- 快捷操作 -->
      <el-col :span="8">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">{{ t('dashboard.quickActions') }}</span>
          </div>
          <div class="quick-actions">
            <div
              class="action-item"
              @click="$router.push('/processes')"
            >
              <el-icon
                :size="24"
                color="var(--hsbc-red)"
              >
                <Plus />
              </el-icon>
              <span>{{ t('menu.processes') }}</span>
            </div>
            <div
              class="action-item"
              @click="$router.push('/tasks')"
            >
              <el-icon
                :size="24"
                color="var(--info-blue)"
              >
                <List />
              </el-icon>
              <span>{{ t('menu.tasks') }}</span>
            </div>
            <div
              class="action-item"
              @click="$router.push('/my-applications')"
            >
              <el-icon
                :size="24"
                color="var(--success-green)"
              >
                <Document />
              </el-icon>
              <span>{{ t('menu.myApplications') }}</span>
            </div>
            <div
              class="action-item"
              @click="$router.push('/delegations')"
            >
              <el-icon
                :size="24"
                color="var(--warning-orange)"
              >
                <Share />
              </el-icon>
              <span>{{ t('menu.delegations') }}</span>
            </div>
            <div
              class="action-item"
              @click="$router.push('/permissions')"
            >
              <el-icon
                :size="24"
                color="#722ed1"
              >
                <Key />
              </el-icon>
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
                :percentage="loading ? 0 : Math.round(performanceOverview.efficiencyScore)"
                :stroke-width="8"
                color="var(--hsbc-red)"
              />
            </div>
            <div class="score-item">
              <span class="score-label">{{ t('dashboard.qualityScore') }}</span>
              <el-progress
                :percentage="loading ? 0 : Math.round(performanceOverview.qualityScore)"
                :stroke-width="8"
                color="var(--success-green)"
              />
            </div>
            <div class="score-item">
              <span class="score-label">{{ t('dashboard.collaborationScore') }}</span>
              <el-progress
                :percentage="loading ? 0 : Math.round(performanceOverview.collaborationScore)"
                :stroke-width="8"
                color="var(--info-blue)"
              />
            </div>
          </div>
          <div class="rank-info">
            <span>{{ t('dashboard.monthlyRank') }}:</span>
            <span
              v-if="loading"
              class="rank-value rank-loading"
            >
              <el-icon class="is-loading dashboard-inline-loading-sm">
                <Loading />
              </el-icon>
            </span>
            <span
              v-else
              class="rank-value"
            >
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
            <el-button
              type="primary"
              link
              @click="$router.push('/tasks')"
            >
              {{ t('dashboard.viewAll') }}
            </el-button>
          </div>
          <div class="recent-tasks">
            <div
              v-for="task in recentTasks"
              :key="task.taskId"
              class="task-item"
            >
              <div class="task-info">
                <span class="task-name">{{ task.taskName }}</span>
                <span class="task-process">{{ task.processDefinitionName }}</span>
              </div>
              <el-tag
                :class="['priority-tag', getPriorityClass(task.priority)]"
                size="small"
              >
                {{ getPriorityLabel(task.priority) }}
              </el-tag>
            </div>
            <div
              v-if="loading && recentTasks.length === 0"
              class="recent-tasks-loading"
            >
              <el-icon class="is-loading">
                <Loading />
              </el-icon>
              <span>{{ t('common.loading') }}</span>
            </div>
            <el-empty
              v-else-if="recentTasks.length === 0"
              :description="t('dashboard.noTasks')"
            />
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Team Requests Dialog -->
    <el-dialog
      v-model="teamDialogVisible"
      :title="t('dashboard.teamRequestsTitle')"
      width="900px"
      destroy-on-close
    >
      <div class="team-summary">
        <div
          class="team-summary-item"
          @click="switchTeamTab('all')"
        >
          <div class="team-summary-value">
            {{ teamRequests.overallCount }}
          </div>
          <div class="team-summary-label">
            {{ t('dashboard.overallRequests') }}
          </div>
        </div>
        <div
          class="team-summary-item running"
          @click="switchTeamTab('RUNNING')"
        >
          <div class="team-summary-value">
            {{ teamRequests.runningCount }}
          </div>
          <div class="team-summary-label">
            {{ t('dashboard.runningRequests') }}
          </div>
        </div>
        <div
          class="team-summary-item completed"
          @click="switchTeamTab('COMPLETED')"
        >
          <div class="team-summary-value">
            {{ teamRequests.completedCount }}
          </div>
          <div class="team-summary-label">
            {{ t('dashboard.completedRequests') }}
          </div>
        </div>
        <div
          class="team-summary-item withdrawn"
          @click="switchTeamTab('WITHDRAWN')"
        >
          <div class="team-summary-value">
            {{ teamRequests.withdrawnCount }}
          </div>
          <div class="team-summary-label">
            {{ t('dashboard.withdrawnRequests') }}
          </div>
        </div>
      </div>

      <el-tabs
        v-model="teamActiveTab"
        @tab-change="handleTeamTabChange"
      >
        <el-tab-pane
          :label="t('dashboard.overallRequests')"
          name="all"
        />
        <el-tab-pane
          :label="t('dashboard.runningRequests')"
          name="RUNNING"
        />
        <el-tab-pane
          :label="t('dashboard.completedRequests')"
          name="COMPLETED"
        />
        <el-tab-pane
          :label="t('dashboard.withdrawnRequests')"
          name="WITHDRAWN"
        />
      </el-tabs>

      <el-table
        :data="teamRequests.content"
        :loading="teamLoading"
        stripe
        table-layout="fixed"
      >
        <el-table-column
          prop="businessKey"
          :label="t('application.processTitle')"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.businessKey || row.processDefinitionName }}
          </template>
        </el-table-column>
        <el-table-column
          prop="startUserName"
          :label="t('dashboard.initiator')"
          min-width="100"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.startUserName || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="currentNode"
          :label="t('application.currentStep')"
          min-width="120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.currentNode || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="currentAssignee"
          :label="t('application.currentAssignee')"
          min-width="100"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.currentAssignee || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="startTime"
          :label="t('application.startTime')"
          width="160"
        >
          <template #default="{ row }">
            {{ formatDate(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="status"
          :label="t('application.status')"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="getTeamStatusType(row.status)"
              size="small"
              effect="light"
            >
              {{ getTeamStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="teamPagination.page"
        v-model:page-size="teamPagination.size"
        :disabled="teamLoading"
        :total="teamRequests.totalElements"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end;"
        @current-change="handleTeamPageChange"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, List, Document, Share, Key, Loading } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/dateFormat'
import { useDashboardOverview } from '@/composables/dashboard/useDashboardOverview'
import { useTeamRequests } from '@/composables/dashboard/useTeamRequests'
import { useTaskPriority } from '@/composables/dashboard/useTaskPriority'

const { t } = useI18n()

// 仪表盘概览数据（任务 / 流程 / 绩效 / 最近任务）
const {
  loading,
  taskOverview,
  processOverview,
  performanceOverview,
  recentTasks,
  loadDashboardData
} = useDashboardOverview()

// 团队请求弹窗
const {
  teamDialogVisible,
  teamLoading,
  teamActiveTab,
  teamPagination,
  teamRequests,
  openTeamRequestsDialog,
  switchTeamTab,
  handleTeamTabChange,
  handleTeamPageChange,
  getTeamStatusType,
  getTeamStatusLabel
} = useTeamRequests()

// 最近任务优先级标签与样式
const { getPriorityLabel, getPriorityClass } = useTaskPriority()

onMounted(() => {
  loadDashboardData()
})
</script>

<style lang="scss" scoped>
.dashboard-page {
  .dashboard-inline-loading {
    font-size: 28px;
    vertical-align: middle;
  }

  .dashboard-inline-loading-sm {
    font-size: 22px;
    vertical-align: middle;
  }

  .recent-tasks-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 24px 0;
    color: var(--text-secondary);
    font-size: 14px;
  }

  .rank-loading {
    display: inline-flex;
    align-items: center;
    min-height: 24px;
  }

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

    .stat-link {
      color: inherit;
      text-decoration: none;
      &:hover {
        text-decoration: underline;
        cursor: pointer;
      }
    }
  }

  .team-title-link {
    cursor: pointer;
    &:hover {
      text-decoration: underline;
      color: var(--el-color-primary);
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

<style lang="scss">
.team-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;

  .team-summary-item {
    text-align: center;
    padding: 14px 8px;
    border-radius: 8px;
    background: #f5f7fa;
    cursor: pointer;
    transition: box-shadow 0.2s;

    &:hover {
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    }

    &.running {
      background: #fdf6ec;
      .team-summary-value { color: #e6a23c; }
    }
    &.completed {
      background: #f0f9eb;
      .team-summary-value { color: #00A651; }
    }
    &.withdrawn {
      background: #f4f4f5;
      .team-summary-value { color: #909399; }
    }

    .team-summary-value {
      font-size: 28px;
      font-weight: 700;
      color: var(--text-primary);
    }

    .team-summary-label {
      font-size: 13px;
      color: var(--text-secondary);
      margin-top: 4px;
    }
  }
}
</style>
