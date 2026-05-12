<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('common.preview')"
    width="800px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-tabs
      v-if="splitPreviewRows"
      v-model="activeTab"
      class="split-preview-tabs"
    >
      <el-tab-pane
        :label="$t('form.portalViews.toDoDisplay')"
        name="todo"
      >
        <el-table
          :data="splitPreviewRows.todo"
          border
          style="width: 100%;"
        >
          <el-table-column
            prop="label"
            :label="$t('subTableView.displayLabel')"
            min-width="200"
          />
          <el-table-column
            prop="value"
            :label="$t('subTableView.previewValue')"
            min-width="200"
          />
        </el-table>
      </el-tab-pane>
      <el-tab-pane
        :label="$t('form.portalViews.myRequestsDisplay')"
        name="myRequest"
      >
        <el-table
          :data="splitPreviewRows.myRequest"
          border
          style="width: 100%;"
        >
          <el-table-column
            prop="label"
            :label="$t('subTableView.displayLabel')"
            min-width="200"
          />
          <el-table-column
            prop="value"
            :label="$t('subTableView.previewValue')"
            min-width="200"
          />
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-table
      v-else
      :data="previewFieldRows"
      border
      style="width: 100%;"
    >
      <el-table-column
        prop="label"
        :label="$t('subTableView.displayLabel')"
        min-width="200"
      />
      <el-table-column
        prop="value"
        :label="$t('subTableView.previewValue')"
        min-width="200"
      />
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

export type PreviewFieldRow = { label: string; value: string }

const props = defineProps<{
  modelValue: boolean
  previewFieldRows: PreviewFieldRow[]
  splitPreviewRows?: { todo: PreviewFieldRow[]; myRequest: PreviewFieldRow[] } | null
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const activeTab = ref<'todo' | 'myRequest'>('todo')

watch(
  () => props.modelValue,
  open => {
    if (open) activeTab.value = 'todo'
  }
)
</script>

<style scoped>
.split-preview-tabs :deep(.el-tabs__content) {
  padding-top: 12px;
}
</style>
