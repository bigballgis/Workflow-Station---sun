<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('common.preview')"
    width="900px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-tabs
      v-if="splitColumns"
      v-model="activeTab"
      class="split-preview-tabs"
    >
      <el-tab-pane
        :label="$t('form.portalViews.toDoDisplay')"
        name="todo"
      >
        <div class="table-scroll-wrap">
          <el-table
            :data="mockRows"
            border
            size="small"
            style="width: 100%"
          >
            <el-table-column
              v-for="col in splitColumns.todo"
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
      </el-tab-pane>
      <el-tab-pane
        :label="$t('form.portalViews.myRequestsDisplay')"
        name="myRequest"
      >
        <div class="table-scroll-wrap">
          <el-table
            :data="mockRows"
            border
            size="small"
            style="width: 100%"
          >
            <el-table-column
              v-for="col in splitColumns.myRequest"
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
      </el-tab-pane>
    </el-tabs>
    <div
      v-else
      class="table-scroll-wrap"
    >
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
import { ref, watch } from 'vue'

export interface PreviewColumn {
  key: string
  label: string
  /** Pre-rendered cell values for each mock row */
  mockValues: string[]
}

const MOCK_ROW_COUNT = 3

const props = defineProps<{
  modelValue: boolean
  columns: PreviewColumn[]
  splitColumns?: { todo: PreviewColumn[]; myRequest: PreviewColumn[] } | null
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const activeTab = ref<'todo' | 'myRequest'>('todo')
const mockRows = Array.from({ length: MOCK_ROW_COUNT }, (_, i) => ({ _idx: i }))

watch(
  () => props.modelValue,
  open => { if (open) activeTab.value = 'todo' }
)
</script>

<style scoped>
.split-preview-tabs :deep(.el-tabs__content) {
  padding-top: 12px;
}
.table-scroll-wrap {
  overflow-x: auto;
}
</style>
