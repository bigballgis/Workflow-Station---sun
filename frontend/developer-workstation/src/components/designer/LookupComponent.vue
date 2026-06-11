<template>
  <div class="lookup-component">
    <LookupPreview
      v-model="lookupValue"
      :label="''"
      :placeholder="placeholder || previewConfig.placeholder"
      :search-fields="previewConfig.searchFields"
      :display-fields="previewConfig.displayFields"
      :selected-display-field="previewConfig.selectedDisplayField"
      :filter-conditions="previewConfig.filterConditions"
      :view-fields="previewConfig.viewFields"
      :field-defs="previewConfig.fieldDefs"
      :show-backfill-view="previewConfig.showBackfillView"
      :readonly="isReadonly"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import LookupPreview from './LookupPreview.vue'
import { lookupStore } from './lookupStore'
import { isFormCreateRuleReadonly } from '@/utils/formCreateRuleUtils'

const props = defineProps<{
  modelValue?: any
  placeholder?: string
  lookupConfig?: string
  disabled?: boolean
  formCreateInject?: { rule?: Record<string, unknown> }
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
}>()

const lookupValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const isReadonly = computed(
  () => props.disabled === true || isFormCreateRuleReadonly(props.formCreateInject?.rule),
)

function parseLookupConfig(raw?: string): Record<string, any> {
  if (!raw) return {}
  try {
    return JSON.parse(raw)
  } catch {
    return {}
  }
}

const previewConfig = computed(() => {
  const config = parseLookupConfig(props.lookupConfig)
  const binding = lookupStore.relationBindings.find(item => item.bindingId === config.bindingId)
  const table = lookupStore.tables.find(item => item.id === (config.tableId ?? binding?.tableId))
  const fields = ((table as any)?.fieldDefinitions || (table as any)?.fields || lookupStore.rtFieldCache[config.tableId ?? binding?.tableId] || [])
    .map((field: any) => ({
      fieldName: field.fieldName,
      dataType: field.dataType,
      displayName: field.displayName,
    }))
  const displayFields = config.displayFields || []

  return {
    placeholder: 'Click to search',
    searchFields: config.searchFields || [],
    displayFields,
    selectedDisplayField: config.selectedDisplayField || config.displayField || '',
    filterConditions: Array.isArray(config.filterConditions) ? config.filterConditions : [],
    viewFields: config.showBackfillView === false
      ? []
      : displayFields.map((fieldName: string, index: number) => ({
        fieldName,
        displayLabel: fields.find((field: any) => field.fieldName === fieldName)?.displayName || fieldName,
        sortOrder: index,
        visible: true,
      })),
    fieldDefs: fields,
    showBackfillView: config.showBackfillView !== false,
  }
})
</script>

<style lang="scss" scoped>
.lookup-component {
  width: 100%;

  :deep(.lookup-label-text) {
    display: none;
  }
}
</style>
