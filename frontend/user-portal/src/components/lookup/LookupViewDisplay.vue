<template>
  <div class="lookup-view-display" v-if="selectedData && displayFields.length > 0">
    <el-descriptions :column="1" border size="small" direction="horizontal">
      <el-descriptions-item
        v-for="field in displayFields"
        :key="field.fieldName"
        :label="field.displayLabel || field.fieldName"
        label-class-name="lookup-view-label"
        class-name="lookup-view-value"
      >
        {{ selectedData[field.fieldName] ?? '-' }}
      </el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface ViewField {
  fieldName: string
  displayLabel: string
  columnWidth?: number
  sortOrder: number
  visible: boolean
}

const props = defineProps<{
  selectedData?: Record<string, any> | null
  viewFields: ViewField[]
}>()

const displayFields = computed(() => {
  const configured = (props.viewFields || [])
    .filter(f => f.visible !== false)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

  if (configured.length > 0) return configured

  // Fallback: show all fields from selectedData (matches developer-workstation preview behavior)
  if (!props.selectedData) return []
  return Object.keys(props.selectedData)
    .map((k, i) => ({ fieldName: k, displayLabel: k, sortOrder: i, visible: true } as ViewField))
})
</script>

<style lang="scss" scoped>
.lookup-view-display {
  margin-top: 8px;

  :deep(.lookup-view-label) {
    width: 40%;
    font-weight: 500;
    color: #606266;
    background: #fafafa;
  }

  :deep(.lookup-view-value) {
    color: #303133;
  }
}
</style>
