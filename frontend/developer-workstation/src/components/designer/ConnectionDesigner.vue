<template>
  <div class="connection-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="openCreateDialog">{{ t('connection.create') }}</el-button>
      <el-button @click="loadConnections" :loading="loading">
        <el-icon><Refresh /></el-icon> {{ t('common.refresh') }}
      </el-button>
    </div>

    <DesignerListTable
      :loading="loading"
      :storage-key="`${functionUnitId}:connections`"
      :columns="listColumns"
      :rows="connections"
      :actions-width="240"
      table-class="connection-list-table"
    >
      <template #cell-connectionType="{ row }">
        {{ t(`connection.provider.${normalizeEmailProviderType(row.connectionType)}`) }}
      </template>
      <template #cell-enabled="{ row }">
        <el-tag
          :type="row.enabled ? 'success' : 'info'"
          size="small"
        >
          {{ row.enabled ? t('common.yes') : t('common.no') }}
        </el-tag>
      </template>
      <template #actions="{ row }">
        <div
          class="row-actions"
          @click.stop
        >
          <el-button
            link
            type="primary"
            @click="openEditDialog(row as EmailConnection)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            link
            type="success"
            @click="openTestDialog(row as EmailConnection)"
          >
            {{ t('connection.test') }}
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(row as EmailConnection)"
          >
            {{ t('common.delete') }}
          </el-button>
        </div>
      </template>
    </DesignerListTable>

    <el-dialog
      v-model="showFormDialog"
      :title="editingId ? t('connection.edit') : t('connection.create')"
      width="520px"
      top="8vh"
      :align-center="false"
      destroy-on-close
      class="connection-form-dialog"
      @opened="scrollConnectionDialogToTop"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="connectionFormRules"
        label-position="top"
        class="connection-form"
      >
        <el-form-item
          prop="senderEmail"
          class="connection-sender-email-item"
          :label="t('connection.fromEmail')"
          required
        >
          <el-input
            v-model="form.senderEmail"
            autocomplete="off"
            :placeholder="t('connection.emailAddressPlaceholder')"
          />
          <div class="form-tip">{{ t('connection.fromEmailHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('connection.fromName')">
          <el-input v-model="form.fromName" :placeholder="t('connection.fromNamePlaceholder')" />
        </el-form-item>
        <template v-if="isOutboundCapable">
          <p class="connection-section-title">{{ t('connection.smtpSection') }}</p>
          <p class="form-tip connection-system-smtp-hint">{{ t('connection.systemSmtpFromAdminHint') }}</p>
        </template>
        <template v-if="!isOutboundCapable">
          <p class="connection-section-title">{{ t('connection.smtpSection') }}</p>
          <el-form-item prop="host" :label="t('connection.host')" required>
            <el-input
              v-model="form.host"
              placeholder="smtp.example.com"
              autocomplete="off"
            />
            <div class="form-tip">{{ t('connection.smtpHostHint') }}</div>
          </el-form-item>
          <el-form-item prop="port" :label="t('connection.port')" required>
            <el-input-number
              v-model="form.port"
              :min="1"
              :max="65535"
              controls-position="right"
              style="width: 100%"
            />
            <div class="form-tip">{{ t('connection.smtpPortHint') }}</div>
          </el-form-item>
          <el-form-item prop="useTls" :label="t('connection.useTls')" required>
            <el-radio-group v-model="form.useTls">
              <el-radio :value="true">{{ t('common.yes') }}</el-radio>
              <el-radio :value="false">{{ t('common.no') }}</el-radio>
            </el-radio-group>
            <div class="form-tip">{{ t('connection.smtpTlsHint') }}</div>
          </el-form-item>
        </template>
        <el-form-item :label="t('connection.username')">
          <el-input v-model="form.username" autocomplete="off" :placeholder="t('connection.usernamePlaceholder')" />
          <div class="form-tip">{{ t('connection.usernameHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('connection.password')" :required="!editingId">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="editingId ? t('connection.passwordKeep') : ''"
          />
          <div class="form-tip">{{ t('connection.passwordHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('connection.direction')">
          <el-select v-model="form.direction" style="width: 100%">
            <el-option :label="t('connection.directionOutbound')" value="OUTBOUND" />
            <el-option :label="t('connection.directionInbound')" value="INBOUND" />
            <el-option :label="t('connection.directionBoth')" value="BOTH" />
          </el-select>
          <div class="form-tip">{{ t('connection.directionHint') }}</div>
        </el-form-item>

        <template v-if="isInbound">
          <el-divider content-position="left">{{ t('connection.inboundSection') }}</el-divider>
          <el-form-item :label="t('connection.mailboxAddress')">
            <el-input v-model="form.mailboxAddress" autocomplete="off" :placeholder="t('connection.mailboxAddressPlaceholder')" />
            <div class="form-tip">{{ t('connection.mailboxAddressHint') }}</div>
          </el-form-item>
          <el-form-item :label="t('connection.imapHost')" :required="imapRequired">
            <el-input v-model="form.imapHost" placeholder="imap.example.com" autocomplete="off" />
            <div class="form-tip">{{ t('connection.imapHostHint') }}</div>
          </el-form-item>
          <el-form-item :label="t('connection.imapPort')" :required="imapRequired">
            <el-input-number
              v-model="form.imapPort"
              :min="1"
              :max="65535"
              controls-position="right"
              style="width: 100%"
            />
            <div class="form-tip">{{ t('connection.imapPortHint') }}</div>
          </el-form-item>
          <el-form-item :label="t('connection.imapUseSsl')">
            <el-radio-group v-model="form.imapUseSsl">
              <el-radio :value="true">{{ t('common.yes') }}</el-radio>
              <el-radio :value="false">{{ t('common.no') }}</el-radio>
            </el-radio-group>
            <div class="form-tip">{{ t('connection.imapSslHint') }}</div>
          </el-form-item>
        </template>

        <el-form-item :label="t('connection.enabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTestDialog" :title="t('connection.testDialog')" width="420px">
      <el-form label-width="auto" label-position="left">
        <el-form-item :label="t('connection.testRecipient')" required>
          <el-input v-model="testRecipient" placeholder="test@example.com" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTestDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleTest" :loading="testing">{{ t('connection.sendTest') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { connectionApi, type EmailConnection, type EmailConnectionRequest } from '@/api/connection'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import {
  formatConnectionTestFailureMessage,
  isMessageBoxCancel,
  isValidSenderEmail,
  resolveConnectionSaveErrorMessage,
} from '@/utils/connectionValidation'
import {
  normalizeEmailProviderType,
  type EmailProviderType
} from '@/utils/emailProviderPresets'
import DesignerListTable from '@/components/designer-list/DesignerListTable.vue'
import type { DesignerListTableColumn } from '@/composables/useDesignerListGrid'

const props = defineProps<{ functionUnitId: number }>()
const { t } = useI18n()

function resolveConnectionSaveError(error: unknown): string {
  return resolveConnectionSaveErrorMessage(error, t, (e) => resolveUserFacingHttpMessage(e, t))
}

const formRef = ref<FormInstance>()

const connections = ref<EmailConnection[]>([])
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const showFormDialog = ref(false)
const showTestDialog = ref(false)
const editingId = ref<number | null>(null)
const testingConnectionId = ref<number | null>(null)
const testRecipient = ref('')

const SMTP_DEFAULT_PORT = 25
const SMTP_DEFAULT_USE_TLS = true
const SMTP_CONNECTION_TYPE: EmailProviderType = 'SMTP'

/** Dialog model — avoid `name` (reserved/conflicts with el-form + HTML form). */
type ConnectionFormState = Omit<EmailConnectionRequest, 'name'> & { senderEmail: string }

const defaultForm = (): ConnectionFormState => ({
  senderEmail: '',
  connectionType: SMTP_CONNECTION_TYPE,
  host: '',
  port: SMTP_DEFAULT_PORT,
  username: '',
  password: '',
  fromName: '',
  useTls: SMTP_DEFAULT_USE_TLS,
  enabled: true,
  direction: 'OUTBOUND',
  mailboxAddress: '',
  imapHost: '',
  imapPort: undefined,
  imapUseSsl: true
})

const form = reactive<ConnectionFormState>(defaultForm())

/** Outbound-capable connections use System Config for SMTP host/port/TLS. */
const isOutboundCapable = computed(
  () => form.direction === 'OUTBOUND' || form.direction === 'BOTH' || !form.direction,
)

const connectionFormRules = computed<FormRules>(() => {
  const rules: FormRules = {
    senderEmail: [
      { required: true, message: t('connection.emailAddressRequired'), trigger: ['blur', 'change'] },
      {
        validator: (_rule, value, callback) => {
          const text = String(value ?? '').trim()
          if (!isValidSenderEmail(text)) {
            callback(new Error(t('connection.emailAddressInvalid')))
            return
          }
          callback()
        },
        trigger: ['blur', 'change'],
      },
    ],
  }
  if (!isOutboundCapable.value) {
    rules.host = [{ required: true, message: t('connection.hostRequired'), trigger: ['blur', 'change'] }]
    rules.port = [
      {
        validator: (_rule, value, callback) => {
          if (value == null || value < 1 || value > 65535) {
            callback(new Error(t('connection.portRequired')))
            return
          }
          callback()
        },
        trigger: ['blur', 'change'],
      },
    ]
    rules.useTls = [
      {
        validator: (_rule, value, callback) => {
          if (value == null) {
            callback(new Error(t('connection.tlsRequired')))
            return
          }
          callback()
        },
        trigger: 'change',
      },
    ]
  }
  return rules
})

function clearConnectionFormValidation() {
  nextTick(() => formRef.value?.clearValidate())
}

const listColumns = computed<DesignerListTableColumn<EmailConnection>[]>(() => [
  {
    key: 'name',
    prop: 'name',
    label: t('connection.emailAddress'),
    defaultWidth: 200,
    showOverflowTooltip: true,
  },
  {
    key: 'connectionType',
    prop: 'connectionType',
    label: t('connection.providerType'),
    defaultWidth: 140,
    showOverflowTooltip: true,
    getValue: (row) => t(`connection.provider.${normalizeEmailProviderType(row.connectionType)}`),
  },
  {
    key: 'fromName',
    prop: 'fromName',
    label: t('connection.fromName'),
    defaultWidth: 140,
    showOverflowTooltip: true,
  },
  {
    key: 'enabled',
    prop: 'enabled',
    label: t('connection.enabled'),
    defaultWidth: 100,
    getValue: (row) => (row.enabled ? t('common.yes') : t('common.no')),
  },
])

/** Inbound (IMAP) fields only apply when direction includes inbound. */
const isInbound = computed(() => form.direction === 'INBOUND' || form.direction === 'BOTH')

/** Non-SMTP provider types have a built-in IMAP preset in the engine; custom SMTP must fill it in. */
const hasImapPreset = computed(() => form.connectionType !== 'SMTP')

/** IMAP host/port are mandatory only for custom SMTP used inbound (no preset to fall back on). */
const imapRequired = computed(() => isInbound.value && !hasImapPreset.value)

function buildPayload(): EmailConnectionRequest {
  const emailAddress = form.senderEmail.trim()
  const smtpUsername = form.username?.trim()
  const inbound = form.direction === 'INBOUND' || form.direction === 'BOTH'
  const outboundCapable = isOutboundCapable.value
  return {
    name: emailAddress,
    connectionType: SMTP_CONNECTION_TYPE,
    host: outboundCapable ? undefined : form.host?.trim(),
    port: outboundCapable ? undefined : form.port,
    useTls: outboundCapable ? undefined : form.useTls,
    username: smtpUsername || undefined,
    password: form.password,
    fromName: form.fromName?.trim() || undefined,
    enabled: form.enabled,
    direction: form.direction || 'OUTBOUND',
    mailboxAddress: inbound ? (form.mailboxAddress?.trim() || undefined) : undefined,
    imapHost: inbound ? (form.imapHost?.trim() || undefined) : undefined,
    imapPort: inbound ? form.imapPort : undefined,
    imapUseSsl: inbound ? form.imapUseSsl : undefined
  }
}

async function loadConnections() {
  loading.value = true
  try {
    const res = await connectionApi.list(props.functionUnitId)
    connections.value = res.data || []
  } catch {
    ElMessage.error(t('connection.loadFailed'))
  } finally {
    loading.value = false
  }
}

function scrollConnectionDialogToTop() {
  nextTick(() => {
    const body = document.querySelector('.el-dialog.connection-form-dialog .el-dialog__body')
    if (body instanceof HTMLElement) {
      body.scrollTop = 0
    }
  })
}

function openCreateDialog() {
  editingId.value = null
  Object.assign(form, defaultForm())
  showFormDialog.value = true
  clearConnectionFormValidation()
}

function openEditDialog(row: EmailConnection) {
  editingId.value = row.id
  Object.assign(form, {
    senderEmail: row.fromEmail || row.name || '',
    connectionType: SMTP_CONNECTION_TYPE,
    host: row.host,
    port: row.port ?? SMTP_DEFAULT_PORT,
    username: row.username || '',
    password: '',
    direction: row.direction || 'OUTBOUND',
    mailboxAddress: row.mailboxAddress || '',
    imapHost: row.imapHost || '',
    imapPort: row.imapPort ?? undefined,
    imapUseSsl: row.imapUseSsl ?? true,
    fromName: row.fromName || '',
    useTls: row.useTls ?? SMTP_DEFAULT_USE_TLS,
    enabled: row.enabled
  })
  showFormDialog.value = true
  clearConnectionFormValidation()
}

function openTestDialog(row: EmailConnection) {
  testingConnectionId.value = row.id
  testRecipient.value = ''
  showTestDialog.value = true
}

async function handleSave() {
  const formEl = formRef.value
  if (!formEl) return

  const valid = await formEl.validate().catch(() => false)
  if (!valid) {
    void formEl.scrollToField('senderEmail')
    return
  }

  if (imapRequired.value && !form.imapHost?.trim()) {
    ElMessage.warning({ message: t('connection.imapHostRequired'), zIndex: 10001 })
    return
  }
  if (imapRequired.value && (form.imapPort == null || form.imapPort < 1 || form.imapPort > 65535)) {
    ElMessage.warning({ message: t('connection.imapPortRequired'), zIndex: 10001 })
    return
  }
  if (form.username?.trim() && !form.password && !editingId.value) {
    ElMessage.warning({ message: t('connection.passwordRequired'), zIndex: 10001 })
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value && !payload.password) {
      delete payload.password
    }
    if (editingId.value) {
      await connectionApi.update(props.functionUnitId, editingId.value, payload)
    } else {
      await connectionApi.create(props.functionUnitId, payload)
    }
    ElMessage.success({ message: t('common.saveSuccess'), zIndex: 10001 })
    showFormDialog.value = false
    await loadConnections()
  } catch (e) {
    const msg = resolveConnectionSaveError(e)
    if (msg === t('connection.emailAddressInvalid')) {
      void formEl.validateField('senderEmail').catch(() => {})
      void formEl.scrollToField('senderEmail')
    }
    ElMessage.error({ message: msg, zIndex: 10001 })
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: EmailConnection) {
  try {
    await ElMessageBox.confirm(t('connection.deleteConfirm', { name: row.name }), t('common.confirm'), { type: 'warning' })
    await connectionApi.delete(props.functionUnitId, row.id)
    ElMessage.success(t('common.deleteSuccess'))
    await loadConnections()
  } catch (error) {
    if (isMessageBoxCancel(error)) return
    ElMessage.error(resolveUserFacingHttpMessage(error, t) || t('common.deleteFailed'))
  }
}

async function handleTest() {
  if (!testingConnectionId.value || !testRecipient.value) {
    ElMessage.warning(t('connection.testRecipientRequired'))
    return
  }
  testing.value = true
  try {
    const res = await connectionApi.test(props.functionUnitId, testingConnectionId.value, testRecipient.value)
    if (res.data?.success) {
      ElMessage.success(res.data.message || t('connection.testSuccess'))
      showTestDialog.value = false
    } else {
      ElMessage.error(formatConnectionTestFailureMessage(res.data, t))
    }
  } catch (error) {
    ElMessage.error(resolveUserFacingHttpMessage(error, t) || t('connection.testFailed'))
  } finally {
    testing.value = false
  }
}

onMounted(loadConnections)
</script>

<style scoped lang="scss">
.connection-designer {
  padding: 8px 0;
}
.designer-toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}

.connection-list-table {
  :deep(.el-table__body .cell) {
    overflow: visible;
  }

  .row-actions {
    display: inline-flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 8px;
    white-space: nowrap;
  }
}

.connection-form-dialog :deep(.el-dialog__body) {
  padding-top: 12px;
  overflow-y: auto;
}

.connection-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  .connection-section-title {
    margin: 4px 0 12px;
    font-size: 13px;
    font-weight: 600;
    color: #606266;
  }

  .connection-sender-email-item {
    margin-bottom: 18px;
  }

  .form-tip {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.4;
    color: #909399;
  }

  :deep(.el-form-item__error) {
    position: static;
    padding-top: 4px;
    line-height: 1.4;
    white-space: normal;
    word-break: break-word;
  }
}
</style>

<style lang="scss">
/* el-dialog teleports to body — unscoped so layout rules always apply */
.el-dialog.connection-form-dialog {
  margin-top: 8vh !important;
  margin-bottom: 5vh;
  max-height: 90vh;
  display: flex;
  flex-direction: column;

  .el-dialog__body {
    padding-top: 12px;
    overflow-y: auto;
  }

  .connection-sender-email-item .el-input {
    width: 100%;
  }
}
</style>
