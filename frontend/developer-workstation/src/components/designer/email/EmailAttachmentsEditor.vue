<template>
  <div
    class="email-attachments-block"
    data-testid="email-attachments-editor"
  >
    <div
      v-if="showTitle"
      class="email-attachments-label"
    >
      {{ t('properties.emailAttachments') }}
    </div>
    <div class="form-tip email-attachments-hint">
      {{ t('properties.emailAttachmentsHint') }}
    </div>
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
      data-testid="email-attachments-add"
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
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useEmailAttachmentOptionGroups } from '@/composables/email/useEmailAttachmentOptionGroups'
import { useSendTaskAttachmentFieldOptions } from '@/composables/taskProperties/useSendTaskAttachmentFieldOptions'
import { useSendTaskEmailAttachments } from '@/composables/taskProperties/useSendTaskEmailAttachments'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    functionUnitId: number
    /** Send Task shows an in-panel title; Action Design uses el-form-item label instead. */
    showTitle?: boolean
  }>(),
  {
    modelValue: '',
    showTitle: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const { t } = useI18n()

function persistAttachments(_name: string, value: unknown) {
  emit('update:modelValue', typeof value === 'string' ? value : '')
}

const {
  emailAttachments,
  loadFromExtension,
  addAttachment,
  removeAttachment,
  setAttachmentFromOption,
  selectedOptionValue,
} = useSendTaskEmailAttachments(persistAttachments)

const { fieldOptions, loadingFieldOptions, loadFieldOptions } = useSendTaskAttachmentFieldOptions()

const attachmentOptionGroups = useEmailAttachmentOptionGroups(
  fieldOptions,
  emailAttachments,
  selectedOptionValue,
  () => t('properties.emailAttachments'),
)

function onAttachmentFieldChange(index: number, val: unknown) {
  setAttachmentFromOption(index, val != null ? String(val) : '', fieldOptions.value)
}

watch(
  () => props.modelValue,
  (raw) => loadFromExtension(raw),
  { immediate: true },
)

watch(
  () => props.functionUnitId,
  (id) => {
    void loadFieldOptions(id)
  },
  { immediate: true },
)
</script>

<style scoped>
.email-attachments-block {
  width: 100%;
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
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.email-attachments-empty {
  color: #e6a23c;
  margin-top: 6px;
  font-size: 12px;
}

.email-attachment-item {
  padding: 8px;
  margin-bottom: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}

.email-field-block {
  margin-bottom: 4px;
}

.email-field-label {
  display: block;
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
</style>
