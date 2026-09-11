<template>
  <div class="upload-max-files-editor">
    <div class="umf-input-row">
      <el-switch
        :model-value="multi"
        inline-prompt
        :active-text="t('form.uploadMulti')"
        :inactive-text="t('form.uploadSingle')"
        data-testid="upload-multi-switch"
        @update:model-value="onMultiChange"
      />
      <DesignerHelpLink
        path="/form-upload#max-files"
        :aria-label="t('form.uploadGuideLinkAria')"
        test-id="upload-max-files-guide-link"
      />
    </div>
    <div class="umf-hint">{{ t(multi ? 'form.uploadMaxFilesHint' : 'form.uploadSingleHint') }}</div>
    <div
      v-if="multi"
      class="umf-count-row"
      data-testid="upload-max-files-count"
    >
      <el-input-number
        :model-value="resolved"
        :min="2"
        :max="50"
        controls-position="right"
        style="width: 100%"
        @update:model-value="onChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import DesignerHelpLink from '@/components/designer/DesignerHelpLink.vue'
import { formControlTypeStore } from './formControlTypeStore'
import {
  DEFAULT_UPLOAD_MAX_FILES,
  maxFilesForUploadMulti,
  resolveUploadMaxFiles,
} from '@platform-shared/upload/uploadFieldValue'

const props = defineProps<{ modelValue?: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()
const { t } = useI18n()

const resolved = computed(() => {
  if (typeof props.modelValue === 'number' && props.modelValue >= 1) return props.modelValue
  return resolveUploadMaxFiles({ maxFiles: props.modelValue ?? undefined })
})
const multi = computed(() => resolved.value > 1)

function applyToActiveRule(maxFiles: number): void {
  const rule = formControlTypeStore.activeRule
  if (!rule || rule.type !== 'advancedUpload') return
  const next = (rule.props && typeof rule.props === 'object')
    ? rule.props as Record<string, unknown>
    : {}
  rule.props = next
  next.maxFiles = maxFiles
  next.limit = maxFiles
  next.multiple = maxFiles > 1
}

function commit(maxFiles: number): void {
  emit('update:modelValue', maxFiles)
  applyToActiveRule(maxFiles)
}

function onMultiChange(on: boolean | string | number): void {
  commit(maxFilesForUploadMulti(on === true, resolved.value))
}

function onChange(next: number | undefined) {
  const maxFiles = typeof next === 'number' && next >= 2 ? Math.floor(next) : DEFAULT_UPLOAD_MAX_FILES
  commit(maxFiles)
}
</script>

<style scoped>
.upload-max-files-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.umf-input-row,
.umf-count-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.umf-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
</style>
