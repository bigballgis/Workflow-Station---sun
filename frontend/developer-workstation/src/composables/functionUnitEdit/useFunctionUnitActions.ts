import { ref } from 'vue'
import type { ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ValidationResult } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import type { useFunctionUnitStore } from '@/stores/functionUnit'

type FunctionUnitStore = ReturnType<typeof useFunctionUnitStore>

interface UseFunctionUnitActionsOptions {
  functionUnitId: ComputedRef<number>
  store: FunctionUnitStore
}

/** Validate / publish / export operations for the function unit header. */
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

  async function handlePublish() {
    try {
      const { value } = await ElMessageBox.prompt(t('functionUnit.enterChangeLogPrompt'), t('functionUnit.publishFunctionUnit'), {
        inputType: 'textarea',
        inputPlaceholder: t('functionUnit.publishChangeLogPlaceholder')
      })
      await store.publish(functionUnitId.value, value)
      ElMessage.success(t('functionUnit.publishSuccess'))
      store.fetchById(functionUnitId.value)
    } catch (e: any) {
      if (e !== 'cancel') {
        ElMessage.error(e.response?.data?.message || t('functionUnit.publishFailed'))
      }
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
    handlePublish,
    handleExport
  }
}
