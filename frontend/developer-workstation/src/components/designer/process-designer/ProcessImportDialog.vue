<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('process.importBpmnXml')"
    width="600px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-input
      :model-value="importXml"
      type="textarea"
      :rows="15"
      :placeholder="$t('process.pasteBpmnXml')"
      @update:model-value="$emit('update:importXml', $event)"
    />
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ $t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="$emit('import')"
      >
        {{ $t('process.import') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean
  importXml: string
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
  'update:importXml': [value: string]
  import: []
}>()
</script>
