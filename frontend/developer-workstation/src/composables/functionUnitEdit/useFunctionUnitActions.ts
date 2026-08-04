import { ref } from 'vue'
import type { ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { ValidationResult } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import type { useFunctionUnitStore } from '@/stores/functionUnit'

type FunctionUnitStore = ReturnType<typeof useFunctionUnitStore>

interface UseFunctionUnitActionsOptions {
  functionUnitId: ComputedRef<number>
  store: FunctionUnitStore
}

/**
 * Validate / export operations for the function unit header.
 *
 * Publish 入口已废弃：版本快照与状态推进由 Deploy 流程内部完成
 * （后端 DeploymentComponentImpl 第一步即调 publish），DW 不再单独暴露该按钮。
 */
export function useFunctionUnitActions(options: UseFunctionUnitActionsOptions) {
  const { functionUnitId, store } = options
  const { t } = useI18n()

  const validating = ref(false)
  const exporting = ref(false)
  const showValidationDialog = ref(false)
  const validationResult = ref<ValidationResult | null>(null)

  async function handleValidate() {
    validating.value = true
    try {
      validationResult.value = await store.validate(functionUnitId.value)
      showValidationDialog.value = true
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('functionUnit.validationFailed'))
    } finally {
      validating.value = false
    }
  }

  async function handleExport() {
    exporting.value = true
    try {
      const response = await functionUnitApi.exportFunctionUnit(functionUnitId.value)
      // Create download link
      const blob = new Blob([response as any], { type: 'application/zip' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `function-unit-${store.current?.name || functionUnitId.value}.zip`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      ElMessage.success(t('functionUnit.exportSuccess'))
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('functionUnit.exportFailed'))
    } finally {
      exporting.value = false
    }
  }

  return {
    validating,
    exporting,
    showValidationDialog,
    validationResult,
    handleValidate,
    handleExport
  }
}
