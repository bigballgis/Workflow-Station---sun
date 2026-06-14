import { ref } from 'vue'
import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { functionUnitApi } from '@/api/functionUnit'
import { findLastTaskAssigneeTopologyViolations } from '@/utils/bpmnAssigneeTopology'

interface UseProcessActionsOptions {
  functionUnitId: number
  /** Accessor for the live bpmn-js modeler instance (avoids holding a stale reference). */
  getModeler: () => any
  store: {
    process: { bpmnXml?: string } | null
    saveProcess: (functionUnitId: number, payload: { bpmnXml: string }) => Promise<unknown>
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

  let autoSaveTimer: ReturnType<typeof setTimeout> | null = null

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

    if (isAutoSave) {
      autoSaving.value = true
    } else {
      saving.value = true
    }

    try {
      const { xml } = await bpmnModeler.saveXML({ format: true })
      await store.saveProcess(functionUnitId, { bpmnXml: xml })

      if (isAutoSave) {
        lastAutoSaveTime.value = new Date()
      } else {
        ElMessage.success(t('process.saveSuccess'))
      }
    } catch (e: any) {
      const msg =
        e?.message ||
        e?.response?.data?.error?.message ||
        e?.response?.data?.message ||
        t('process.saveFailed')
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
