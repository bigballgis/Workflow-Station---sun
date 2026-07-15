<template>
  <div class="connection-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="openCreateDialog">{{ t('connection.create') }}</el-button>
      <el-button @click="loadConnections" :loading="loading">
        <el-icon><Refresh /></el-icon> {{ t('common.refresh') }}
      </el-button>
    </div>

    <el-table :data="connections" v-loading="loading" stripe>
      <el-table-column prop="name" :label="t('connection.emailAddress')" min-width="200" />
      <el-table-column prop="connectionType" :label="t('connection.providerType')" width="140">
        <template #default="{ row }">
          {{ t(`connection.provider.${normalizeEmailProviderType(row.connectionType)}`) }}
        </template>
      </el-table-column>
      <el-table-column prop="fromName" :label="t('connection.fromName')" width="140" />
      <el-table-column prop="enabled" :label="t('connection.enabled')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? t('common.yes') : t('common.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row as EmailConnection)">{{ t('common.edit') }}</el-button>
          <el-button link type="success" @click="openTestDialog(row as EmailConnection)">{{ t('connection.test') }}</el-button>
          <el-button link type="danger" @click="handleDelete(row as EmailConnection)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="showFormDialog"
      :title="editingId ? t('connection.edit') : t('connection.create')"
      width="500px"
      destroy-on-close
    >
      <el-form :model="form" label-position="top" class="connection-form">
        <el-form-item :label="t('connection.emailAddress')" required>
          <el-input
            v-model="form.name"
            type="email"
            autocomplete="email"
            placeholder="1527598351@qq.com"
          />
          <div class="form-tip">{{ t('connection.emailAddressHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('connection.providerType')" required>
          <el-select v-model="form.connectionType" style="width: 100%">
            <el-option
              v-for="provider in EMAIL_PROVIDER_OPTIONS"
              :key="provider"
              :label="t(`connection.provider.${provider}`)"
              :value="provider"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('connection.host')" required>
          <el-input
            v-model="form.host"
            placeholder="smtp.example.com"
            autocomplete="off"
          />
          <div class="form-tip">{{ t('connection.smtpHostHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('connection.port')" required>
          <el-input-number
            v-model="form.port"
            :min="1"
            :max="65535"
            controls-position="right"
            style="width: 100%"
          />
          <div class="form-tip">{{ t('connection.smtpPortHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('connection.useTls')" required>
          <el-radio-group v-model="form.useTls">
            <el-radio :value="true">{{ t('common.yes') }}</el-radio>
            <el-radio :value="false">{{ t('common.no') }}</el-radio>
          </el-radio-group>
          <div class="form-tip">{{ t('connection.smtpTlsHint') }}</div>
        </el-form-item>
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
        <el-form-item :label="t('connection.fromName')">
          <el-input v-model="form.fromName" :placeholder="t('connection.fromNamePlaceholder')" />
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

    <el-dialog v-model="showTestDialog" :title="t('connection.test')" width="420px">
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
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { connectionApi, type EmailConnection, type EmailConnectionRequest } from '@/api/connection'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import {
  EMAIL_PROVIDER_OPTIONS,
  normalizeEmailProviderType,
  type EmailProviderType
} from '@/utils/emailProviderPresets'

const props = defineProps<{ functionUnitId: number }>()
const { t } = useI18n()

const connections = ref<EmailConnection[]>([])
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const showFormDialog = ref(false)
const showTestDialog = ref(false)
const editingId = ref<number | null>(null)
const testingConnectionId = ref<number | null>(null)
const testRecipient = ref('')

const defaultForm = (): EmailConnectionRequest => ({
  name: '',
  connectionType: 'SMTP',
  host: '',
  port: undefined,
  username: '',
  password: '',
  fromName: '',
  useTls: false,
  enabled: true,
  direction: 'OUTBOUND',
  mailboxAddress: '',
  imapHost: '',
  imapPort: undefined,
  imapUseSsl: true
})

const form = reactive<EmailConnectionRequest>(defaultForm())

/** Inbound (IMAP) fields only apply when direction includes inbound. */
const isInbound = computed(() => form.direction === 'INBOUND' || form.direction === 'BOTH')

/** Non-SMTP provider types have a built-in IMAP preset in the engine; custom SMTP must fill it in. */
const hasImapPreset = computed(() => form.connectionType !== 'SMTP')

/** IMAP host/port are mandatory only for custom SMTP used inbound (no preset to fall back on). */
const imapRequired = computed(() => isInbound.value && !hasImapPreset.value)

function buildPayload(): EmailConnectionRequest {
  const emailAddress = form.name.trim()
  const inbound = form.direction === 'INBOUND' || form.direction === 'BOTH'
  return {
    name: emailAddress,
    connectionType: form.connectionType as EmailProviderType,
    host: form.host?.trim(),
    port: form.port,
    useTls: form.useTls,
    username: form.username?.trim() || emailAddress,
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

function openCreateDialog() {
  editingId.value = null
  Object.assign(form, defaultForm())
  showFormDialog.value = true
}

function openEditDialog(row: EmailConnection) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    connectionType: normalizeEmailProviderType(row.connectionType),
    host: row.host,
    port: row.port,
    username: row.username || row.name,
    password: '',
    direction: row.direction || 'OUTBOUND',
    mailboxAddress: row.mailboxAddress || '',
    imapHost: row.imapHost || '',
    imapPort: row.imapPort ?? undefined,
    imapUseSsl: row.imapUseSsl ?? true,
    fromName: row.fromName || '',
    useTls: row.useTls,
    enabled: row.enabled
  })
  showFormDialog.value = true
}

function openTestDialog(row: EmailConnection) {
  testingConnectionId.value = row.id
  testRecipient.value = ''
  showTestDialog.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning(t('connection.emailAddressRequired'))
    return
  }
  if (!form.host?.trim()) {
    ElMessage.warning(t('connection.hostRequired'))
    return
  }
  if (form.port == null || form.port < 1 || form.port > 65535) {
    ElMessage.warning(t('connection.portRequired'))
    return
  }
  if (form.useTls == null) {
    ElMessage.warning(t('connection.tlsRequired'))
    return
  }
  if (imapRequired.value && !form.imapHost?.trim()) {
    ElMessage.warning(t('connection.imapHostRequired'))
    return
  }
  if (imapRequired.value && (form.imapPort == null || form.imapPort < 1 || form.imapPort > 65535)) {
    ElMessage.warning(t('connection.imapPortRequired'))
    return
  }
  if (!editingId.value && !form.password) {
    ElMessage.warning(t('connection.passwordRequired'))
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
    ElMessage.success(t('common.saveSuccess'))
    showFormDialog.value = false
    await loadConnections()
  } catch (e) {
    ElMessage.error(resolveUserFacingHttpMessage(e, t) || t('common.saveFailed'))
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
  } catch {
    // cancelled or failed
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
      ElMessage.error(res.data?.message || t('connection.testFailed'))
    }
  } catch {
    ElMessage.error(t('connection.testFailed'))
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

.connection-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  .form-tip {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.4;
    color: #909399;
  }
}
</style>
