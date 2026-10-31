<template>
  <div>
    <LookupField :model-value="modelValue" v-bind="config" :placeholder="placeholder" :readonly="disabled || readonly"
      @update:model-value="value => emit('update:modelValue', value)" @view-fields-loaded="value => viewFields = value.map(field => ({ ...field, displayLabel: field.displayLabel ?? field.fieldName }))" />
    <LookupViewDisplay v-if="config.showBackfillView !== false && selected" :selected-data="selected" :view-fields="viewFields" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import LookupField from './LookupField.vue'
import LookupViewDisplay, { type ViewField } from './LookupViewDisplay.vue'
const props = defineProps<{ modelValue?: unknown; lookupConfig?: string | Record<string, unknown>; placeholder?: string; disabled?: boolean; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()
const viewFields = ref<ViewField[]>([])
const selected = computed(() => props.modelValue && typeof props.modelValue === 'object' && !Array.isArray(props.modelValue)
  ? props.modelValue as Record<string, unknown> : null)
const config = computed(() => {
  const value = typeof props.lookupConfig === 'string' ? JSON.parse(props.lookupConfig) : props.lookupConfig
  if (!value || typeof value !== 'object' || !value.tableId) throw new Error('subTable.batchLookupConfig')
  return { ...value, tableId: Number(value.tableId), lookupConfig: props.lookupConfig }
})
</script>
