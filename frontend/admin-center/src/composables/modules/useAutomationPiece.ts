/**
 * Automation Pieces 目录页：共享列表 + 包级启停/导入导出。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import {
  automationPieceApi,
  exportFilename,
  type AutomationPieceSummary
} from '@/api/automationPiece'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'

const EXPAND_COL_WIDTH = 48
const ACTIONS_COL_WIDTH = 140
const PIECE_COL_WIDTHS: Record<string, number> = {
  displayName: 110,
  name: 140,
  version: 130,
  pieceType: 105,
  disabled: 85,
  actionCount: 90,
  triggerCount: 90,
  updated: 150,
}

export function useAutomationPiece() {
  const { t } = useI18n()

  const loading = ref(false)
  const keyword = ref('')
  const exportingKey = ref('')
  const togglingKey = ref('')
  const deletingKey = ref('')
  const importing = ref(false)

  const grid = useAdminListGrid<AutomationPieceSummary>({
    storageKey: 'admin-list-layout:automation-pieces',
    extraWidth: EXPAND_COL_WIDTH + ACTIONS_COL_WIDTH,
    defaultWidthOf: (field) => PIECE_COL_WIDTHS[field] ?? 120,
  })

  const rowKey = (row: AutomationPieceSummary) => `${row.name}@${row.version}`

  const versionOptions = (row: AutomationPieceSummary) => row.versions ?? [row]

  const pickVersion = (row: AutomationPieceSummary, version: string) => {
    const picked = versionOptions(row).find((item) => item.version === version)
    if (!picked) return
    const versions = row.versions
    Object.assign(row, picked, { versions })
  }

  const loadPieces = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const envelope = await automationPieceApi.query({
        ...grid.buildQuery(),
        keyword: keyword.value || undefined,
      })
      if (!grid.isCurrentQuery(seq)) return
      const page = envelope.data
      if (!page) {
        throw new Error('automation/pieces/query response is missing data')
      }
      grid.applyPage(page, 'automation/pieces/query response is missing its column declaration')
    } catch {
      if (!grid.isCurrentQuery(seq)) return
      ElMessage.error(t('automationPiece.loadFailed'))
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  const handleExport = async (row: AutomationPieceSummary) => {
    exportingKey.value = rowKey(row)
    try {
      const blob = await automationPieceApi.exportPiece(row.name, row.version)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = exportFilename(row)
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      ElMessage.error(t('automationPiece.exportFailed'))
    } finally {
      exportingKey.value = ''
    }
  }

  const handleImportFile = async (file: UploadFile) => {
    if (!file.raw) return
    importing.value = true
    try {
      const res = await automationPieceApi.importPiece(file.raw)
      const info = res.data
      ElMessage.success(t('automationPiece.importSuccess', {
        name: info?.displayName ?? '',
        version: info?.version ?? ''
      }))
      await loadPieces()
    } catch {
      ElMessage.error(t('automationPiece.importFailed'))
    } finally {
      importing.value = false
    }
  }

  const handleToggle = async (row: AutomationPieceSummary, enabled: boolean) => {
    togglingKey.value = row.name
    try {
      await automationPieceApi.togglePiece(row.name, !enabled)
      row.disabled = !enabled
      row.versions?.forEach((item) => { item.disabled = !enabled })
    } catch {
      ElMessage.error(t('automationPiece.toggleFailed'))
    } finally {
      togglingKey.value = ''
    }
  }

  const handleDelete = async (row: AutomationPieceSummary) => {
    try {
      const message = row.pieceType === 'OFFICIAL'
        ? t('automationPiece.deleteOfficialConfirm', { name: row.displayName, version: row.version })
        : t('automationPiece.deleteConfirm', { name: row.displayName, version: row.version })
      await ElMessageBox.confirm(message, t('common.delete'), {
        type: 'warning',
        confirmButtonText: t('common.delete')
      })
    } catch {
      return
    }
    deletingKey.value = rowKey(row)
    try {
      await automationPieceApi.deletePiece(row.name, row.version)
      ElMessage.success(t('automationPiece.deleted'))
      await loadPieces()
    } catch (e: unknown) {
      const status = (e as { status?: number })?.status
      const flows = (e as { message?: string })?.message ?? ''
      if (status === 409) {
        try {
          await ElMessageBox.confirm(
            t('automationPiece.deleteInUse', { flows }),
            t('common.delete'),
            { type: 'error', confirmButtonText: t('automationPiece.forceDelete') }
          )
          await automationPieceApi.deletePiece(row.name, row.version, true)
          ElMessage.success(t('automationPiece.deleted'))
          await loadPieces()
        } catch {
          // cancelled or force-delete failed (interceptor already notified)
        }
      }
    } finally {
      deletingKey.value = ''
    }
  }

  return {
    loading,
    keyword,
    exportingKey,
    togglingKey,
    deletingKey,
    importing,
    handleExport,
    handleImportFile,
    handleToggle,
    handleDelete,
    loadPieces,
    rowKey,
    versionOptions,
    pickVersion,
    EXPAND_COL_WIDTH,
    ACTIONS_COL_WIDTH,
    ...grid,
  }
}
