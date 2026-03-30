<template>
  <div class="sub-table-validation-editor">
    <el-form size="small" label-width="100px">
      <el-form-item :label="t('businessLogic.minRows')">
        <el-input-number v-model="config.minRows" :min="0" :controls="false" style="width: 120px" @change="emitUpdate" />
      </el-form-item>
      <el-form-item :label="t('businessLogic.maxRows')">
        <el-input-number v-model="config.maxRows" :min="0" :controls="false" style="width: 120px" @change="emitUpdate" />
      </el-form-item>
    </el-form>

    <el-divider content-position="left">{{ t('businessLogic.columnValidation') }}</el-divider>
    <div v-for="col in columns" :key="col" class="column-section">
      <div class="column-header">
        <el-tag size="small">{{ col }}</el-tag>
        <el-button size="small" link @click="addColumnRule(col)">{{ t('businessLogic.addRule') }}</el-button>
      </div>
      <ValidationRuleList
        v-if="columnRules[col] && columnRules[col].length > 0"
        :model-value="columnRules[col]"
        @update:model-value="updateColumnRules(col, $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SubTableValidationConfig, ValidationRule } from './formBusinessLogicTypes'
import ValidationRuleList from './ValidationRuleList.vue'

const { t } = useI18n()

const props = defineProps<{
  modelValue: SubTableValidationConfig
  columns: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SubTableValidationConfig): void
}>()

const config = reactive<SubTableValidationConfig>({
  minRows: props.modelValue.minRows ?? 0,
  maxRows: props.modelValue.maxRows ?? 0,
  columnRules: { ...props.modelValue.columnRules },
})

const columnRules = computed(() => config.columnRules ?? {})

function emitUpdate() {
  emit('update:modelValue', { ...config })
}

function addColumnRule(col: string) {
  if (!config.columnRules) config.columnRules = {}
  if (!config.columnRules[col]) config.columnRules[col] = []
  config.columnRules[col].push({ type: 'required', message: '' })
  emitUpdate()
}

function updateColumnRules(col: string, rules: ValidationRule[]) {
  if (!config.columnRules) config.columnRules = {}
  config.columnRules[col] = rules
  emitUpdate()
}
</script>

<style scoped lang="scss">
.sub-table-validation-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.column-section {
  margin-bottom: 8px;
}
.column-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
</style>
