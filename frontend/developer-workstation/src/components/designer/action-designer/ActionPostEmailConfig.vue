<template>
  <div
    class="action-post-email"
    data-testid="action-post-email-config"
  >
  <el-divider>
    <span class="post-email-title-row">
      {{ t('action.postEmail.title') }}
      <DesignerHelpLink
        path="/action-email"
        :ariaLabel="t('action.postEmail.guideLinkAria')"
        test-id="action-post-email-guide-link"
      />
    </span>
  </el-divider>
  <el-alert
    v-if="unsupported"
    type="info"
    :closable="false"
    :title="t('action.postEmail.unsupportedHint')"
    style="margin-bottom: 12px"
  />
  <el-form-item :label="t('action.postEmail.enabled')">
    <el-switch
      :model-value="model.enabled === true"
      :disabled="unsupported"
      data-testid="action-post-email-enabled"
      @update:model-value="patch({ enabled: $event })"
    />
  </el-form-item>
  <template v-if="model.enabled && !unsupported">
    <el-form-item :label="t('action.postEmail.emailTo')">
      <RecipientExpressionField
        :model-value="model.emailTo || ''"
        :placeholder="t('action.postEmail.emailToPlaceholder')"
        :groups="recipientVariableGroups"
        :loading="variablesLoading"
        @update:model-value="patch({ emailTo: $event })"
      />
    </el-form-item>
    <el-form-item :label="t('action.postEmail.connection')">
      <el-select
        :model-value="model.connectionId || ''"
        clearable
        style="width: 100%"
        :placeholder="t('action.postEmail.connectionPlaceholder')"
        @change="(val: string) => patch({ connectionId: val })"
      >
        <el-option
          v-for="conn in emailConnections"
          :key="conn.connectionUid"
          :label="conn.name"
          :value="conn.connectionUid"
        />
      </el-select>
    </el-form-item>
    <el-form-item :label="t('action.postEmail.template')">
      <el-select
        :model-value="model.emailTemplateId || ''"
        clearable
        style="width: 100%"
        :placeholder="t('action.postEmail.templatePlaceholder')"
        @change="(val: string) => patch({ emailTemplateId: val })"
      >
        <el-option
          v-for="tpl in emailTemplates"
          :key="tpl.id"
          :label="tpl.name"
          :value="String(tpl.id)"
        />
      </el-select>
    </el-form-item>
    <el-form-item>
      <el-button
        link
        type="primary"
        @click="advancedOpen = !advancedOpen"
      >
        {{ advancedOpen ? t('action.postEmail.hideAdvanced') : t('action.postEmail.showAdvanced') }}
      </el-button>
    </el-form-item>
    <template v-if="advancedOpen">
      <el-form-item :label="t('action.postEmail.emailFrom')">
        <RecipientExpressionField
          :model-value="model.emailFrom || ''"
          :placeholder="t('action.postEmail.emailFromPlaceholder')"
          :groups="recipientVariableGroups"
          :loading="variablesLoading"
          @update:model-value="patch({ emailFrom: $event })"
        />
      </el-form-item>
      <el-form-item :label="t('action.postEmail.emailCc')">
        <RecipientExpressionField
          :model-value="model.emailCc || ''"
          :placeholder="t('action.postEmail.emailCcPlaceholder')"
          :groups="recipientVariableGroups"
          :loading="variablesLoading"
          @update:model-value="patch({ emailCc: $event })"
        />
      </el-form-item>
      <el-form-item :label="t('action.postEmail.emailBcc')">
        <RecipientExpressionField
          :model-value="model.emailBcc || ''"
          :placeholder="t('action.postEmail.emailBccPlaceholder')"
          :groups="recipientVariableGroups"
          :loading="variablesLoading"
          @update:model-value="patch({ emailBcc: $event })"
        />
      </el-form-item>
      <el-form-item :label="t('action.postEmail.attachments')">
        <EmailAttachmentsEditor
          :model-value="model.emailAttachments || ''"
          :function-unit-id="functionUnitId"
          :show-title="false"
          @update:model-value="patch({ emailAttachments: $event })"
        />
      </el-form-item>
    </template>
  </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import DesignerHelpLink from '@/components/designer/DesignerHelpLink.vue'
import EmailAttachmentsEditor from '@/components/designer/email/EmailAttachmentsEditor.vue'
import RecipientExpressionField from '@/components/designer/email/RecipientExpressionField.vue'
import { connectionApi, type EmailConnection } from '@/api/connection'
import { emailTemplateApi, type EmailTemplate } from '@/api/emailTemplate'
import { useEmailTemplateVariables } from '@/composables/email/useEmailTemplateVariables'
import { parseAttachments } from '@/composables/taskProperties/useSendTaskEmailAttachments'
import { filterGroupsForSendTaskRecipient } from '@/utils/sendTaskJuelVariables'

const UNSUPPORTED = new Set(['API_CALL', 'CUSTOM_SCRIPT', 'COMPOSITE'])

export interface ActionPostEmailModel {
  enabled?: boolean
  connectionId?: string
  emailTo?: string
  emailCc?: string
  emailBcc?: string
  emailFrom?: string
  emailTemplateId?: string
  emailAttachments?: string
}

const props = defineProps<{
  modelValue: ActionPostEmailModel
  actionType: string
  functionUnitId: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: ActionPostEmailModel]
}>()

const { t } = useI18n()
const advancedOpen = ref(false)
const emailConnections = ref<EmailConnection[]>([])
const emailTemplates = ref<EmailTemplate[]>([])
const { groups: templateVariableGroups, loading: variablesLoading, load: loadTemplateVariables } =
  useEmailTemplateVariables(() => props.functionUnitId)
const recipientVariableGroups = computed(() =>
  filterGroupsForSendTaskRecipient(templateVariableGroups.value),
)
const model = computed(() => props.modelValue ?? {})
const unsupported = computed(() => UNSUPPORTED.has((props.actionType || '').toUpperCase()))

function patch(partial: Partial<ActionPostEmailModel>) {
  emit('update:modelValue', { ...model.value, ...partial })
}

async function loadLookups() {
  try {
    const [connRes, tplRes] = await Promise.all([
      connectionApi.list(props.functionUnitId),
      emailTemplateApi.list(props.functionUnitId),
    ])
    emailConnections.value = (connRes.data || []).filter((c) => {
      const direction = c.direction || 'OUTBOUND'
      return direction === 'OUTBOUND' || direction === 'BOTH'
    })
    emailTemplates.value = (tplRes.data || []).filter((tpl) => tpl.enabled)
  } catch {
    emailConnections.value = []
    emailTemplates.value = []
  }
  await loadTemplateVariables()
}

watch(unsupported, (isUnsupported) => {
  if (isUnsupported && model.value.enabled) {
    patch({ enabled: false })
  }
})

watch(
  () => model.value.emailAttachments,
  (raw) => {
    if (parseAttachments(raw).length > 0) {
      advancedOpen.value = true
    }
  },
  { immediate: true },
)

onMounted(loadLookups)
</script>

<style scoped>
.post-email-title-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
