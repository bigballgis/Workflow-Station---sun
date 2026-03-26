<template>
  <div class="lookup-view-display" v-if="selectedData && viewFields.length > 0">
    <el-descriptions :column="2" border size="small">
      <el-descriptions-item
        v-for="field in viewFields"
        :key="field.fieldName"
        :label="field.displayLabel || field.fieldName"
        :span="field.columnWidth && field.columnWidth > 200 ? 2 : 1"
      >
        {{ selectedData[field.fieldName] ?? '-' }}
      </el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

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

// Filter to only visible fields, sorted by sortOrder
const visibleFields = ref<ViewField[]>([])

watch(() => props.viewFields, (fields) => {
  visibleFields.value = (fields || [])
    .filter(f => f.visible)
    .sort((a, b) => a.sortOrder - b.sortOrder)
}, { immediate: true })
</script>

<style lang="scss" scoped>
.lookup-view-display {
  margin-top: 8px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>
