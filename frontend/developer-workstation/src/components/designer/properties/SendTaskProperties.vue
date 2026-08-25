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
        </label>
        <el-input
          v-model="emailFrom"
          :placeholder="t('properties.emailFromPlaceholder')"
          @input="onEmailConfigChange('emailFrom', emailFrom)"
        />
        <div class="form-tip">{{ t('properties.emailFromHint') }}</div>
      </div>

      <div class="email-field-block">
        <label class="email-field-label">
          {{ t('properties.emailTemplate') }}
          <span class="email-required-mark">*</span>
        </label>
        <el-select
          v-model="emailTemplateId"
          :placeholder="t('properties.emailTemplatePlaceholder')"
          style="width: 100%"
          clearable
          @change="onEmailTemplateChange"
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
          <div class="form-tip email-attachments-hint">{{ t('properties.emailAttachmentsHint') }}</div>
          <div
            v-for="(att, index) in emailAttachments"
            :key="index"
            class="email-attachment-item"
          >
            <div class="email-field-block">
              <label class="email-field-label">{{ t('properties.emailAttachmentField') }}</label>
              <el-select
                :model-value="selectedOptionValue(att)"
                :placeholder="t('properties.emailAttachmentFieldPlaceholder')"
                :loading="loadingFieldOptions"
                filterable
                clearable
                style="width: 100%"
                @change="(val) => onAttachmentFieldChange(index, val)"
              >
                <el-option-group
                  v-for="group in attachmentOptionGroups"
                  :key="group.label"
                  :label="group.label"
                >
                  <el-option
                    v-for="opt in group.options"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-option-group>
              </el-select>
            </div>
            <el-button
              link
              type="danger"
              @click="removeAttachment(index)"
            >
              {{ t('properties.emailAttachmentRemove') }}
            </el-button>
          </div>
          <el-button
            size="small"
            :disabled="fieldOptions.length === 0 || loadingFieldOptions"
            @click="addAttachment"
          >
            {{ t('properties.emailAddAttachment') }}
          </el-button>
          <div
            v-if="!loadingFieldOptions && fieldOptions.length === 0"
            class="form-tip email-attachments-empty"
          >
            {{ t('properties.emailAttachmentsEmpty') }}
          </div>
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
import { ArrowDown } from '@element-plus/icons-vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { getExtensionProperties } from '@/utils/bpmnExtensions'
import { useTaskPropertiesState } from '@/composables/taskProperties/useTaskPropertiesState'
import { useTaskPropertiesForms } from '@/composables/taskProperties/useTaskPropertiesForms'
import { useSendTaskEmailAttachments } from '@/composables/taskProperties/useSendTaskEmailAttachments'
import { useSendTaskAttachmentFieldOptions } from '@/composables/taskProperties/useSendTaskAttachmentFieldOptions'
import { resolveOwnedSendTaskConnectionUid } from '@/composables/taskProperties/resolveOwnedSendTaskConnectionUid'

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
  setAttachmentFromOption,
  selectedOptionValue
} = useSendTaskEmailAttachments(updateExtProp)

const {
  fieldOptions,
  loadingFieldOptions,
  loadFieldOptions
} = useSendTaskAttachmentFieldOptions()

const attachmentOptionGroups = computed(() => {
  const byGroup = new Map<string, typeof fieldOptions.value>()
  for (const opt of fieldOptions.value) {
    const list = byGroup.get(opt.group) || []
    list.push(opt)
    byGroup.set(opt.group, list)
  }
  // Ensure already-saved refs remain visible/selectable even if catalog reload lags.
  const known = new Set(fieldOptions.value.map(o => o.value))
  for (const att of emailAttachments.value) {
    const value = selectedOptionValue(att)
    if (!value || known.has(value)) continue
    const label = value.startsWith('lookup:')
      ? value.slice('lookup:'.length)
      : (att.fieldName || value)
    const group = t('properties.emailAttachments')
    const list = byGroup.get(group) || []
    list.push({ value, label, group, ref: { ...att } })
    byGroup.set(group, list)
    known.add(value)
  }
  return Array.from(byGroup.entries()).map(([label, options]) => ({ label, options }))
})

const {
  emailConnections,
  emailTemplates,
  loadEmailConnections,
  loadEmailTemplates
} = useTaskPropertiesForms(propsAccessor, {
  formId: ref(null),
  updateExtProp
})

function bindOwnedEmailConnection() {
  const next = resolveOwnedSendTaskConnectionUid(connectionId.value, emailConnections.value)
  if (next === connectionId.value) {
    return
  }
  connectionId.value = next
  onEmailConfigChange('connectionId', next)
}

async function loadEmailConnectionsAndBind() {
  await loadEmailConnections()
  bindOwnedEmailConnection()
}

function loadSendTaskProperties() {
  loadProperties()
  if (!props.element) return
  const ext = getExtensionProperties(props.element)
  loadEmailAttachments(ext.emailAttachments)
  // Keep advanced panel open when attachments already configured so they stay visible.
  if (emailAttachments.value.length > 0) {
    emailAdvancedOpen.value = true
  }
  bindOwnedEmailConnection()
}

function onAttachmentFieldChange(index: number, val: unknown) {
  setAttachmentFromOption(index, val != null ? String(val) : '', fieldOptions.value)
}

/**
 * Persist template id only. Subject/body stay on the Email Template entity and are
 * resolved at runtime — never copied into Send Task extension properties.
 */
function onEmailTemplateChange(templateId: string) {
  updateExtProp('emailTemplateId', templateId || '')
  // Clear legacy inline content so BPMN no longer carries editable subject/body.
  updateExtProp('emailSubject', '')
  updateExtProp('emailBody', '')
}

watch(
  () => props.element,
  async () => {
    loadSendTaskProperties()
    await loadEmailTemplates()
  },
  { immediate: true }
)

watch(
  () => props.functionUnitId,
  (id) => {
    if (id) loadFieldOptions(id)
  },
  { immediate: true }
)

onMounted(() => {
  void loadEmailConnectionsAndBind()
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
  margin-bottom: 4px;
}

.email-attachments-hint {
  margin-bottom: 8px;
}

.email-attachments-empty {
  color: #e6a23c;
  margin-top: 6px;
}

.email-attachment-item {
  padding: 8px;
  margin-bottom: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}
</style>
