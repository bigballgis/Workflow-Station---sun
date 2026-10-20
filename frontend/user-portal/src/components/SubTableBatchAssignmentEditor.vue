<template>
  <BatchAssignmentEditor ref="editor" v-bind="$props" :create-directory="useBatchAssignmentDirectory" :field-write-modes="capability.rowDesign?.fieldWriteModes" />
</template>
<script setup lang="ts">
import { ref } from 'vue'
import BatchAssignmentEditor from '@platform-shared/batch/BatchAssignmentEditor.vue'
import type { SubTableBatchCapability } from '@/utils/subTableBatchCapability'
import { useBatchAssignmentDirectory } from '@/composables/subTableField/useBatchAssignmentDirectory'

defineProps<{
  rule?: Record<string, any>
  options?: Record<string, any>
  disabled?: boolean
  rows: Array<Record<string, unknown>>
  capability: SubTableBatchCapability
  rowIdentity: (row: Record<string, unknown>) => string | null
  rowScopeKey: (row: Record<string, unknown>) => Record<string, unknown> | null
}>()
const editor = ref<InstanceType<typeof BatchAssignmentEditor>>()
function collect(rows: Array<Record<string, unknown>>) {
  if (!editor.value) throw new Error('subTable.batchAssignmentRequired')
  return editor.value.collect(rows)
}
function hasChanges(): boolean { return editor.value?.hasChanges() === true }
defineExpose({ collect, hasChanges })
</script>
