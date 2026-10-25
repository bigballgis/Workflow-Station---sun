<template>
  <LookupField :model-value="modelValue" :table-id="SYSTEM_USER_RELATION_TABLE_ID"
    :search-fields="['username', 'display_name', 'full_name']" display-field="display_name"
    :display-fields="['username', 'display_name', 'full_name']" selected-display-field="display_name"
    :readonly="readonly" :prefetch-limit="30" remote-filter
    @update:model-value="update" />
</template>
<script setup lang="ts">
import LookupField from './lookup/LookupField.vue'
// Platform virtual User relation identity, shared with SystemUserRelationTable.ID.
const SYSTEM_USER_RELATION_TABLE_ID = -1_000_000_001
defineProps<{ modelValue: unknown; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>()
function update(value: unknown) {
  if (value == null) { emit('update:modelValue', null); return }
  if (typeof value !== 'object' || !('id' in value) || typeof value.id !== 'string') throw new Error('Invalid assignment selection')
  emit('update:modelValue', value.id)
}
</script>
