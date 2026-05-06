<template>
  <div v-if="isCompletedTask && completedFormData?.snapshot" class="section snapshot-section">
    <div class="section-header">
      <el-icon><Document /></el-icon>
      <span>{{ $t('task.completedSnapshot') }}</span>
    </div>
    <div class="section-content">
      <SnapshotDiffRenderer
        :snapshot-values="completedFormData.snapshot.fieldValues || {}"
        :live-values="completedFormData.liveValues || {}"
        :fields="fieldsForSnapshot"
        :show-live-values="completedFormData.showLiveValues ?? true"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import SnapshotDiffRenderer from '@/components/SnapshotDiffRenderer.vue'
import { type FormField, type FormTab } from '@/components/FormRenderer.vue'

const props = defineProps<{
  isCompletedTask: boolean
  completedFormData: { snapshot?: { fieldValues: Record<string, any> }; liveValues?: Record<string, any>; showLiveValues?: boolean } | null
  formFields: FormField[]
  formTabs: FormTab[]
}>()

const fieldsForSnapshot = computed(() =>
  props.formFields.length > 0 ? props.formFields : (props.formTabs.flatMap(tab => tab.fields) || [])
)
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
