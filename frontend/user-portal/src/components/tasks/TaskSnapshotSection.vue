<template>
  <div
    v-if="isCompletedTask && completedFormData?.snapshot"
    class="section snapshot-section"
  >
    <div class="section-content">
      <SnapshotDiffRenderer
        :snapshot-values="completedFormData.snapshot.fieldValues || {}"
        :live-values="completedFormData.liveValues || {}"
        :fields="formFields"
        :tabs="formTabs"
        :fields-after-tabs="formFieldsAfterTabs"
        :sub-table-bindings="subTableBindings || []"
        :show-live-values="completedFormData.showLiveValues ?? true"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import SnapshotDiffRenderer from '@/components/SnapshotDiffRenderer.vue'
import { type FormField, type FormTab } from '@/components/FormRenderer.vue'
import type { SnapshotSubTableBindingSource } from '@/components/snapshotDiffSubTables'

defineProps<{
  isCompletedTask: boolean
  completedFormData: { snapshot?: { fieldValues: Record<string, any> }; liveValues?: Record<string, any>; showLiveValues?: boolean } | null
  formFields: FormField[]
  formTabs: FormTab[]
  formFieldsAfterTabs?: FormField[]
  subTableBindings?: SnapshotSubTableBindingSource[]
}>()
</script>

<style lang="scss" scoped>
.section {
  background: white;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e4e7ed);

  .section-content {
    padding: 20px;
  }
}
</style>
