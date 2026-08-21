<template>
  <el-select
    :model-value="currentType"
    style="width: 100%"
    @update:model-value="onChange"
  >
    <el-option :label="t('form.ownerControlTypeInput')" value="input" />
    <el-option :label="t('form.ownerControlTypeOwner')" value="owner" />
  </el-select>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formControlTypeStore } from './formControlTypeStore'

/**
 * Props-panel control that switches a VARCHAR field between Input and Owner.
 * Mutates the active form-create rule (docs/design/owner-field-component.md §5.1).
 */
defineProps<{
  modelValue?: string
}>()

const { t } = useI18n()

const currentType = computed(() => {
  const type = formControlTypeStore.activeRule?.type
  return type === 'owner' ? 'owner' : 'input'
})

function onChange(next: string) {
  const rule = formControlTypeStore.activeRule
  if (!rule) return
  const props = (rule.props && typeof rule.props === 'object')
    ? { ...(rule.props as Record<string, unknown>) }
    : {}
  if (next === 'owner') {
    rule.type = 'owner'
    if (typeof props.ownerConfig !== 'string' || !props.ownerConfig.trim()) {
      props.ownerConfig = '{"source":"CREATOR"}'
    }
    rule.props = props
    return
  }
  rule.type = 'input'
  delete props.ownerConfig
  rule.props = props
}
</script>
