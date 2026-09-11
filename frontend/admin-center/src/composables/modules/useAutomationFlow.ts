/**
 * Automation Flows 目录页：共享列表 + 导入导出 / 启停 / 删除。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import {
  automationFlowApi,
  type AutomationFlowSummary,
  type AutomationWorkspaceOption,
  type ConnectionCheckItem,
  type FlowExportConnection,
} from '@/api/automationFlow'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'
import { formatDate } from '@/utils/format'

const ACTIONS_COL_WIDTH = 140

const COMPACT_FIELDS = new Set(['displayName', 'readiness'])

export function useAutomationFlow() {
  const { t } = useI18n()

  const loading = ref(false)
  const keyword = ref('')
  const exportingId = ref('')
  const actingId = ref('')

  const structureDialogVisible = ref(false)
  const structureFlow = ref<AutomationFlowSummary | null>(null)

  const importDialogVisible = ref(false)
  const importFile = ref<File | null>(null)
  const importPublish = ref(true)
  /** 导入目标 workspace（DW 开发组 id）。默认 Public，选项加载后填入其真实组 id
   *  ——空串会被 el-select 当成"未选择"而显示占位符。 */
  const importWorkspaceId = ref('')
  const workspaceOptions = ref<AutomationWorkspaceOption[]>([])
  const publicWorkspaceId = ref('')
  /** 转让对话框：目标 workspace 默认留空，必须显式选一个（转让是归属变更，不该有默认值） */
  const transferDialogVisible = ref(false)
  const transferFlowRow = ref<AutomationFlowSummary | null>(null)
  const transferTargetId = ref('')
  const transferring = ref(false)
  const importing = ref(false)
  const connectionChecks = ref<ConnectionCheckItem[]>([])

  const viewportWidth = ref(typeof window === 'undefined' ? 1440 : window.innerWidth)
  const syncViewportWidth = () => {
    viewportWidth.value = window.innerWidth
  }
  const isCompact = computed(() => viewportWidth.value < 1180)

  const grid = useAdminListGrid<AutomationFlowSummary>({
    storageKey: 'admin-list-layout:automation-flows',
    extraWidth: ACTIONS_COL_WIDTH,
  })

  const tableColumns = computed(() =>
    isCompact.value
      ? grid.displayColumns.value.filter((col) => COMPACT_FIELDS.has(col.field))
      : grid.displayColumns.value,
  )


  const hasMissingConnections = computed(() =>
    connectionChecks.value.some((item) => !item.exists))

  const shortPieceName = (name: string) =>
    name.includes('/') ? name.split('/')[1] : name

  const compactMeta = (row: AutomationFlowSummary) =>
    [row.id, row.ownerName, formatDate(row.updated)].filter(Boolean).join(' · ')

  const readiness = (row: AutomationFlowSummary) => {
    const key = row.readiness ?? (!row.published ? 'DRAFT' : row.status)
    if (key === 'DRAFT') {
      return { type: 'info' as const, effect: 'plain' as const, labelKey: 'automationFlow.stateDraft' }
    }
    return key === 'ENABLED'
      ? { type: 'success' as const, effect: 'light' as const, labelKey: 'automationFlow.stateLive' }
      : { type: 'warning' as const, effect: 'plain' as const, labelKey: 'automationFlow.stateStopped' }
  }

  const loadFlows = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const envelope = await automationFlowApi.query({
        ...grid.buildQuery(),
        keyword: keyword.value || undefined,
      })
      if (!grid.isCurrentQuery(seq)) return
      const page = envelope.data
      if (!page) {
        throw new Error('automation/flows/query response is missing data')
      }
      grid.applyPage(page, 'automation/flows/query response is missing its column declaration')
    } catch {
      if (!grid.isCurrentQuery(seq)) return
      ElMessage.error(t('automationFlow.loadFailed'))
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  const handleExport = async (row: AutomationFlowSummary) => {
    exportingId.value = row.id
    try {
      const blob = await automationFlowApi.exportFlow(row.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `flow-${row.displayName.replace(/[^\w-]+/g, '-')}-${row.flowKey ?? row.id}.json`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      ElMessage.error(t('automationFlow.exportFailed'))
    } finally {
      exportingId.value = ''
    }
  }

  const handleRowCommand = (command: string, row: AutomationFlowSummary) => {
    if (command === 'structure') {
      structureFlow.value = row
      structureDialogVisible.value = true
    } else if (command === 'transfer') {
      transferFlowRow.value = row
      transferTargetId.value = ''
      transferDialogVisible.value = true
    } else if (command === 'toggle') {
      void handleToggle(row)
    } else if (command === 'delete') {
      void handleDelete(row)
    }
  }

  const handleToggle = async (row: AutomationFlowSummary) => {
    const enable = row.status !== 'ENABLED'
    actingId.value = row.id
    try {
      await automationFlowApi.setEnabled(row.id, enable)
      row.status = enable ? 'ENABLED' : 'DISABLED'
      row.readiness = enable ? 'ENABLED' : 'DISABLED'
      ElMessage.success(t(enable ? 'automationFlow.enabled' : 'automationFlow.disabled',
        { name: row.displayName }))
    } catch {
      // interceptor already notified (e.g. enabling an unpublished flow)
    } finally {
      actingId.value = ''
    }
  }

  const handleDelete = async (row: AutomationFlowSummary) => {
    try {
      await ElMessageBox.confirm(
        t('automationFlow.deleteConfirm', { name: row.displayName }),
        t('common.delete'),
        { type: 'warning', confirmButtonText: t('common.delete') },
      )
    } catch {
      return
    }
    actingId.value = row.id
    try {
      await automationFlowApi.deleteFlow(row.id)
      ElMessage.success(t('automationFlow.deleted', { name: row.displayName }))
      await loadFlows()
    } catch (e: unknown) {
      const status = (e as { status?: number })?.status
      if (status !== 409) {
        return
      }
      const units = (e as { message?: string })?.message ?? ''
      try {
        await ElMessageBox.confirm(
          t('automationFlow.deleteInUse', { units }),
          t('common.delete'),
          { type: 'error', confirmButtonText: t('automationFlow.forceDelete') },
        )
        await automationFlowApi.deleteFlow(row.id, true)
        ElMessage.success(t('automationFlow.deleted', { name: row.displayName }))
        await loadFlows()
      } catch {
        // cancelled or force-delete failed (interceptor already notified)
      }
    } finally {
      actingId.value = ''
    }
  }

  const onImportFileChange = async (file: UploadFile) => {
    importFile.value = file.raw ?? null
    connectionChecks.value = []
    if (!file.raw) return
    try {
      const pkg = JSON.parse(await file.raw.text()) as { connections?: FlowExportConnection[] }
      const ids = (pkg.connections ?? []).map((c) => c.externalId).filter(Boolean)
      if (ids.length === 0) return
      const res = await automationFlowApi.connectionsCheck(ids, importWorkspaceId.value || null)
      connectionChecks.value = res.data ?? []
    } catch {
      connectionChecks.value = []
    }
  }

  const resetImportDialog = () => {
    importFile.value = null
    importPublish.value = true
    importWorkspaceId.value = publicWorkspaceId.value
    connectionChecks.value = []
  }

  /** 目标 workspace 选项；'' = Public（后端把空值解析成 Public）。 */
  const loadWorkspaces = async () => {
    try {
      const res = await automationFlowApi.listWorkspaces()
      workspaceOptions.value = res.data ?? []
      publicWorkspaceId.value = workspaceOptions.value.find((w) => w.publicWorkspace)?.id ?? ''
      if (!importWorkspaceId.value) {
        importWorkspaceId.value = publicWorkspaceId.value
      }
    } catch {
      workspaceOptions.value = []
    }
  }

  const handleTransfer = async () => {
    const row = transferFlowRow.value
    if (!row || !transferTargetId.value) return
    transferring.value = true
    try {
      const res = await automationFlowApi.transferFlow(row.id, transferTargetId.value)
      const info = res.data
      ElMessage.success(t('automationFlow.transferDone', {
        name: row.displayName,
        from: info?.fromWorkspaceName ?? '',
        to: info?.toWorkspaceName ?? '',
      }))
      // 归属改了但没能重新启用：说清楚要人工去目标 workspace 启用，不让它看起来一切正常
      if (info?.reEnableFailure) {
        ElMessage.warning(t('automationFlow.transferReEnableFailed', {
          to: info.toWorkspaceName,
          reason: info.reEnableFailure,
        }))
      }
      transferDialogVisible.value = false
      await loadFlows()
    } catch {
      // interceptor already notified
    } finally {
      transferring.value = false
    }
  }

  const handleImport = async () => {
    if (!importFile.value) return
    importing.value = true
    try {
      const res = await automationFlowApi.importFlow(
        importFile.value, importPublish.value, importWorkspaceId.value || null,
      )
      const info = res.data
      ElMessage.success(t(
        info?.created ? 'automationFlow.importCreated' : 'automationFlow.importUpdated',
        {
          name: info?.displayName ?? '',
          id: info?.flowId ?? '',
          workspace: info?.workspaceName ?? '',
        },
      ))
      importDialogVisible.value = false
      await loadFlows()
    } catch {
      // interceptor already notified
    } finally {
      importing.value = false
    }
  }

  /** 换目标 workspace 要重跑 connection 预检：连接是 per-project 的，答案随目标而变。 */
  const onImportWorkspaceChange = async () => {
    if (importFile.value) {
      await onImportFileChange({ raw: importFile.value } as UploadFile)
    }
  }

  onMounted(() => {
    window.addEventListener('resize', syncViewportWidth)
    void loadWorkspaces()
  })
  onBeforeUnmount(() => window.removeEventListener('resize', syncViewportWidth))

  return {
    loading,
    keyword,
    exportingId,
    actingId,
    structureDialogVisible,
    structureFlow,
    importDialogVisible,
    importFile,
    importPublish,
    importWorkspaceId,
    workspaceOptions,
    loadWorkspaces,
    onImportWorkspaceChange,
    transferDialogVisible,
    transferFlowRow,
    transferTargetId,
    transferring,
    handleTransfer,
    importing,
    connectionChecks,
    hasMissingConnections,
    isCompact,
    tableColumns,
    compactMeta,
    readiness,
    shortPieceName,
    loadFlows,
    handleExport,
    handleRowCommand,
    onImportFileChange,
    resetImportDialog,
    handleImport,
    ACTIONS_COL_WIDTH,
    ...grid,
  }
}
