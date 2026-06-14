import { reactive, ref } from 'vue'
import type { ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { DeployRequest, DeployResponse } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import type { useFunctionUnitStore } from '@/stores/functionUnit'

type FunctionUnitStore = ReturnType<typeof useFunctionUnitStore>
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

interface UseFunctionUnitDeployOptions {
  functionUnitId: ComputedRef<number>
  store: FunctionUnitStore
}

/** Deploy dialog: form, deployment trigger, status polling, and display helpers. */
export function useFunctionUnitDeploy(options: UseFunctionUnitDeployOptions) {
  const { functionUnitId, store } = options
  const { t } = useI18n()

  const deploying = ref(false)
  const showDeployDialog = ref(false)
  const deployStatus = ref<DeployResponse | null>(null)
  const deployPollingTimer = ref<number | null>(null)

  const deployForm = reactive({
    autoEnable: true,
    changeLog: ''
  })

  async function handleDeploy() {
    deploying.value = true
    deployStatus.value = null
    try {
      const request: DeployRequest = {
        autoEnable: deployForm.autoEnable,
        changeLog: deployForm.changeLog || undefined
      }
      const response = await functionUnitApi.deploy(functionUnitId.value, request)
      deployStatus.value = response.data

      // Start polling for status
      if (response.data.status === 'DEPLOYING') {
        startDeployPolling(response.data.deploymentId)
      } else if (response.data.status === 'SUCCESS') {
        const versionInfo = response.data.versionNumber
          ? t('functionUnit.deploySuccessWithVersion', { version: response.data.versionNumber })
          : t('functionUnit.deploySuccess')
        ElMessage.success(versionInfo)
        if (response.data.versionNumber && store.current) store.current.currentVersion = response.data.versionNumber
        store.fetchVersions(functionUnitId.value)
      }
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('functionUnit.deployFailed'))
      deployStatus.value = {
        deploymentId: '',
        status: 'FAILED',
        message: e.response?.data?.message || t('functionUnit.deployFailed')
      }
    } finally {
      deploying.value = false
    }
  }

  function startDeployPolling(deploymentId: string) {
    if (deployPollingTimer.value) {
      clearInterval(deployPollingTimer.value)
    }

    deployPollingTimer.value = window.setInterval(async () => {
      try {
        const response = await functionUnitApi.getDeploymentStatus(deploymentId)
        deployStatus.value = response.data

        if (response.data.status === 'SUCCESS') {
          const versionInfo = response.data.versionNumber
            ? t('functionUnit.deploySuccessWithVersion', { version: response.data.versionNumber })
            : t('functionUnit.deploySuccess')
          ElMessage.success(versionInfo)
          if (response.data.versionNumber && store.current) store.current.currentVersion = response.data.versionNumber
        store.fetchVersions(functionUnitId.value)
          stopDeployPolling()
        } else if (response.data.status === 'FAILED') {
          ElMessage.error(t('functionUnit.deployFailedWithMessage', { message: response.data.message }))
          stopDeployPolling()
        }
      } catch (e) {
        stopDeployPolling()
      }
    }, 2000)
  }

  function stopDeployPolling() {
    if (deployPollingTimer.value) {
      clearInterval(deployPollingTimer.value)
      deployPollingTimer.value = null
    }
  }

  /** Clears deploy UI state whenever the dialog finishes closing (X, overlay, Esc, or footer Close). */
  function cleanupDeployDialogState() {
    deployStatus.value = null
    deployForm.changeLog = ''
    stopDeployPolling()
  }

  function closeDeployDialog() {
    showDeployDialog.value = false
  }

  function getDeployStatusType(status: string): TagType {
    const map: Record<string, TagType> = {
      PENDING: 'info',
      DEPLOYING: 'warning',
      SUCCESS: 'success',
      FAILED: 'danger',
      ROLLED_BACK: 'info'
    }
    return map[status] || 'info'
  }

  function getDeployStatusLabel(status: string) {
    const map: Record<string, string> = {
      PENDING: t('functionUnit.statusPending'),
      DEPLOYING: t('functionUnit.statusDeploying'),
      SUCCESS: t('functionUnit.statusSuccess'),
      FAILED: t('functionUnit.statusFailed'),
      ROLLED_BACK: t('functionUnit.statusRolledBack')
    }
    return map[status] || status
  }

  function translateStep(text: string) {
    if (!text) return text
    // 尝试用 te() 检查 key 是否存在，存在则翻译
    const translated = t(text)
    return translated !== text ? translated : text
  }

  return {
    deploying,
    showDeployDialog,
    deployStatus,
    deployForm,
    handleDeploy,
    stopDeployPolling,
    cleanupDeployDialogState,
    closeDeployDialog,
    getDeployStatusType,
    getDeployStatusLabel,
    translateStep
  }
}
