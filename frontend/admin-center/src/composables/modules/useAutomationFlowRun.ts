/**
 * Automation Runs 页：共享列表 + 单次运行详情。
 *
 * 这页原本在 Developer Workstation 的 Automation → Run History。DW 只在 dev 存在
 * （不进 K8S 部署集），而"某次自动化为什么失败"是生产运维问题，故与 piece 目录、
 * flow 迁移一样收在 Admin Center。只读：没有重跑/取消，运行本身仍由 AP 负责。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  automationFlowRunApi,
  type AutomationFlowRunSummary,
  type AutomationRunStatus,
} from '@/api/automationFlowRun'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'
import { formatDate } from '@/utils/format'

/** 窄屏只留"哪个流程 + 什么结果"，其余进副标题 */
const COMPACT_FIELDS = new Set(['flowDisplayName', 'status'])

type StatusTone = { type: 'success' | 'danger' | 'warning' | 'info'; effect: 'light' | 'plain' }

/** AP 的 11 个状态压成 4 档语义色；标签文案仍逐值区分（列声明里的 options） */
const STATUS_TONE: Record<AutomationRunStatus, StatusTone> = {
  SUCCEEDED: { type: 'success', effect: 'light' },
  RUNNING: { type: 'info', effect: 'light' },
  QUEUED: { type: 'info', effect: 'plain' },
  PAUSED: { type: 'warning', effect: 'plain' },
  CANCELED: { type: 'warning', effect: 'plain' },
  FAILED: { type: 'danger', effect: 'light' },
  TIMEOUT: { type: 'danger', effect: 'plain' },
  INTERNAL_ERROR: { type: 'danger', effect: 'light' },
  QUOTA_EXCEEDED: { type: 'danger', effect: 'plain' },
  MEMORY_LIMIT_EXCEEDED: { type: 'danger', effect: 'plain' },
  LOG_SIZE_EXCEEDED: { type: 'danger', effect: 'plain' },
}

export function useAutomationFlowRun() {
  const { t } = useI18n()

  const loading = ref(false)
  const keyword = ref('')

  const detailVisible = ref(false)
  const loadingDetail = ref(false)
  const detailRun = ref<AutomationFlowRunSummary | null>(null)
  const detailJson = ref('')

  const viewportWidth = ref(typeof window === 'undefined' ? 1440 : window.innerWidth)
  const syncViewportWidth = () => {
    viewportWidth.value = window.innerWidth
  }
  const isCompact = computed(() => viewportWidth.value < 1180)

  const grid = useAdminListGrid<AutomationFlowRunSummary>({
    storageKey: 'admin-list-layout:automation-runs',
  })

  const tableColumns = computed(() =>
    isCompact.value
      ? grid.displayColumns.value.filter((col) => COMPACT_FIELDS.has(col.field))
      : grid.displayColumns.value,
  )

  const statusTone = (status: AutomationRunStatus): StatusTone =>
    STATUS_TONE[status] ?? { type: 'info', effect: 'plain' }

  /** 状态标签文案走列声明的 options（后端唯一真源），未声明的值原样显示 */
  const statusLabel = (status: AutomationRunStatus): string => {
    const column = grid.displayColumns.value.find((col) => col.field === 'status')
    return column?.options?.find((option) => option.value === status)?.label ?? status
  }

  /** 毫秒 → "3.2s" / "1m 04s"；未结束的运行没有耗时 */
  const formatDuration = (durationMs: number | null): string => {
    if (durationMs === null || durationMs === undefined || durationMs < 0) return '—'
    if (durationMs < 60_000) return `${(durationMs / 1000).toFixed(1)}s`
    const minutes = Math.floor(durationMs / 60_000)
    const seconds = Math.round((durationMs % 60_000) / 1000)
    return `${minutes}m ${String(seconds).padStart(2, '0')}s`
  }

  const compactMeta = (row: AutomationFlowRunSummary) =>
    [row.startTime ? formatDate(row.startTime) : null, formatDuration(row.durationMs), row.triggeredByName]
      .filter(Boolean)
      .join(' · ')

  const loadRuns = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const envelope = await automationFlowRunApi.query({
        ...grid.buildQuery(),
        keyword: keyword.value || undefined,
      })
      if (!grid.isCurrentQuery(seq)) return
      const page = envelope.data
      if (!page) {
        throw new Error('automation/flow-runs/query response is missing data')
      }
      grid.applyPage(page, 'automation/flow-runs/query response is missing its column declaration')
    } catch {
      if (!grid.isCurrentQuery(seq)) return
      ElMessage.error(t('automationRun.loadFailed'))
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  /** 行点击 → 抽屉里展示这次运行的完整 JSON（逐步骤输出存在 AP 侧，按需取） */
  const openRunDetail = async (row: AutomationFlowRunSummary) => {
    detailRun.value = row
    detailJson.value = ''
    detailVisible.value = true
    loadingDetail.value = true
    try {
      const res = await automationFlowRunApi.getRun(row.id)
      detailJson.value = JSON.stringify(res.data ?? {}, null, 2)
    } catch {
      // interceptor already notified (404 = 该运行不在当前会话可见范围/已过保留期)
      detailJson.value = ''
    } finally {
      loadingDetail.value = false
    }
  }

  onMounted(() => {
    window.addEventListener('resize', syncViewportWidth)
  })
  onBeforeUnmount(() => window.removeEventListener('resize', syncViewportWidth))

  return {
    loading,
    keyword,
    isCompact,
    tableColumns,
    statusTone,
    statusLabel,
    formatDuration,
    compactMeta,
    loadRuns,
    detailVisible,
    loadingDetail,
    detailRun,
    detailJson,
    openRunDetail,
    ...grid,
  }
}
