import { ref } from 'vue'
import type { Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { functionUnitApi } from '@/api/functionUnit'
import { findLastTaskAssigneeTopologyViolations } from '@/utils/bpmnAssigneeTopology'
import { isEmptyBpmnDiagram } from '@/utils/bpmnDiagramContent'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

/** 后端拒绝「空图覆盖非空流程」时的错误码（ProcessDesignComponentImpl#save）。 */
const EMPTY_PROCESS_OVERWRITE_BLOCKED = 'EMPTY_PROCESS_OVERWRITE_BLOCKED'

interface UseProcessActionsOptions {
  functionUnitId: number
  /** Accessor for the live bpmn-js modeler instance (avoids holding a stale reference). */
  getModeler: () => any
  store: {
    process: { bpmnXml?: string } | null
    saveProcess: (
      functionUnitId: number,
      payload: { bpmnXml: string },
      options?: { allowEmpty?: boolean }
    ) => Promise<unknown>
  }
  showImportDialog: Ref<boolean>
  importXml: Ref<string>
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * ProcessDesigner actions: topology validation, SVG/XML export, XML import,
 * manual save, debounced auto-save, and the live BPMN XML export used by the
 * debug panel. Owns the saving/auto-save UI state.
 */
export function useProcessActions(options: UseProcessActionsOptions) {
  const { functionUnitId, getModeler, store, showImportDialog, importXml, t } = options

  const saving = ref(false)
  const autoSaving = ref(false)
  const lastAutoSaveTime = ref<Date | null>(null)
  /** 画布已被清空、自动保存被空图护栏挡下（工具栏据此提示需手动保存确认）。 */
  const autoSaveBlocked = ref(false)

  let autoSaveTimer: ReturnType<typeof setTimeout> | null = null
  /** 每轮阻断只弹一次 toast：commandStack.changed 每 2s 就会再次触发保存。 */
  let emptyDiagramWarned = false

  function formatLastTaskTopologyViolations(): string {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return ''
    const violations = findLastTaskAssigneeTopologyViolations(bpmnModeler)
    return violations
      .map((v) => `${v.taskName || v.taskId} (${v.incomingCount})`)
      .join('; ')
  }

  async function exportCurrentBpmnXml(): Promise<string> {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return store.process?.bpmnXml || ''
    try {
      const { xml } = await bpmnModeler.saveXML({ format: true })
      return xml || store.process?.bpmnXml || ''
    } catch {
      return store.process?.bpmnXml || ''
    }
  }

  async function handleValidate() {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    const detail = formatLastTaskTopologyViolations()
    if (detail) {
      ElMessage.error(t('process.lastTaskAnchorBlocked', { detail }))
      return
    }
    try {
      const res = await functionUnitApi.validateProcess?.(functionUnitId)
      if (res?.data?.valid) {
        ElMessage.success(t('process.validationPassed'))
      } else {
        const errors = res?.data?.errors || []
        const warnings = res?.data?.warnings || []
        if (errors.length) {
          ElMessage.error(`${t('process.validationError')}: ${errors.join(', ')}`)
        } else if (warnings.length) {
          ElMessage.warning(`${t('process.validationWarning')}: ${warnings.join(', ')}`)
        }
      }
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('process.validationError'))
    }
  }

  async function handleExportSVG() {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    try {
      const { svg } = await bpmnModeler.saveSVG()
      downloadFile(svg, 'process.svg', 'image/svg+xml')
      ElMessage.success(t('process.svgExportSuccess'))
    } catch (err) {
      ElMessage.error(t('process.svgExportFailed'))
    }
  }

  async function handleExportXML() {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    try {
      const { xml } = await bpmnModeler.saveXML({ format: true })
      downloadFile(xml, 'process.bpmn', 'application/xml')
      ElMessage.success(t('process.xmlExportSuccess'))
    } catch (err) {
      ElMessage.error(t('process.xmlExportFailed'))
    }
  }

  function downloadFile(content: string, filename: string, type: string) {
    const blob = new Blob([content], { type })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  }

  async function handleImportXML() {
    const bpmnModeler = getModeler()
    if (!bpmnModeler || !importXml.value.trim()) return
    try {
      await bpmnModeler.importXML(importXml.value)
      showImportDialog.value = false
      importXml.value = ''
      ElMessage.success(t('process.importSuccess'))
    } catch (err: any) {
      ElMessage.error(t('process.importFailed') + ': ' + (err.message || t('process.importFailed')))
    }
  }

  /**
   * 当前导出的图是空的，而已保存的版本非空 —— 即「这次保存会把流程整体抹掉」。
   * 已存版本本身就是空的时不算（用户在空图上继续画的正常场景）。
   */
  function clearsExistingDiagram(xml: string): boolean {
    return isEmptyBpmnDiagram(xml) && !isEmptyBpmnDiagram(store.process?.bpmnXml)
  }

  async function handleSave(isAutoSave = false) {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    const detail = formatLastTaskTopologyViolations()
    if (detail) {
      if (!isAutoSave) {
        ElMessage.error(t('process.lastTaskAnchorBlocked', { detail }))
      }
      return
    }

    let xml: string
    try {
      xml = (await bpmnModeler.saveXML({ format: true })).xml
    } catch (e) {
      // bpmn-js 导出失败：没有 XML 就无从判断空图，直接放弃本次保存。
      if (!isAutoSave) {
        ElMessage.error((e as Error)?.message || t('process.saveFailed'))
      }
      return
    }

    // 空图护栏：清空画布只能由用户显式确认后落库，自动保存一律拒绝。
    // 2026-07-31 FU 50030 即由误触快捷键触发的自动保存把整条流程覆盖成空 process。
    const wipesDiagram = clearsExistingDiagram(xml)
    if (wipesDiagram) {
      if (isAutoSave) {
        autoSaveBlocked.value = true
        if (!emptyDiagramWarned) {
          emptyDiagramWarned = true
          ElMessage.warning(t('process.emptyDiagramAutoSaveBlocked'))
        }
        return
      }
      try {
        await ElMessageBox.confirm(
          t('process.emptyDiagramSaveConfirm'),
          t('process.emptyDiagramSaveConfirmTitle'),
          {
            type: 'warning',
            confirmButtonText: t('common.confirm'),
            cancelButtonText: t('common.cancel')
          }
        )
      } catch {
        return // 用户取消（含关闭弹窗）：保持已存版本
      }
    }

    if (isAutoSave) {
      autoSaving.value = true
    } else {
      saving.value = true
    }

    try {
      // allowEmpty 只在用户确认后传，后端据此放行同一条护栏。
      await store.saveProcess(functionUnitId, { bpmnXml: xml }, { allowEmpty: wipesDiagram })

      autoSaveBlocked.value = false
      emptyDiagramWarned = false
      if (isAutoSave) {
        lastAutoSaveTime.value = new Date()
      } else {
        ElMessage.success(t('process.saveSuccess'))
      }
    } catch (e) {
      const code = (e as { response?: { data?: { error?: { code?: string } } } })?.response?.data
        ?.error?.code
      const msg =
        code === EMPTY_PROCESS_OVERWRITE_BLOCKED
          ? t('process.emptyDiagramSaveRejected')
          : resolveUserFacingHttpMessage(e, t)
      if (!isAutoSave) {
        ElMessage.error(msg)
      }
    } finally {
      if (isAutoSave) {
        autoSaving.value = false
      } else {
        saving.value = false
      }
    }
  }

  function scheduleAutoSave() {
    // Clear existing timer
    if (autoSaveTimer) {
      clearTimeout(autoSaveTimer)
    }

    // Schedule auto-save after 2 seconds of inactivity
    autoSaveTimer = setTimeout(() => {
      handleSave(true)
    }, 2000)
  }

  function clearAutoSaveTimer() {
    if (autoSaveTimer) {
      clearTimeout(autoSaveTimer)
      autoSaveTimer = null
    }
  }

  function formatAutoSaveTime(time: Date): string {
    const now = new Date()
    const diff = Math.floor((now.getTime() - time.getTime()) / 1000)

    if (diff < 60) {
      return t('process.justNow')
    } else if (diff < 3600) {
      const minutes = Math.floor(diff / 60)
      return t('process.minutesAgo', { count: minutes })
    } else {
      return time.toLocaleTimeString()
    }
  }

  return {
    saving,
    autoSaving,
    lastAutoSaveTime,
    autoSaveBlocked,
    formatLastTaskTopologyViolations,
    exportCurrentBpmnXml,
    handleValidate,
    handleExportSVG,
    handleExportXML,
    handleImportXML,
    handleSave,
    scheduleAutoSave,
    clearAutoSaveTimer,
    formatAutoSaveTime,
  }
}
