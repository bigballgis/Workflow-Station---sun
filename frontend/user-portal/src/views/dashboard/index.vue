<template>
  <div class="dashboard-page">
    <!-- 报头式「今日台账」：问候 + 日期 | 待办/逾期/今日完成 内联大数字 + 新建申请 CTA -->
    <header class="day-masthead">
      <div class="masthead-left">
        <h1 class="greeting">
          {{ greetingText }}
        </h1>
        <p class="dateline">
          {{ dateLine }}
        </p>
      </div>
      <div class="masthead-right">
        <div class="day-ledger">
          <router-link
            to="/tasks"
            class="ledger-item is-link"
          >
            <span class="ledger-num">{{ loading ? '–' : taskOverview.pendingCount }}</span>
            <span class="ledger-label">{{ t('dashboard.pendingTasks') }}</span>
          </router-link>
          <div
            class="ledger-item"
            :class="{ 'is-alert': !loading && taskOverview.overdueCount > 0 }"
          >
            <span class="ledger-num">{{ loading ? '–' : taskOverview.overdueCount }}</span>
            <span class="ledger-label">{{ t('dashboard.overdueTasks') }}</span>
          </div>
          <router-link
            to="/tasks/completed"
            class="ledger-item is-link"
          >
            <span class="ledger-num">{{ loading ? '–' : taskOverview.completedTodayCount }}</span>
            <span class="ledger-label">{{ t('dashboard.completedToday') }}</span>
          </router-link>
        </div>
        <el-button
          type="primary"
          round
          class="masthead-cta"
          @click="$router.push('/processes')"
        >
          {{ t('dashboard.startRequest') }}
        </el-button>
      </div>
    </header>
    <div class="masthead-rule">
      <span class="rule-accent" />
    </div>

    <el-row :gutter="20">
      <!-- 左栏：需要你处理（任务台账） -->
      <el-col
        :lg="16"
        :xs="24"
        class="col-main"
      >
        <section class="ledger-card tasks-card">
          <header class="card-head">
            <span class="eyebrow">{{ t('dashboard.needsAction') }}</span>
            <span
              v-if="!loading && taskOverview.pendingCount > 0"
              class="count-pill"
            >{{ taskOverview.pendingCount }}</span>
            <router-link
              to="/tasks"
              class="head-link"
            >
              {{ t('dashboard.viewAll') }} →
            </router-link>
          </header>

          <el-skeleton
            v-if="loading && recentTasks.length === 0"
            :rows="5"
            animated
            class="card-skeleton"
          />

          <ul
            v-else-if="recentTasks.length > 0"
            class="task-ledger"
          >
            <li
              v-for="task in recentTasks"
              :key="taskKey(task)"
              class="task-row"
              tabindex="0"
              role="link"
              @click="openTask(task)"
              @keyup.enter="openTask(task)"
            >
              <span
                class="prio-dot"
                :class="getPriorityClass(task.priority)"
              />
              <div class="task-text">
                <span class="task-name">{{ task.taskName || task.name }}</span>
                <span class="task-process">{{ task.processDefinitionName || task.processName }}</span>
              </div>
              <div class="task-meta">
                <span
                  v-if="task.dueDate"
                  class="task-due"
                >{{ t('dashboard.dueBy', { date: formatDate(task.dueDate) }) }}</span>
                <span
                  class="prio-tag"
                  :class="getPriorityClass(task.priority)"
                >{{ getPriorityLabel(task.priority) }}</span>
                <span class="row-arrow">→</span>
              </div>
            </li>
          </ul>

          <div
            v-else
            class="task-empty"
          >
            <p class="empty-text">
              {{ t('dashboard.noTasks') }}
            </p>
            <el-button
              round
              @click="$router.push('/processes')"
            >
              {{ t('dashboard.startRequest') }}
            </el-button>
          </div>
        </section>
      </el-col>

      <!-- 右栏：流程概览 / 团队台账 / 个人绩效 -->
      <el-col
        :lg="8"
        :xs="24"
        class="col-side"
      >
        <section class="ledger-card">
          <header class="card-head">
            <span class="eyebrow">{{ t('dashboard.processOverview') }}</span>
          </header>
          <div class="mini-ledger">
            <div class="mini-item">
              <span class="mini-num">{{ loading ? '–' : processOverview.initiatedCount }}</span>
              <span class="ledger-label">{{ t('dashboard.initiatedProcesses') }}</span>
            </div>
            <div class="mini-item">
              <span class="mini-num">{{ loading ? '–' : processOverview.inProgressCount }}</span>
              <span class="ledger-label">{{ t('dashboard.inProgressProcesses') }}</span>
            </div>
            <div class="mini-item">
              <span class="mini-num">{{ loading ? '–' : processOverview.completedThisMonthCount }}</span>
              <span class="ledger-label">{{ t('dashboard.completedThisMonth') }}</span>
            </div>
          </div>
          <div class="meter-row">
            <span class="meter-label">{{ t('dashboard.approvalRate') }}</span>
            <span class="meter">
              <span
                class="meter-fill"
                :style="{ width: loading ? '0%' : `${Math.round(processOverview.approvalRate * 100)}%` }"
              />
            </span>
            <span class="meter-value">{{ loading ? '–' : `${Math.round(processOverview.approvalRate * 100)}%` }}</span>
          </div>
        </section>

        <section class="ledger-card">
          <header class="card-head">
            <span class="eyebrow">{{ t('dashboard.teamTaskOverview') }}</span>
          </header>
          <div class="mini-ledger">
            <div class="mini-item">
              <span class="mini-num">{{ loading ? '–' : taskOverview.teamPendingCount }}</span>
              <span class="ledger-label">{{ t('dashboard.teamPendingTasks') }}</span>
            </div>
            <div
              class="mini-item"
              :class="{ 'is-alert': !loading && taskOverview.teamOverdueCount > 0 }"
            >
              <span class="mini-num">{{ loading ? '–' : taskOverview.teamOverdueCount }}</span>
              <span class="ledger-label">{{ t('dashboard.teamOverdueTasks') }}</span>
            </div>
            <div class="mini-item">
              <span class="mini-num">{{ loading ? '–' : taskOverview.teamCompletedTodayCount }}</span>
              <span class="ledger-label">{{ t('dashboard.teamCompletedToday') }}</span>
            </div>
          </div>
          <button
            type="button"
            class="card-foot-link"
            @click="openTeamRequestsDialog"
          >
            {{ t('dashboard.viewTeamRequests') }} →
          </button>
        </section>

        <section class="ledger-card">
          <header class="card-head">
            <span class="eyebrow">{{ t('dashboard.performance') }}</span>
          </header>
          <div class="score-list">
            <div class="meter-row">
              <span class="meter-label">{{ t('dashboard.efficiencyScore') }}</span>
              <span class="meter">
                <span
                  class="meter-fill"
                  :style="{ width: loading ? '0%' : `${Math.round(performanceOverview.efficiencyScore)}%` }"
                />
              </span>
              <span class="meter-value">{{ loading ? '–' : Math.round(performanceOverview.efficiencyScore) }}</span>
            </div>
            <div class="meter-row">
              <span class="meter-label">{{ t('dashboard.qualityScore') }}</span>
              <span class="meter">
                <span
                  class="meter-fill"
                  :style="{ width: loading ? '0%' : `${Math.round(performanceOverview.qualityScore)}%` }"
                />
              </span>
              <span class="meter-value">{{ loading ? '–' : Math.round(performanceOverview.qualityScore) }}</span>
            </div>
            <div class="meter-row">
              <span class="meter-label">{{ t('dashboard.collaborationScore') }}</span>
              <span class="meter">
                <span
                  class="meter-fill"
                  :style="{ width: loading ? '0%' : `${Math.round(performanceOverview.collaborationScore)}%` }"
                />
              </span>
              <span class="meter-value">{{ loading ? '–' : Math.round(performanceOverview.collaborationScore) }}</span>
            </div>
          </div>
          <div class="rank-line">
            <span class="ledger-label">{{ t('dashboard.monthlyRank') }}</span>
            <span
              v-if="!loading"
              class="rank-value"
            >{{ t('dashboard.rankFormat', { rank: performanceOverview.monthlyRank, total: performanceOverview.totalUsers }) }}</span>
            <span
              v-else
              class="rank-value"
            >–</span>
          </div>
        </section>
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
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { formatDate } from '@/utils/dateFormat'
import { getStoredUser } from '@/api/auth'
import { useDashboardOverview } from '@/composables/dashboard/useDashboardOverview'
import { useTeamRequests } from '@/composables/dashboard/useTeamRequests'
import { useTaskPriority } from '@/composables/dashboard/useTaskPriority'

const { t, locale } = useI18n()
const router = useRouter()

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

// 报头问候：按当前时段选键，带用户显示名
const greetingText = computed(() => {
  const hour = new Date().getHours()
  const key = hour < 12 ? 'dashboard.greetingMorning'
    : hour < 18 ? 'dashboard.greetingAfternoon'
      : 'dashboard.greetingEvening'
  const user = getStoredUser()
  return t(key, { name: user?.displayName || user?.username || '' })
})

// 日期行随语言环境格式化（zh: 7月24日 星期五 / en: Friday, July 24）
const dateLine = computed(() => new Intl.DateTimeFormat(locale.value, {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date()))

// 后端最近任务字段有 taskId/taskName 与 id/name 两种形态，取键与跳转都做兼容
interface RecentTaskLike {
  taskId?: string
  id?: string
  taskName?: string
  name?: string
  processDefinitionName?: string
  processName?: string
  priority?: string | number
  dueDate?: string
}
const taskKey = (task: RecentTaskLike) => task.taskId || task.id
const openTask = (task: RecentTaskLike) => {
  const id = taskKey(task)
  if (id) router.push(`/tasks/${id}`)
}

onMounted(() => {
  loadDashboardData()
})
</script>

<style lang="scss" scoped>
// ==================== 报头台账 ====================
.day-masthead {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
  padding: 8px 4px 18px;
}

.greeting {
  margin: 0;
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--ws-text);
  line-height: 1.25;
}

.dateline {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ws-text-muted);
}

.masthead-right {
  display: flex;
  align-items: flex-end;
  gap: 28px;
  flex-wrap: wrap;
}

.day-ledger {
  display: flex;
  align-items: stretch;
}

.ledger-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 24px;
  text-decoration: none;

  & + .ledger-item {
    border-left: 1px solid var(--ws-line);
  }

  &.is-link:hover .ledger-num {
    color: var(--primary-color);
  }

  &.is-alert .ledger-num {
    color: var(--primary-color);
  }
}

.ledger-num {
  font-size: 32px;
  font-weight: 650;
  line-height: 1.1;
  color: var(--ws-text);
  font-variant-numeric: tabular-nums;
  transition: color 0.15s;
}

.ledger-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #8f8f8a;
  white-space: nowrap;
}

.masthead-cta {
  margin-bottom: 4px;
}

// 报头下 2px 墨色规则线 + 左端品牌红短段（呼应表头规则线母题）
.masthead-rule {
  position: relative;
  height: 2px;
  background: var(--ws-ink);
  margin-bottom: 20px;

  .rule-accent {
    position: absolute;
    left: 0;
    top: 0;
    width: 64px;
    height: 2px;
    background: var(--primary-color);
  }
}

// ==================== 账册卡通用 ====================
.ledger-card {
  background: var(--ws-card-bg);
  border: 1px solid var(--ws-card-border);
  border-radius: var(--ws-radius-card);
  padding: 18px 22px 20px;
  box-shadow: 0 1px 2px rgba(20, 20, 20, 0.04);

  .col-side & + .ledger-card {
    margin-top: 20px;
  }
}

.col-side .ledger-card:first-child {
  margin-top: 0;
}

// xs 下右栏整体与左栏拉开距离
@media (max-width: 1199px) {
  .col-side .ledger-card:first-child {
    margin-top: 20px;
  }
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;

  .eyebrow {
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #8f8f8a;
  }

  .count-pill {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 22px;
    height: 18px;
    padding: 0 7px;
    border-radius: 999px;
    background: var(--primary-soft);
    color: var(--primary-color);
    font-size: 12px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .head-link {
    margin-left: auto;
    font-size: 13px;
    color: var(--ws-text-secondary);
    text-decoration: none;

    &:hover {
      color: var(--primary-color);
    }
  }
}

.card-skeleton {
  padding: 4px 0;
}

// ==================== 任务台账列表 ====================
.task-ledger {
  list-style: none;
  margin: 0;
  padding: 0;
}

.task-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 8px;
  margin: 0 -8px;
  border-radius: 8px;
  border-bottom: 1px solid var(--ws-line);
  cursor: pointer;
  transition: background-color 0.15s;

  &:last-child {
    border-bottom: none;
  }

  &:hover,
  &:focus-visible {
    background-color: #f7f8fa;

    .row-arrow {
      opacity: 1;
      transform: translateX(0);
    }
  }

  &:focus-visible {
    outline: 2px solid var(--primary-color);
    outline-offset: -2px;
  }
}

.prio-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #9c9c9c;

  &.urgent { background: var(--primary-color); }
  &.high { background: #d98f00; }
  &.normal { background: #b8b8b4; }
  &.low { background: #d8d8d4; }
}

.task-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;

  .task-name {
    font-size: 14px;
    font-weight: 500;
    color: var(--ws-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .task-process {
    font-size: 12px;
    color: var(--ws-text-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;

  .task-due {
    font-size: 12px;
    color: var(--ws-text-muted);
    font-variant-numeric: tabular-nums;
  }
}

.prio-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;

  &.urgent { background: var(--primary-soft); color: #c00011; }
  &.high { background: #fbf0dd; color: #a36a00; }
  &.normal { background: #f0f0ee; color: #6f6f6f; }
  &.low { background: #f0f0ee; color: #9c9c9c; }
}

.row-arrow {
  color: var(--primary-color);
  font-size: 15px;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.15s, transform 0.15s;
}

.task-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 48px 0;

  .empty-text {
    margin: 0;
    font-size: 14px;
    color: var(--ws-text-muted);
  }
}

// ==================== 右栏迷你台账 / 量表 ====================
.mini-ledger {
  display: flex;
  align-items: stretch;
  margin-bottom: 16px;
}

.mini-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
  padding: 0 14px;

  &:first-child {
    padding-left: 0;
  }

  & + .mini-item {
    border-left: 1px solid var(--ws-line);
  }

  &.is-alert .mini-num {
    color: var(--primary-color);
  }

  .ledger-label {
    white-space: normal;
    line-height: 1.4;
  }
}

.mini-num {
  font-size: 22px;
  font-weight: 650;
  line-height: 1.2;
  color: var(--ws-text);
  font-variant-numeric: tabular-nums;
}

.meter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 12px;

  .meter-label {
    flex-shrink: 0;
    width: 5.5em;
    font-size: 13px;
    color: var(--ws-text-secondary);
  }

  .meter {
    flex: 1;
    height: 4px;
    border-radius: 2px;
    background: var(--primary-soft);
    overflow: hidden;
  }

  .meter-fill {
    display: block;
    height: 100%;
    border-radius: 2px;
    background: var(--primary-color);
    transition: width 0.5s ease-out;
  }

  .meter-value {
    flex-shrink: 0;
    min-width: 2.5em;
    text-align: right;
    font-size: 13px;
    font-weight: 600;
    color: var(--ws-text);
    font-variant-numeric: tabular-nums;
  }
}

.score-list .meter-row:first-child {
  padding-top: 0;
}

.card-foot-link {
  display: block;
  width: 100%;
  margin-top: 4px;
  padding: 8px 0 0;
  border: none;
  border-top: 1px solid var(--ws-line);
  background: none;
  text-align: left;
  font-size: 13px;
  color: var(--ws-text-secondary);
  cursor: pointer;

  &:hover {
    color: var(--primary-color);
  }
}

.rank-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--ws-line);

  .rank-value {
    font-size: 14px;
    font-weight: 600;
    color: var(--primary-color);
    font-variant-numeric: tabular-nums;
  }
}

// ==================== 入场动效（尊重 reduced-motion） ====================
@media (prefers-reduced-motion: no-preference) {
  .day-masthead,
  .masthead-rule {
    animation: rise 0.4s ease-out both;
  }

  .col-main .ledger-card {
    animation: rise 0.4s ease-out 0.06s both;
  }

  .col-side .ledger-card {
    animation: rise 0.4s ease-out both;

    &:nth-child(1) { animation-delay: 0.12s; }
    &:nth-child(2) { animation-delay: 0.18s; }
    &:nth-child(3) { animation-delay: 0.24s; }
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(8px);
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
      background: #fbf0dd;
      .team-summary-value { color: #a36a00; }
    }
    &.completed {
      background: #e6f5eb;
      .team-summary-value { color: #1f7a40; }
    }
    &.withdrawn {
      background: #f0f0ee;
      .team-summary-value { color: #6f6f6f; }
    }

    .team-summary-value {
      font-size: 28px;
      font-weight: 700;
      color: var(--text-primary);
      font-variant-numeric: tabular-nums;
    }

    .team-summary-label {
      font-size: 13px;
      color: var(--text-secondary);
      margin-top: 4px;
    }
  }
}
</style>
