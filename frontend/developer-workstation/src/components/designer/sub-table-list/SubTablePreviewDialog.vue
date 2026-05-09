<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="$t('common.preview')" width="800px" destroy-on-close>
    <el-table :data="previewFieldRows" border style="width: 100%;">
      <el-table-column prop="label" :label="$t('subTableView.displayLabel')" min-width="200" />
      <el-table-column prop="value" :label="$t('subTableView.previewValue')" min-width="200" />
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean
  previewFieldRows: Array<{ label: string; value: string }>
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>
