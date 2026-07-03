<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('action.testActionTitle')"
    width="600px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      label-width="auto"
      label-position="left"
    >
      <el-form-item :label="$t('action.testData')">
        <el-input
          :model-value="testData"
          type="textarea"
          :rows="5"
          :placeholder="$t('action.testDataPlaceholder')"
          @update:model-value="$emit('update:testData', $event)"
        />
      </el-form-item>
    </el-form>
    <el-divider>{{ $t('action.executionResult') }}</el-divider>
    <pre class="test-result">{{ testResult }}</pre>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ $t('action.close') }}
      </el-button>
      <el-button
        type="primary"
        :loading="testing"
        @click="$emit('executeTest')"
      >
        {{ $t('action.executeTest') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean
  testActionType: string
  testInputMapping: Array<{ paramName: string; paramLabel: string; paramType: string; required: boolean }>
  testRawJsonMode: boolean
  testStructuredData: Record<string, any>
  testData: string
  testResult: string
  testing: boolean
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
  'update:testRawJsonMode': [value: boolean]
  'update:testData': [value: string]
  executeTest: []
}>()
</script>

<style lang="scss" scoped>
.test-result {
  background: #f5f7fa;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  padding: 12px;
  min-height: 60px;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  font-size: 13px;
  font-family: monospace;
}
</style>
