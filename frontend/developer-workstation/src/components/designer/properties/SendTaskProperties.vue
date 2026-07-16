<template>
  <div class="send-task-properties">
    <div class="send-task-email-section">
      <div class="send-task-email-section__title">
        {{ t('properties.emailConfig') }}
      </div>

      <div class="email-field-block email-to-field-wrap">
        <label class="email-field-label">
          {{ t('properties.emailTo') }}
          <span class="email-required-mark">*</span>
        </label>
        <el-input
          v-model="emailTo"
          :placeholder="t('properties.emailToPlaceholder')"
          @input="onEmailConfigChange('emailTo', emailTo)"
        />
        <div class="form-tip">{{ t('properties.emailToHint') }}</div>
      </div>

      <div class="email-field-block">
        <label class="email-field-label">
          {{ t('properties.emailConnection') }}
          <span class="email-required-mark">*</span>
        </label>
        <el-select
          v-model="connectionId"
          :placeholder="t('properties.emailConnectionPlaceholder')"
          style="width: 100%"
          clearable
          @change="onEmailConfigChange('connectionId', connectionId)"
        >
          <el-option
            v-for="conn in emailConnections"
            :key="conn.connectionUid"
            :label="conn.name"
            :value="conn.connectionUid"
          />
        </el-select>
        <div class="form-tip">{{ t('properties.emailConnectionHint') }}</div>
      </div>

      <div class="email-field-block">
        <label class="email-field-label">
          {{ t('properties.emailFrom') }}
          <span class="email-required-mark">*</span>
        </label>
        <el-input
          v-model="emailFrom"
          :placeholder="t('properties.emailFromPlaceholder')"
          @input="onEmailConfigChange('emailFrom', emailFrom)"
        />
        <div class="form-tip">{{ t('properties.emailFromHint') }}</div>
      </div>

      <div class="email-field-block">
        <label class="email-field-label">{{ t('properties.emailTemplate') }}</label>
        <el-select
          v-model="emailTemplateId"
          :placeholder="t('properties.emailTemplatePlaceholder')"
          style="width: 100%"
          clearable
          @change="applyEmailTemplate"
        >
          <el-option
            v-for="tpl in emailTemplates"
            :key="tpl.id"
            :label="tpl.name"
            :value="String(tpl.id)"
          />
        </el-select>
        <div class="form-tip">{{ t('properties.emailTemplateHint') }}</div>
      </div>

      <div class="email-field-block">
        <label class="email-field-label">
          {{ t('properties.emailSubject') }}
          <span class="email-required-mark">*</span>
        </label>
        <el-input
          v-model="emailSubject"
          :placeholder="t('properties.emailSubjectPlaceholder', { example: EMAIL_SUBJECT_VAR_EXAMPLE })"
          @input="onEmailConfigChange('emailSubject', emailSubject)"
        />
      </div>

      <div class="email-field-block">
        <label class="email-field-label">
          {{ t('properties.emailBody') }}
          <span class="email-required-mark">*</span>
        </label>
        <EmailRichBodyEditor
          v-model="emailBody"
          :function-unit-id="props.functionUnitId"
          @update:model-value="onEmailBodyChange"
        />
      </div>

      <div class="email-advanced-toggle">
        <el-button
          link
          type="primary"
          @click="emailAdvancedOpen = !emailAdvancedOpen"
        >
          {{ emailAdvancedOpen ? t('properties.emailHideAdvanced') : t('properties.emailShowAdvanced') }}
          <el-icon class="email-advanced-chevron" :class="{ open: emailAdvancedOpen }">
            <ArrowDown />
          </el-icon>
        </el-button>
      </div>

      <div
        v-show="emailAdvancedOpen"
        class="email-advanced-panel"
      >
        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailCc') }}</label>
          <el-input
            v-model="emailCc"
            :placeholder="t('properties.emailCcPlaceholder')"
            @input="onEmailConfigChange('emailCc', emailCc)"
          />
        </div>
        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailBcc') }}</label>
          <el-input
            v-model="emailBcc"
            :placeholder="t('properties.emailBccPlaceholder')"
            @input="onEmailConfigChange('emailBcc', emailBcc)"
          />
        </div>
        <div class="email-attachments-block">
          <div class="email-attachments-label">{{ t('properties.emailAttachments') }}</div>
          <div
            v-for="(att, index) in emailAttachments"
            :key="index"
            class="email-attachment-item"
          >
            <div class="email-field-block">
              <label class="email-field-label">{{ t('properties.emailAttachmentName') }}</label>
              <el-input
                v-model="att.name"
                :placeholder="t('properties.emailAttachmentNamePlaceholder')"
                @input="onAttachmentChange"
              />
            </div>
            <div class="email-field-block">
              <label class="email-field-label">{{ t('properties.emailAttachmentContent') }}</label>
              <el-input
                v-model="att.content"
                type="textarea"
                :rows="2"
                :placeholder="t('properties.emailAttachmentContentPlaceholder')"
                @input="onAttachmentChange"
              />
            </div>
            <el-button
              link
              type="danger"
              @click="removeAttachment(index)"
            >
              {{ t('common.delete') }}
            </el-button>
          </div>
          <el-button
            size="small"
            @click="addAttachment"
          >
            {{ t('properties.emailAddAttachment') }}
          </el-button>
        </div>
        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailSensitivity') }}</label>
          <el-select
            v-model="emailSensitivity"
            style="width: 100%"
            @change="onEmailConfigChange('emailSensitivity', emailSensitivity)"
          >
            <el-option
              v-for="opt in emailSensitivityOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailReplyTo') }}</label>
          <el-input
            v-model="emailReplyTo"
            :placeholder="t('properties.emailReplyToPlaceholder')"
            @input="onEmailConfigChange('emailReplyTo', emailReplyTo)"
          />
        </div>
        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailImportance') }}</label>
          <el-select
            v-model="emailImportance"
            style="width: 100%"
            @change="onEmailConfigChange('emailImportance', emailImportance)"
          >
            <el-option
              v-for="opt in emailImportanceOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
      </div>
    </div>

    <el-collapse v-model="activeGroups">
      <el-collapse-item
        :title="t('properties.basic')"
        name="basic"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.taskId')">
            <el-input
              :model-value="basicProps.id"
              disabled
            />
          </el-form-item>
          <el-form-item :label="t('common.type')">
            <el-input
              :model-value="taskTypeLabel"
              disabled
            />
          </el-form-item>
          <el-form-item :label="t('properties.taskName')">
            <el-input
              v-model="taskName"
              :placeholder="t('properties.taskName')"
              @change="updateBasicProp('name', taskName)"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { getExtensionProperties } from '@/utils/bpmnExtensions'
import { emailTemplateApi } from '@/api/emailTemplate'
import EmailRichBodyEditor from '@/components/designer/email/EmailRichBodyEditor.vue'
import { useTaskPropertiesState } from '@/composables/taskProperties/useTaskPropertiesState'
import { useTaskPropertiesForms } from '@/composables/taskProperties/useTaskPropertiesForms'
import { useSendTaskEmailAttachments } from '@/composables/taskProperties/useSendTaskEmailAttachments'
import { EMAIL_SUBJECT_VAR_EXAMPLE } from '@/composables/email/useEmailTemplateVariables'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic'])
const emailAdvancedOpen = ref(false)

const propsAccessor = reactive({
  get modeler() {
    return props.modeler
  },
  get element() {
    return props.element
  },
  get functionUnitId() {
    return props.functionUnitId
  }
})

const {
  taskName,
  connectionId,
  emailTemplateId,
  emailFrom,
  emailTo,
  emailCc,
  emailBcc,
  emailReplyTo,
  emailImportance,
  emailSensitivity,
  emailSubject,
  emailBody,
  basicProps,
  taskTypeLabel,
  loadProperties,
  updateBasicProp,
  updateExtProp,
  onEmailConfigChange
} = useTaskPropertiesState(propsAccessor, t)

const emailImportanceOptions = computed(() => [
  { value: 'low', label: t('properties.emailImportanceLow') },
  { value: 'normal', label: t('properties.emailImportanceNormal') },
  { value: 'high', label: t('properties.emailImportanceHigh') }
])

const emailSensitivityOptions = computed(() => [
  { value: 'normal', label: t('properties.emailSensitivityNormal') },
  { value: 'personal', label: t('properties.emailSensitivityPersonal') },
  { value: 'private', label: t('properties.emailSensitivityPrivate') },
  { value: 'confidential', label: t('properties.emailSensitivityConfidential') }
])

const {
  emailAttachments,
  loadFromExtension: loadEmailAttachments,
  addAttachment,
  removeAttachment,
  onAttachmentChange
} = useSendTaskEmailAttachments(updateExtProp)

const {
  emailConnections,
  emailTemplates,
  loadEmailConnections,
  loadEmailTemplates
} = useTaskPropertiesForms(propsAccessor, {
  formId: ref(null),
  updateExtProp
})

function decodeHtmlEntities(text: string): string {
  if (!text || typeof document === 'undefined') return text
  const ta = document.createElement('textarea')
  ta.innerHTML = text
  return ta.value
}

function loadSendTaskProperties() {
  loadProperties()
  if (!props.element) return
  const ext = getExtensionProperties(props.element)
  if (ext.emailBody) {
    emailBody.value = decodeHtmlEntities(String(ext.emailBody))
  }
  loadEmailAttachments(ext.emailAttachments)
}

function onEmailBodyChange(value: string) {
  emailBody.value = value
  onEmailConfigChange('emailBody', value)
}

async function applyEmailTemplate(templateId: string) {
  updateExtProp('emailTemplateId', templateId || '')
  if (!templateId) return
  try {
    const res = await emailTemplateApi.get(props.functionUnitId, Number(templateId))
    const tpl = res.data
    emailSubject.value = tpl.subject || ''
    emailBody.value = tpl.bodyHtml || ''
    onEmailConfigChange('emailSubject', emailSubject.value)
    onEmailConfigChange('emailBody', emailBody.value)
  } catch {
    ElMessage.error(t('properties.emailTemplateLoadFailed'))
  }
}

watch(
  () => props.element,
  async () => {
    loadSendTaskProperties()
    await loadEmailTemplates()
  },
  { immediate: true }
)

onMounted(() => {
  loadEmailConnections()
})
</script>

<style lang="scss" scoped>
.send-task-properties {
  min-height: 200px;

  :deep(.el-collapse) {
    border: none;

    .el-collapse-item__header {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
      background: #fafafa;
      padding: 0 12px;
      height: 36px;
      line-height: 36px;
      border-radius: 4px;
      margin-bottom: 8px;
    }

    .el-collapse-item__wrap {
      border: none;
    }

    .el-collapse-item__content {
      padding: 0 4px 12px;
    }
  }

  :deep(.el-form-item) {
    margin-bottom: 12px;

    .el-form-item__label {
      font-size: 12px;
      color: #606266;
      padding-bottom: 4px;
    }
  }
}

.send-task-email-section {
  margin-bottom: 12px;
  padding: 8px 4px 4px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;
    padding: 0 8px;
  }
}

.email-field-block {
  margin-bottom: 12px;
  padding: 0 4px;

  .email-field-label {
    display: block;
    font-size: 12px;
    font-weight: 600;
    color: #606266;
    padding-bottom: 4px;
    line-height: 1.4;
  }
}

.email-to-field-wrap .email-field-label {
  font-weight: 700;
  color: #303133;
}

.email-required-mark {
  color: #f56c6c;
  margin-left: 2px;
}

.form-tip {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}

.email-advanced-toggle {
  padding: 0 4px 8px;
}

.email-advanced-chevron {
  margin-left: 4px;
  transition: transform 0.2s;

  &.open {
    transform: rotate(180deg);
  }
}

.email-advanced-panel {
  padding-top: 4px;
  border-top: 1px dashed #e4e7ed;
}

.email-attachments-block {
  margin-bottom: 12px;
  padding: 0 4px;
}

.email-attachments-label {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.email-attachment-item {
  padding: 8px;
  margin-bottom: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}
</style>
