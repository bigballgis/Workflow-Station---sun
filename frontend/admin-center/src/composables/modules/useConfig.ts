/**
 * 系统配置业务逻辑 composable
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { logger } from '@/utils/logger'
import { notifyError, notifySuccess } from '@/utils/notify'
import { configApi } from '@/api/config'

export function useConfig() {
  const { t } = useI18n()

  const activeTab = ref('system')
  const loading = ref(false)

  const systemConfig = reactive({ sessionTimeout: 30, maxFileSize: 10, smtpServer: '' })
  const businessConfig = reactive({ processTimeout: 7, taskAssignRule: 'ROUND_ROBIN' })

  const loadConfigs = async () => {
    loading.value = true
    try {
      const configs = await configApi.getAll()
      configs.forEach((config: { configKey: string; configValue: string }) => {
        if (config.configKey === 'session.timeout') systemConfig.sessionTimeout = parseInt(config.configValue) || 30
        if (config.configKey === 'file.maxSize') systemConfig.maxFileSize = parseInt(config.configValue) || 10
        if (config.configKey === 'smtp.server') systemConfig.smtpServer = config.configValue || ''
        if (config.configKey === 'process.timeout') businessConfig.processTimeout = parseInt(config.configValue) || 7
        if (config.configKey === 'task.assignRule') businessConfig.taskAssignRule = config.configValue || 'ROUND_ROBIN'
      })
    } catch (e) {
      logger.error('config', 'Failed to load configs:', e)
    } finally {
      loading.value = false
    }
  }

  const saveConfig = async (type: string) => {
    try {
      if (type === 'system') {
        await Promise.all([
          configApi.update('session.timeout', { configValue: String(systemConfig.sessionTimeout) }),
          configApi.update('file.maxSize', { configValue: String(systemConfig.maxFileSize) }),
          configApi.update('smtp.server', { configValue: systemConfig.smtpServer })
        ])
      } else {
        await Promise.all([
          configApi.update('process.timeout', { configValue: String(businessConfig.processTimeout) }),
          configApi.update('task.assignRule', { configValue: businessConfig.taskAssignRule })
        ])
      }
      notifySuccess(type === 'system' ? t('config.systemSaveSuccess') : t('config.businessSaveSuccess'))
    } catch (e) {
      logger.error('config', 'Failed to save config:', e)
      notifyError(t(errorTranslator(AppErrorCode.CONFIG_SAVE_FAILED)))
    }
  }

  return { activeTab, loading, systemConfig, businessConfig, loadConfigs, saveConfig }
}
