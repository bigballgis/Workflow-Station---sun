<template>
  <div class="dashboard-page">
    <!-- 命令栏：与 admin center 同构的无边框图标命令 -->
    <div class="command-bar">
      <button
        type="button"
        class="command"
        @click="router.push('/processes')"
      >
        <el-icon :size="16">
          <Plus />
        </el-icon>
        <span>{{ t('dashboard.startRequest') }}</span>
      </button>
      <button
        type="button"
        class="command"
        :disabled="loading"
        @click="refresh()"
      >
        <el-icon
          :size="16"
          :class="{ 'is-spinning': loading }"
        >
          <Refresh />
        </el-icon>
        <span>{{ t('dashboard.refresh') }}</span>
      </button>
      <span
        v-if="loadedAt"
        class="command-stamp"
      >{{ t('dashboard.updatedAt', { time: timeOfDay(loadedAt) }) }}</span>
    </div>

    <!-- 标题带 -->
    <h1 class="page-title">
      {{ greetingText }}
    </h1>
    <p class="page-intro">
      {{ introLine }}
    </p>

    <!-- 加载失败要出声，而不是退化成一屏 0 -->
    <div
      v-if="anyLoadFailed"
      class="load-error"
      role="alert"
    >
      <el-icon :size="16">
        <WarningFilled />
      </el-icon>
      <span class="load-error-text">{{ t('dashboard.loadFailed') }}</span>
      <button
        type="button"
        class="load-error-retry"
        @click="refresh()"
      >
        {{ t('dashboard.retry') }}
      </button>
    </div>

    <!-- ============ 主视觉：个人 / 团队两本账 ============ -->
    <section class="ledger">
      <div class="ledger-half">
        <header class="ledger-head">
          <h2 class="ledger-title">
            {{ t('dashboard.myRequests') }}
          </h2>
          <router-link
            to="/my-applications"
            class="ledger-link"
          >
            {{ t('dashboard.viewAll') }} →
          </router-link>
        </header>

        <div class="figures">
          <router-link
            v-for="figure in myRequestFigures"
            :key="figure.key"
            :to="figure.to"
            class="figure figure-link"
          >
            <span class="figure-num">{{ loading ? '–' : figure.value }}</span>
            <span class="figure-label">{{ figure.label }}</span>
          </router-link>
        </div>

        <div class="ledger-foot">
          <span class="foot-label">{{ t('dashboard.approvalRate') }}</span>
          <span class="meter">
            <span
              class="meter-fill"
              :style="{ width: loading ? '0%' : `${approvalPercent}%` }"
            />
          </span>
          <span class="foot-value">{{ loading ? '–' : `${approvalPercent}%` }}</span>
        </div>
      </div>

      <div class="ledger-half">
        <header class="ledger-head">
          <h2 class="ledger-title">
            {{ t('dashboard.myTeam') }}
          </h2>
          <button
            type="button"
            class="ledger-link"
            @click="openTeamRequestsDialog"
          >
            {{ t('dashboard.viewAll') }} →
          </button>
        </header>

        <div class="figures">
          <div class="figure">
            <span class="figure-num">{{ loading ? '–' : teamSummary.overallCount }}</span>
            <span class="figure-label">{{ t('dashboard.overallRequests') }}</span>
          </div>
          <div class="figure">
            <span class="figure-num">{{ loading ? '–' : teamSummary.runningCount }}</span>
            <span class="figure-label">{{ t('dashboard.runningRequests') }}</span>
          </div>
          <div class="figure">
            <span class="figure-num">{{ loading ? '–' : teamSummary.completedCount }}</span>
            <span class="figure-label">{{ t('dashboard.completedRequests') }}</span>
          </div>
          <div class="figure">
            <span class="figure-num">{{ loading ? '–' : teamSummary.withdrawnCount }}</span>
            <span class="figure-label">{{ t('dashboard.withdrawnRequests') }}</span>
          </div>
        </div>

        <!-- 构成条：三段按真实占比，读作「在途 / 已结 / 已撤」 -->
        <div
          v-if="composition.length > 0"
          class="ledger-foot"
        >
          <span
            class="composition"
            role="img"
            :aria-label="compositionLabel"
          >
            <span
              v-for="seg in composition"
              :key="seg.key"
              class="composition-seg"
              :class="`is-${seg.key}`"
              :style="{ width: `${seg.percent}%` }"
            />
          </span>
        </div>
        <p
          v-if="composition.length > 0"
          class="composition-legend"
        >
          <span
            v-for="seg in composition"
            :key="seg.key"
            class="legend-item"
          >
            <span
              class="legend-dot"
              :class="`is-${seg.key}`"
            />{{ seg.label }}
          </span>
        </p>
      </div>
    </section>

    <!-- ============ 我的申请 ============ -->
    <section class="block">
      <header class="block-head">
        <h2 class="block-title">
          {{ t('dashboard.recentRequests') }}
        </h2>
        <router-link
          to="/my-applications"
          class="block-link"
        >
          {{ t('dashboard.viewAll') }} →
        </router-link>
      </header>

      <el-skeleton
        v-if="loading && myRequests.length === 0"
        :rows="4"
        animated
      />

      <div
        v-else-if="myRequests.length > 0"
        class="table-scroll"
      >
        <table
          class="data-table"
        >
          <thead>
            <tr>
              <th scope="col">
                {{ t('dashboard.colRequest') }}
              </th>
              <th scope="col">
                {{ t('application.currentStep') }}
              </th>
              <th scope="col">
                {{ t('application.currentAssignee') }}
              </th>
              <th scope="col">
                {{ t('application.status') }}
              </th>
              <th scope="col">
                {{ t('application.startTime') }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in myRequests"
              :key="row.id"
              class="data-row"
              tabindex="0"
              role="link"
              @click="openRequest(row.id)"
              @keyup.enter="openRequest(row.id)"
            >
              <td class="cell-name">
                {{ requestLabel(row) }}
              </td>
              <td class="cell-muted">
                {{ row.currentStepName || row.currentNode || '—' }}
              </td>
              <td class="cell-muted">
                {{ row.currentAssignee || '—' }}
              </td>
              <td>
                <span
                  class="status"
                  :class="statusClass(row.status)"
                >
                  <span class="status-dot" />
                  {{ getTeamStatusLabel(row.status) }}
                </span>
              </td>
              <td class="cell-muted">
                {{ formatDate(row.startTime) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div
        v-else
        class="block-empty"
      >
        <p class="empty-text">
          {{ t('dashboard.noRequests') }}
        </p>
        <router-link
          to="/processes"
          class="empty-action"
        >
          {{ t('dashboard.startRequest') }}
        </router-link>
      </div>
    </section>

    <!-- ============ 团队申请 ============ -->
    <section class="block">
      <header class="block-head">
        <h2 class="block-title">
          {{ t('dashboard.teamActivity') }}
        </h2>
        <button
          type="button"
          class="block-link"
          @click="openTeamRequestsDialog"
        >
          {{ t('dashboard.viewAll') }} →
        </button>
      </header>

      <el-skeleton
        v-if="loading && teamRecent.length === 0"
        :rows="4"
        animated
      />

      <div
        v-else-if="teamRecent.length > 0"
        class="table-scroll"
      >
        <table
          class="data-table"
        >
          <thead>
            <tr>
              <th scope="col">
                {{ t('dashboard.colRequest') }}
              </th>
              <th scope="col">
                {{ t('dashboard.initiator') }}
              </th>
              <th scope="col">
                {{ t('application.currentStep') }}
              </th>
              <th scope="col">
                {{ t('application.currentAssignee') }}
              </th>
              <th scope="col">
                {{ t('application.status') }}
              </th>
              <th scope="col">
                {{ t('application.startTime') }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in teamRecent"
              :key="row.id"
            >
              <td class="cell-strong">
                {{ teamRequestLabel(row) }}
              </td>
              <td>{{ row.startUserName || '—' }}</td>
              <td class="cell-muted">
                {{ row.currentNode || '—' }}
              </td>
              <td class="cell-muted">
                {{ row.currentAssignee || '—' }}
              </td>
              <td>
                <span
                  class="status"
                  :class="statusClass(row.status)"
                >
                  <span class="status-dot" />
                  {{ getTeamStatusLabel(row.status) }}
                </span>
              </td>
              <td class="cell-muted">
                {{ formatDate(row.startTime) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div
        v-else
        class="block-empty"
      >
        <p class="empty-text">
          {{ t('dashboard.noTeamRequests') }}
        </p>
      </div>
    </section>

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
            {{ teamRequestLabel(row) }}
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
import { Plus, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/dateFormat'
import { getStoredUser } from '@/api/auth'
import type { TeamRequestItem } from '@/api/dashboard'
import type { ProcessInstance } from '@/api/process'
import { useDashboardOverview } from '@/composables/dashboard/useDashboardOverview'
import { useRequestsBoard } from '@/composables/dashboard/useRequestsBoard'
import { useTeamRequests } from '@/composables/dashboard/useTeamRequests'

const { t, locale } = useI18n()
const router = useRouter()

// 个人流程概览（发起 / 在途 / 本月完成 / 通过率）与待办计数（用于问候语下那句话）
const {
  loading: overviewLoading,
  loadFailed: overviewFailed,
  loadedAt,
  taskOverview,
  processOverview,
  loadDashboardData
} = useDashboardOverview()

// 首页两张申请预览表 + 团队汇总
const {
  loading: boardLoading,
  loadFailed: boardFailed,
  myRequests,
  teamSummary,
  teamRecent,
  loadRequestsBoard
} = useRequestsBoard()

// 团队请求弹窗（「View all」入口，自带分页与状态页签）
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

const loading = computed(() => overviewLoading.value || boardLoading.value)
const anyLoadFailed = computed(() => overviewFailed.value || boardFailed.value)

const refresh = () => {
  void loadDashboardData()
  void loadRequestsBoard()
}

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

const timeOfDay = (d: Date) => new Intl.DateTimeFormat(locale.value, {
  hour: '2-digit',
  minute: '2-digit'
}).format(d)

/**
 * H1 下面那句话：只报待办条数。逾期作为产品概念已淡出（To Do 列表的 Due Date 列、
 * 任务详情的逾期标签都已移除），这里再单独喊一句就会成为它最后一个突兀的出口。
 * 整句交给 i18n 拼（日期是参数），中英文的标点才各自对。
 */
const introLine = computed(() => {
  const date = dateLine.value
  if (overviewLoading.value || overviewFailed.value) return date
  const count = taskOverview.value.pendingCount
  // 第三个参数是复数计数：英文按 count 选单/复数形式，中文只有一种形式不受影响
  if (count > 0) return t('dashboard.introPending', { date, count }, count)
  return t('dashboard.introClear', { date })
})

const approvalPercent = computed(() => Math.round(processOverview.value.approvalRate * 100))

/**
 * The three MY REQUESTS figures are entry points into My Applications, each landing
 * on the tab that lists exactly what the number counts. `initiated` has no status
 * filter (it counts every request the user started); "completed this month" lands on
 * the Completed tab — the list has no month window, so it shows every completed one.
 */
const myRequestFigures = computed(() => [
  {
    key: 'initiated',
    value: processOverview.value.initiatedCount,
    label: t('dashboard.initiatedProcesses'),
    to: { path: '/my-applications' }
  },
  {
    key: 'inProgress',
    value: processOverview.value.inProgressCount,
    label: t('dashboard.inProgressProcesses'),
    to: { path: '/my-applications', query: { status: 'RUNNING' } }
  },
  {
    key: 'completedThisMonth',
    value: processOverview.value.completedThisMonthCount,
    label: t('dashboard.completedThisMonth'),
    to: { path: '/my-applications', query: { status: 'COMPLETED' } }
  },
  {
    key: 'draft',
    value: processOverview.value.draftCount,
    label: t('dashboard.myDrafts'),
    to: { path: '/my-applications', query: { status: 'DRAFT' } }
  }
])

/** 团队构成条：只画有量的那几段，占比按总数算；总数为 0 时整条不画。 */
const composition = computed(() => {
  const { overallCount, runningCount, completedCount, withdrawnCount } = teamSummary.value
  if (!overallCount) return []
  return [
    { key: 'running', count: runningCount, name: t('dashboard.runningRequests') },
    { key: 'completed', count: completedCount, name: t('dashboard.completedRequests') },
    { key: 'withdrawn', count: withdrawnCount, name: t('dashboard.withdrawnRequests') }
  ]
    .filter((part) => part.count > 0)
    .map((part) => ({
      key: part.key,
      label: `${part.count} ${part.name}`,
      percent: Math.round((part.count / overallCount) * 100)
    }))
})

const compositionLabel = computed(() => composition.value.map((seg) => seg.label).join(', '))

const statusClass = (status?: string) => `is-${(status || 'unknown').toLowerCase()}`

/** 申请的可读标识：优先主表配置的 Request ID，其次业务键，最后流程名。 */
const requestLabel = (row: ProcessInstance) =>
  row.requestId || row.businessKey || row.processDefinitionName

/** 团队那张表同理；两张表必须用同一套回退顺序，否则同一条申请在两处叫法不同。 */
const teamRequestLabel = (row: TeamRequestItem) =>
  row.requestId || row.businessKey || row.processDefinitionName

const openRequest = (id: string) => {
  if (id) router.push(`/applications/${id}`)
}

onMounted(() => {
  refresh()
})
</script>

<style lang="scss" scoped>
// 内容画布是主题灰，页面自己是一张白色卡面（沿用主题的卡片 token）
.dashboard-page {
  // 与 tasks / my-applications 一致：白卡铺满内容区，而不是留出右侧和下方的灰边
  min-height: 100%;
  padding: 20px 24px 28px;
  background: var(--ws-card-bg);
  border: 1px solid var(--ws-card-border);
  border-radius: var(--ws-radius-card);
}

// ==================== 命令栏 ====================
.command-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin: -4px 0 16px;
}

.command {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 12px;
  border: none;
  border-radius: 2px;
  background: transparent;
  color: var(--ws-text);
  font-size: 13.5px;
  cursor: pointer;

  &:hover:not(:disabled) { background: var(--background-light); }

  &:focus-visible {
    outline: 2px solid var(--hsbc-red);
    outline-offset: -2px;
  }

  &:disabled {
    color: var(--ws-text-muted);
    cursor: default;
  }
}

.command-stamp {
  margin-left: auto;
  font-size: 12px;
  color: var(--ws-text-muted);
}

.is-spinning { animation: dash-spin 0.8s linear infinite; }

@keyframes dash-spin {
  to { transform: rotate(360deg); }
}

// ==================== 标题带 ====================
.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ws-text);
}

.page-intro {
  margin: 6px 0 0;
  max-width: 720px;
  font-size: 13.5px;
  line-height: 1.55;
  color: var(--ws-text-secondary);
}

// ==================== 加载失败 ====================
.load-error {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 10px 14px;
  border: 1px solid rgba(219, 0, 17, 0.35);
  border-left: 3px solid var(--hsbc-red);
  background: rgba(219, 0, 17, 0.04);
  color: var(--hsbc-red);
  font-size: 13.5px;
}

.load-error-text { flex: 1; }

.load-error-retry {
  border: none;
  background: transparent;
  color: var(--hsbc-red);
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

// ==================== 主视觉：两本账 ====================
.ledger {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  margin-top: 26px;
  border-top: 1px solid var(--ws-line-strong);
  border-bottom: 1px solid var(--ws-line-strong);
}

.ledger-half {
  display: flex;
  flex-direction: column;
  padding: 22px 32px 24px 0;

  & + & {
    padding-right: 0;
    padding-left: 32px;
    border-left: 1px solid var(--ws-line);
  }
}

.ledger-head {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 20px;
}

.ledger-title {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ws-text-secondary);
}

.ledger-link {
  margin-left: auto;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--hsbc-red);
  font: inherit;
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;

  &:hover { text-decoration: underline; }
}

// 大数字是这一屏的主角：字号拉开，标签退到底下当注脚
.figures {
  display: flex;
  gap: 36px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}

.figure {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

// 三个数字是进 My Applications 对应分页的入口
.figure-link {
  color: inherit;
  text-decoration: none;
  cursor: pointer;

  &:hover .figure-num { color: var(--hsbc-red); }
  &:hover .figure-label { color: var(--ws-text); }

  &:focus-visible {
    outline: 2px solid var(--hsbc-red);
    outline-offset: 4px;
    border-radius: 2px;
  }
}

.figure-num {
  font-size: 40px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
  color: var(--ws-text);
}

.figure-label {
  font-size: 11.5px;
  line-height: 1.35;
  color: var(--ws-text-secondary);
}

.ledger-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
}

.foot-label {
  flex: 0 0 auto;
  font-size: 13px;
  color: var(--ws-text-secondary);
}

.foot-value {
  flex: 0 0 auto;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--ws-text);
}

.meter {
  flex: 1;
  min-width: 0;
  height: 3px;
  background: var(--ws-line-strong);
  overflow: hidden;
}

.meter-fill {
  display: block;
  height: 100%;
  background: var(--hsbc-red);
  transition: width 0.4s ease;
}

// 构成条：一段红 + 两档灰，红色只留给「还在跑」
.composition {
  display: flex;
  width: 100%;
  height: 8px;
  gap: 2px;
  overflow: hidden;
}

.composition-seg {
  display: block;
  height: 100%;

  // 三档要在白底上都分得出来：--ws-line-strong 做小圆点几乎看不见
  &.is-running { background: var(--hsbc-red); }
  &.is-completed { background: var(--ws-text-secondary); }
  &.is-withdrawn { background: var(--ws-text-muted); }
}

.composition-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 10px 0 0;
  font-size: 12.5px;
  color: var(--ws-text-secondary);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.legend-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;

  &.is-running { background: var(--hsbc-red); }
  &.is-completed { background: var(--ws-text-secondary); }
  &.is-withdrawn { background: var(--ws-text-muted); }
}

// ==================== 区块 ====================
.block { margin-top: 32px; }

.block-head {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 10px;
}

.block-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--ws-text);
}

.block-link {
  margin-left: auto;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--hsbc-red);
  font: inherit;
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;

  &:hover { text-decoration: underline; }
}

// ==================== 数据表 ====================
// 窄屏让表格自己横滚，页面本身不横滚
.table-scroll {
  overflow-x: auto;
  overscroll-behavior-x: contain;
}

.data-table {
  width: 100%;
  min-width: 660px;
  border-collapse: collapse;
  font-size: 13.5px;

  th {
    padding: 0 12px 8px;
    border-bottom: 1px solid var(--ws-line-strong);
    color: var(--ws-text-secondary);
    font-size: 12.5px;
    font-weight: 600;
    text-align: left;
    white-space: nowrap;
  }

  td {
    padding: 11px 12px;
    border-bottom: 1px solid var(--ws-line);
    color: var(--ws-text);
    vertical-align: middle;
  }

  th:first-child,
  td:first-child { padding-left: 0; }
}

.data-row {
  cursor: pointer;

  &:hover { background: var(--background-light); }

  &:focus-visible {
    outline: 2px solid var(--hsbc-red);
    outline-offset: -2px;
  }
}

// 第一列是入口，用品牌色标出来
.cell-name {
  color: var(--hsbc-red);
  font-weight: 500;
  white-space: nowrap;
}

// 团队那张表的行进不去详情，第一列就不能长得像链接
.cell-strong {
  font-weight: 500;
  white-space: nowrap;
}

.cell-muted { color: var(--ws-text-secondary); }

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  white-space: nowrap;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ws-text-muted);
}

.status.is-running .status-dot { background: var(--hsbc-red); }
.status.is-completed .status-dot { background: var(--ws-text-secondary); }
.status.is-withdrawn .status-dot { background: var(--ws-text-muted); }

.block-empty {
  padding: 26px 0 30px;
  border-top: 1px solid var(--ws-line-strong);

  .empty-text {
    margin: 0 0 12px;
    font-size: 13.5px;
    color: var(--ws-text-secondary);
  }
}

.empty-action {
  display: inline-block;
  color: var(--hsbc-red);
  font-size: 13.5px;
  font-weight: 600;
  text-decoration: none;

  &:hover { text-decoration: underline; }
}

// ==================== 团队弹窗 ====================
.team-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.team-summary-item {
  padding: 12px;
  border: 1px solid var(--ws-card-border);
  text-align: center;
  cursor: pointer;

  &:hover { border-color: var(--hsbc-red); }

  .team-summary-value {
    font-size: 22px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--ws-text);
  }

  .team-summary-label {
    margin-top: 2px;
    font-size: 12px;
    color: var(--ws-text-secondary);
  }
}

@media (max-width: 900px) {
  .ledger { grid-template-columns: 1fr; }

  .ledger-half {
    padding: 20px 0;

    & + & {
      padding-left: 0;
      border-left: none;
      border-top: 1px solid var(--ws-line);
    }
  }

  .figures { gap: 24px; }

  .figure-num { font-size: 32px; }

  .command-stamp {
    width: 100%;
    margin-left: 0;
  }

  .team-summary { grid-template-columns: repeat(2, 1fr); }
}

@media (prefers-reduced-motion: reduce) {
  .is-spinning { animation: none; }
  .meter-fill { transition: none; }
}
</style>
