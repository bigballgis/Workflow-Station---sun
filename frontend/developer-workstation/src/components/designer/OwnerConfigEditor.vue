<template>
  <div class="owner-config-editor">
    <el-radio-group :model-value="source" @change="onSourceChange">
      <el-radio value="CREATOR">{{ t('form.ownerSourceCreator') }}</el-radio>
      <el-radio value="CURRENT_ASSIGNEE">{{ t('form.ownerSourceCurrentAssignee') }}</el-radio>
    </el-radio-group>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * Property-panel editor for Owner `ownerConfig`.
 * Contract (docs/design/owner-field-component.md §4.1): JSON string
 * `{"source":"CREATOR"|"CURRENT_ASSIGNEE"}`.
 */
const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const { t } = useI18n()

const source = computed(() => {
  try {
    const parsed = JSON.parse(props.modelValue || '{}') as { source?: unknown }
    return parsed?.source === 'CURRENT_ASSIGNEE' ? 'CURRENT_ASSIGNEE' : 'CREATOR'
  } catch {
    return 'CREATOR'
  }
})

function onSourceChange(value: string | number | boolean) {
  const next = value === 'CURRENT_ASSIGNEE' ? 'CURRENT_ASSIGNEE' : 'CREATOR'
  emit('update:modelValue', JSON.stringify({ source: next }))
}
</script>

<style scoped>
.owner-config-editor {
  display: flex;
  align-items: center;
  width: 100%;
}
</style>
