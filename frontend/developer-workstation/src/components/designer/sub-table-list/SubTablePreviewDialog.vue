<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('common.preview')"
    width="900px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="table-scroll-wrap">
      <el-table
        :data="mockRows"
        border
        size="small"
        style="width: 100%"
      >
        <el-table-column
          v-for="col in columns"
          :key="col.key"
          :label="col.label"
          :min-width="120"
        >
          <template #default="{ row }">
            {{ col.mockValues[row._idx] ?? col.mockValues[0] ?? '' }}
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">

export interface PreviewColumn {
  key: string
  label: string
  /** Pre-rendered cell values for each mock row */
  mockValues: string[]
}

const MOCK_ROW_COUNT = 3

defineProps<{
  modelValue: boolean
  columns: PreviewColumn[]
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const mockRows = Array.from({ length: MOCK_ROW_COUNT }, (_, i) => ({ _idx: i }))
</script>

<style scoped>
.split-preview-tabs :deep(.el-tabs__content) {
  padding-top: 12px;
}
.table-scroll-wrap {
  overflow-x: auto;
}
</style>
