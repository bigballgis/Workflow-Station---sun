/**
 * Automation Flows 目录页：共享列表 + 导入导出 / 启停 / 删除。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import {
  automationFlowApi,
  type AutomationFlowSummary,
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
      const res = await automationFlowApi.connectionsCheck(ids)
      connectionChecks.value = res.data ?? []
    } catch {
      connectionChecks.value = []
    }
  }

  const resetImportDialog = () => {
    importFile.value = null
    importPublish.value = true
    connectionChecks.value = []
  }

  const handleImport = async () => {
    if (!importFile.value) return
    importing.value = true
    try {
      const res = await automationFlowApi.importFlow(importFile.value, importPublish.value)
      const info = res.data
      ElMessage.success(t(
        info?.created ? 'automationFlow.importCreated' : 'automationFlow.importUpdated',
        { name: info?.displayName ?? '', id: info?.flowId ?? '' },
      ))
      importDialogVisible.value = false
      await loadFlows()
    } catch {
      // interceptor already notified
    } finally {
      importing.value = false
    }
  }

  onMounted(() => {
    window.addEventListener('resize', syncViewportWidth)
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
