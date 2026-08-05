/**
 * 系统配置业务逻辑 composable
 */
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import axios from 'axios'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { logger } from '@/utils/logger'
import { notifyError, notifySuccess } from '@/utils/notify'
import { configApi, type ConfigCreateRequest } from '@/api/config'

type ConfigRow = { configKey: string; configValue: string }

const SMTP_DEFAULT_PORT = 25
const SMTP_DEFAULT_USE_TLS = true
const IMAP_DEFAULT_PORT = 993
const IMAP_DEFAULT_USE_SSL = true

async function upsertConfig(
  key: string,
  value: string,
  meta: Omit<ConfigCreateRequest, 'configKey' | 'configValue'>,
): Promise<void> {
  try {
    await configApi.update(key, { configValue: value })
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      await configApi.create({
        ...meta,
        configKey: key,
        configValue: value,
      })
      return
    }
    throw e
  }
}

export function useConfig() {
  const { t } = useI18n()

  const activeTab = ref('system')
  const loading = ref(false)

  const systemConfig = reactive({
    sessionTimeout: 30,
    maxFileSize: 10,
    smtpHost: '',
    smtpPort: SMTP_DEFAULT_PORT,
    smtpUseTls: SMTP_DEFAULT_USE_TLS,
    imapHost: '',
    imapPort: IMAP_DEFAULT_PORT,
    imapUseSsl: IMAP_DEFAULT_USE_SSL,
  })
  const businessConfig = reactive({ processTimeout: 7, taskAssignRule: 'ROUND_ROBIN' })

  const loadConfigs = async () => {
    loading.value = true
    try {
      const configs = await configApi.getAll()
      let legacySmtpServer = ''
      configs.forEach((config: ConfigRow) => {
        if (config.configKey === 'session.timeout') {
          systemConfig.sessionTimeout = parseInt(config.configValue, 10) || 30
        }
        if (config.configKey === 'file.maxSize') {
          systemConfig.maxFileSize = parseInt(config.configValue, 10) || 10
        }
        if (config.configKey === 'smtp.host') {
          systemConfig.smtpHost = config.configValue || ''
        }
        if (config.configKey === 'smtp.port') {
          const port = parseInt(config.configValue, 10)
          systemConfig.smtpPort = Number.isFinite(port) ? port : SMTP_DEFAULT_PORT
        }
        if (config.configKey === 'smtp.useTls') {
          systemConfig.smtpUseTls = config.configValue !== 'false'
        }
        if (config.configKey === 'imap.host') {
          systemConfig.imapHost = config.configValue || ''
        }
        if (config.configKey === 'imap.port') {
          const port = parseInt(config.configValue, 10)
          systemConfig.imapPort = Number.isFinite(port) ? port : IMAP_DEFAULT_PORT
        }
        if (config.configKey === 'imap.useSsl') {
          systemConfig.imapUseSsl = config.configValue !== 'false'
        }
        if (config.configKey === 'smtp.server') {
          legacySmtpServer = config.configValue || ''
        }
        if (config.configKey === 'process.timeout') {
          businessConfig.processTimeout = parseInt(config.configValue, 10) || 7
        }
        if (config.configKey === 'task.assignRule') {
          businessConfig.taskAssignRule = config.configValue || 'ROUND_ROBIN'
        }
      })
      if (!systemConfig.smtpHost && legacySmtpServer) {
        systemConfig.smtpHost = legacySmtpServer
      }
    } catch (e) {
      logger.error('config', 'Failed to load configs:', e)
    } finally {
      loading.value = false
    }
  }

  const saveConfig = async (type: string) => {
    try {
      if (type === 'system') {
        if (!systemConfig.smtpHost.trim()) {
          notifyError(t('config.smtpHostRequired'))
          return
        }
        if (systemConfig.smtpPort == null || systemConfig.smtpPort < 1 || systemConfig.smtpPort > 65535) {
          notifyError(t('config.smtpPortRequired'))
          return
        }
        if (!systemConfig.imapHost.trim()) {
          notifyError(t('config.imapHostRequired'))
          return
        }
        if (systemConfig.imapPort == null || systemConfig.imapPort < 1 || systemConfig.imapPort > 65535) {
          notifyError(t('config.imapPortRequired'))
          return
        }
        await Promise.all([
          upsertConfig('session.timeout', String(systemConfig.sessionTimeout), {
            category: 'SYSTEM',
            configType: 'NUMBER',
            description: 'Session timeout (minutes)',
          }),
          upsertConfig('file.maxSize', String(systemConfig.maxFileSize), {
            category: 'SYSTEM',
            configType: 'NUMBER',
            description: 'File upload limit (MB)',
          }),
          upsertConfig('smtp.host', systemConfig.smtpHost.trim(), {
            category: 'SYSTEM',
            configType: 'STRING',
            description: 'Global SMTP host for outbound email',
          }),
          upsertConfig('smtp.port', String(systemConfig.smtpPort), {
            category: 'SYSTEM',
            configType: 'NUMBER',
            description: 'Global SMTP port for outbound email',
          }),
          upsertConfig('smtp.useTls', String(systemConfig.smtpUseTls), {
            category: 'SYSTEM',
            configType: 'BOOLEAN',
            description: 'Global SMTP TLS for outbound email',
          }),
          upsertConfig('imap.host', systemConfig.imapHost.trim(), {
            category: 'SYSTEM',
            configType: 'STRING',
            description: 'Global IMAP host for inbound email monitor',
          }),
          upsertConfig('imap.port', String(systemConfig.imapPort), {
            category: 'SYSTEM',
            configType: 'NUMBER',
            description: 'Global IMAP port for inbound email monitor',
          }),
          upsertConfig('imap.useSsl', String(systemConfig.imapUseSsl), {
            category: 'SYSTEM',
            configType: 'BOOLEAN',
            description: 'Global IMAP SSL for inbound email monitor',
          }),
        ])
      } else {
        await Promise.all([
          upsertConfig('process.timeout', String(businessConfig.processTimeout), {
            category: 'BUSINESS',
            configType: 'NUMBER',
            description: 'Process timeout (days)',
          }),
          upsertConfig('task.assignRule', businessConfig.taskAssignRule, {
            category: 'BUSINESS',
            configType: 'STRING',
            description: 'Default task assignment rule',
          }),
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
