<template>
  <div
    v-if="isCompletedTask && completedFormData?.snapshot"
    class="section snapshot-section"
  >
    <div class="section-header">
      <el-icon><Document /></el-icon>
      <span>{{ $t('task.completedSnapshot') }}</span>
    </div>
    <div class="section-content">
      <SnapshotDiffRenderer
        :snapshot-values="completedFormData.snapshot.fieldValues || {}"
        :live-values="completedFormData.liveValues || {}"
        :fields="formFields"
        :tabs="formTabs"
        :fields-after-tabs="formFieldsAfterTabs"
        :show-live-values="completedFormData.showLiveValues ?? true"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'
import SnapshotDiffRenderer from '@/components/SnapshotDiffRenderer.vue'
import { type FormField, type FormTab } from '@/components/FormRenderer.vue'

defineProps<{
  isCompletedTask: boolean
  completedFormData: { snapshot?: { fieldValues: Record<string, any> }; liveValues?: Record<string, any>; showLiveValues?: boolean } | null
  formFields: FormField[]
  formTabs: FormTab[]
  formFieldsAfterTabs?: FormField[]
}>()
</script>

<style lang="scss" scoped>
.section {
  background: white;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e4e7ed);

  .section-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 16px 20px;
    border-bottom: 1px solid var(--border-color, #e4e7ed);
    font-weight: 500;
    font-size: 16px;
    color: var(--text-primary);
  }

  .section-content {
    padding: 20px;
  }
}
</style>
