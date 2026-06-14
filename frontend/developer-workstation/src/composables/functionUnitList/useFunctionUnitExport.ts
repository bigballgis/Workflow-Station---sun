import { ref } from 'vue'
import type { Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { functionUnitApi, type FunctionUnitResponse } from '@/api/functionUnit'

interface UseFunctionUnitExportOptions {
  list: Ref<FunctionUnitResponse[]>
  filteredList: Ref<FunctionUnitResponse[]>
}

/** Export dialog state and zip download for the function unit list. */
export function useFunctionUnitExport(options: UseFunctionUnitExportOptions) {
  const { list, filteredList } = options
  const { t } = useI18n()

  const showExportDialog = ref(false)
  const exporting = ref(false)
  const exportTargetId = ref<number | null>(null)

  function openExportDialog() {
    if (list.value.length === 0) return
    showExportDialog.value = true
  }

  function initExportSelection() {
    if (filteredList.value.length === 1) {
      exportTargetId.value = filteredList.value[0].id
    } else if (list.value.length === 1) {
      exportTargetId.value = list.value[0].id
    } else {
      exportTargetId.value = null
    }
  }

  async function handleExport() {
    if (exportTargetId.value == null) return
    const target = list.value.find(item => item.id === exportTargetId.value)
    exporting.value = true
    try {
      const response = await functionUnitApi.exportFunctionUnit(exportTargetId.value)
      const blob = new Blob([response as unknown as BlobPart], { type: 'application/zip' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `function-unit-${target?.name || exportTargetId.value}.zip`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      ElMessage.success(t('functionUnit.exportSuccess'))
      showExportDialog.value = false
    } catch (e: unknown) {
      const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(message || t('functionUnit.exportFailed'))
    } finally {
      exporting.value = false
    }
  }

  return {
    showExportDialog,
    exporting,
    exportTargetId,
    openExportDialog,
    initExportSelection,
    handleExport,
  }
}
