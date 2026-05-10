/**
 * User Import 业务逻辑 composable
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyWarning } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { userApi, type ImportResult } from '@/api/user'

export function useUserImport(onSuccess: () => void) {
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))

  const loading = ref(false)
  const selectedFile = ref<File | null>(null)
  const importResult = ref<ImportResult | null>(null)

  const validateFile = (file: File): boolean => {
    if (file.size > 5 * 1024 * 1024) { notifyError(t('user.fileSizeExceeded')); return false }
    selectedFile.value = file; importResult.value = null; return true
  }
  const onExceed = () => notifyWarning(t('user.onlyOneFile'))

  const downloadTemplate = async () => {
    try {
      const blob = await userApi.exportTemplate()
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a'); link.href = url; link.download = t('user.templateFileName')
      link.click(); window.URL.revokeObjectURL(url)
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.USER_ACTION_FAILED)) }
  }

  const doImport = async () => {
    if (!selectedFile.value) return
    loading.value = true
    try {
      importResult.value = await userApi.batchImport(selectedFile.value)
      if (importResult.value.success > 0) onSuccess()
      if (importResult.value.failed === 0) notifySuccess(t('user.importSuccessResult'))
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || t('user.importFailed')) }
    finally { loading.value = false }
  }

  return { loading, selectedFile, importResult, validateFile, onExceed, downloadTemplate, doImport }
}
