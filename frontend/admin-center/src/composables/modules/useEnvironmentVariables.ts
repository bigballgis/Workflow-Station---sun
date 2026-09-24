import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { notifyError, notifySuccess } from '@/utils/notify'
import { logger } from '@/utils/logger'
import {
  environmentVariableApi,
  type EnvironmentValueKind,
  type EnvironmentVariable,
  type EnvironmentVariableRequest,
} from '@/api/environment'

export type EnvironmentFormState = {
  varKey: string
  displayName: string
  description: string
  valueKind: EnvironmentValueKind
  defaultValue: string
  currentValue: string
  vaultSecretPath: string
  vaultPassword: string
}

function emptyForm(): EnvironmentFormState {
  return {
    varKey: '',
    displayName: '',
    description: '',
    valueKind: 'TEXT',
    defaultValue: '',
    currentValue: '',
    vaultSecretPath: '',
    vaultPassword: '',
  }
}

export type EnvironmentPayloadResult =
  | { errorKey: string }
  | { payload: EnvironmentVariableRequest }

export function buildEnvironmentPayload(
  form: EnvironmentFormState,
  editing: boolean,
): EnvironmentPayloadResult {
  const varKey = form.varKey.trim()
  const displayName = form.displayName.trim()
  if (!varKey || !displayName) {
    return { errorKey: 'config.envKeyRequired' }
  }
  if (form.valueKind === 'TEXT') {
    if (!form.defaultValue.trim()) {
      return { errorKey: 'config.envDefaultRequired' }
    }
    return {
      payload: {
        varKey,
        displayName,
        description: form.description.trim() || undefined,
        valueKind: 'TEXT',
        defaultValue: form.defaultValue,
        currentValue: form.currentValue,
      },
    }
  }
  const payload: EnvironmentVariableRequest = {
    varKey,
    displayName,
    description: form.description.trim() || undefined,
    valueKind: 'VAULT',
  }
  if (!editing && form.vaultPassword) {
    payload.vaultPassword = form.vaultPassword
  }
  return { payload }
}

export function useEnvironmentVariables() {
  const { t } = useI18n()
  const loading = ref(false)
  const saving = ref(false)
  const rows = ref<EnvironmentVariable[]>([])
  const dialogVisible = ref(false)
  const editingId = ref<string | null>(null)
  const form = reactive<EnvironmentFormState>(emptyForm())

  const load = async (): Promise<void> => {
    loading.value = true
    try {
      rows.value = await environmentVariableApi.list()
    } catch (e) {
      logger.error('config', 'Failed to load environment variables:', e)
    } finally {
      loading.value = false
    }
  }

  const openCreate = (): void => {
    editingId.value = null
    Object.assign(form, emptyForm())
    dialogVisible.value = true
  }

  const openEdit = (row: EnvironmentVariable): void => {
    editingId.value = row.id
    Object.assign(form, {
      varKey: row.varKey,
      displayName: row.displayName,
      description: row.description || '',
      valueKind: row.valueKind,
      defaultValue: row.defaultValue || '',
      currentValue: row.currentValue || '',
      vaultSecretPath: row.vaultSecretPath || '',
      vaultPassword: '',
    })
    dialogVisible.value = true
  }

  const buildPayload = (): EnvironmentVariableRequest | null => {
    const result = buildEnvironmentPayload(form, editingId.value != null)
    if ('errorKey' in result) {
      notifyError(t(result.errorKey))
      return null
    }
    return result.payload
  }

  const save = async (): Promise<void> => {
    const payload = buildPayload()
    if (!payload) return
    saving.value = true
    try {
      if (editingId.value) {
        await environmentVariableApi.update(editingId.value, payload)
      } else {
        await environmentVariableApi.create(payload)
      }
      notifySuccess(t('config.envSaveSuccess'))
      dialogVisible.value = false
      await load()
    } catch (e) {
      logger.error('config', 'Failed to save environment variable:', e)
    } finally {
      saving.value = false
    }
  }

  const remove = async (row: EnvironmentVariable): Promise<void> => {
    try {
      await ElMessageBox.confirm(
        t('config.envDeleteConfirm', { key: row.varKey }),
        t('common.confirm'),
        { type: 'warning' },
      )
      await environmentVariableApi.remove(row.id)
      notifySuccess(t('common.success'))
      await load()
    } catch (e) {
      if (e === 'cancel' || (typeof e === 'object' && e != null && (e as { action?: string }).action === 'cancel')) {
        return
      }
      logger.error('config', 'Failed to delete environment variable:', e)
    }
  }

  return {
    loading,
    saving,
    rows,
    dialogVisible,
    editingId,
    form,
    load,
    openCreate,
    openEdit,
    save,
    remove,
  }
}
