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
            v-if="isOutboundCapableRow(row as EmailConnection)"
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
        <el-form-item :label="t('connection.direction')">
          <el-select v-model="form.direction" style="width: 100%">
            <el-option :label="t('connection.directionOutbound')" value="OUTBOUND" />
            <el-option :label="t('connection.directionInbound')" value="INBOUND" />
          </el-select>
          <div v-if="legacyBothEditing" class="form-tip">{{ t('connection.directionLegacyBothHint') }}</div>
          <div class="form-tip">{{ directionHint }}</div>
        </el-form-item>

        <!-- Monitor-only: mailbox + IMAP credentials; server from System Config -->
        <template v-if="isInboundOnly">
          <p class="connection-section-title">{{ t('connection.monitorSection') }}</p>
          <p class="form-tip connection-system-imap-hint">{{ t('connection.systemImapFromAdminHint') }}</p>
          <el-form-item
            prop="senderEmail"
            class="connection-sender-email-item"
            :label="t('connection.monitorMailboxEmail')"
            required
          >
            <el-input
              v-model="form.senderEmail"
              autocomplete="off"
              :placeholder="t('connection.monitorMailboxEmailPlaceholder')"
            />
            <div class="form-tip">{{ t('connection.monitorMailboxEmailHint') }}</div>
          </el-form-item>
          <el-form-item :label="t('connection.monitorUsername')">
            <el-input
              v-model="form.username"
              autocomplete="off"
              :placeholder="t('connection.monitorUsernamePlaceholder')"
            />
            <div class="form-tip">{{ t('connection.monitorUsernameHint') }}</div>
          </el-form-item>
          <el-form-item :label="t('connection.monitorPassword')" :required="!editingId">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="editingId ? t('connection.passwordKeep') : ''"
            />
            <div class="form-tip">{{ t('connection.monitorPasswordHint') }}</div>
          </el-form-item>
        </template>

        <!-- Outbound send: sender identity + SMTP from System Config -->
        <template v-else>
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
          <p class="connection-section-title">{{ t('connection.smtpSection') }}</p>
          <p class="form-tip connection-system-smtp-hint">{{ t('connection.systemSmtpFromAdminHint') }}</p>
          <el-form-item :label="t('connection.username')">
            <el-input
              v-model="form.username"
              autocomplete="off"
              :placeholder="t('connection.usernamePlaceholder')"
            />
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

const SMTP_CONNECTION_TYPE: EmailProviderType = 'SMTP'

/** Dialog model — avoid `name` (reserved/conflicts with el-form + HTML form). */
type ConnectionFormState = Omit<EmailConnectionRequest, 'name'> & { senderEmail: string }

const defaultForm = (): ConnectionFormState => ({
  senderEmail: '',
  connectionType: SMTP_CONNECTION_TYPE,
  username: '',
  password: '',
  fromName: '',
  enabled: true,
  direction: 'OUTBOUND',
})

const form = reactive<ConnectionFormState>(defaultForm())

const isInboundOnly = computed(() => form.direction === 'INBOUND')
const legacyBothEditing = ref(false)

const directionHint = computed(() =>
  isInboundOnly.value ? t('connection.directionHintMonitor') : t('connection.directionHintOutbound'),
)

function isOutboundCapableRow(row: EmailConnection): boolean {
  const direction = row.direction || 'OUTBOUND'
  return direction === 'OUTBOUND' || direction === 'BOTH'
}

const connectionFormRules = computed<FormRules>(() => ({
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
}))

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

function buildPayload(): EmailConnectionRequest {
  const emailAddress = form.senderEmail.trim()
  const login = form.username?.trim()
  return {
    name: emailAddress,
    connectionType: SMTP_CONNECTION_TYPE,
    username: login || undefined,
    password: form.password,
    fromName: isInboundOnly.value ? undefined : (form.fromName?.trim() || undefined),
    enabled: form.enabled,
    direction: form.direction || 'OUTBOUND',
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
  legacyBothEditing.value = false
  Object.assign(form, defaultForm())
  showFormDialog.value = true
  clearConnectionFormValidation()
}

function openEditDialog(row: EmailConnection) {
  editingId.value = row.id
  const rowDirection = row.direction || 'OUTBOUND'
  legacyBothEditing.value = rowDirection === 'BOTH'
  Object.assign(form, {
    senderEmail: row.fromEmail || row.name || '',
    connectionType: SMTP_CONNECTION_TYPE,
    username: row.username || '',
    password: '',
    direction: rowDirection === 'BOTH' ? 'OUTBOUND' : rowDirection,
    fromName: row.fromName || '',
    enabled: row.enabled,
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
