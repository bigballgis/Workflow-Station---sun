<template>
  <div class="formula-editor">
    <div v-for="(rule, idx) in formulas" :key="idx" class="formula-row">
      <el-form-item :label="t('businessLogic.targetField')" size="small">
        <el-select v-model="rule.targetField" :placeholder="t('businessLogic.selectField')" style="width: 140px">
          <el-option v-for="f in fields" :key="f" :label="f" :value="f" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('businessLogic.expression')" size="small">
        <el-input
          v-model="rule.expression"
          :placeholder="t('businessLogic.expressionPlaceholder')"
          style="width: 240px"
          @blur="validateExpression(rule)"
        />
      </el-form-item>
      <div class="field-refs">
        <el-tag
          v-for="f in fields"
          :key="f"
          size="small"
          type="info"
          class="field-tag"
          @click="insertField(idx, f)"
        >{{ f }}</el-tag>
      </div>
      <el-button link type="danger" size="small" @click="removeFormula(idx)">{{ t('common.delete') }}</el-button>
    </div>
    <el-button size="small" @click="addFormula">{{ t('businessLogic.addFormula') }}</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormulaRule } from './formBusinessLogicTypes'

const { t } = useI18n()

const DANGEROUS_KEYWORDS = ['eval', 'Function', 'import', 'require', 'window', 'document']

const props = defineProps<{
  modelValue: FormulaRule[]
  fields: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FormulaRule[]): void
}>()

const formulas = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

function addFormula() {
  emit('update:modelValue', [
    ...props.modelValue,
    { targetField: '', expression: '', dependsOn: [] },
  ])
}

function removeFormula(idx: number) {
  const updated = [...props.modelValue]
  updated.splice(idx, 1)
  emit('update:modelValue', updated)
}

function insertField(idx: number, field: string) {
  const updated = [...props.modelValue]
  updated[idx] = { ...updated[idx], expression: updated[idx].expression + field }
  // Auto-detect dependencies
  updated[idx].dependsOn = detectDependencies(updated[idx].expression)
  emit('update:modelValue', updated)
}

function detectDependencies(expression: string): string[] {
  return props.fields.filter((f) => expression.includes(f))
}

function validateExpression(rule: FormulaRule) {
  const hasDangerous = DANGEROUS_KEYWORDS.some((kw) => rule.expression.includes(kw))
  if (hasDangerous) {
    ElMessage.warning(t('businessLogic.dangerousExpression'))
  }
  rule.dependsOn = detectDependencies(rule.expression)
}
</script>

<style scoped lang="scss">
.formula-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.formula-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.field-refs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.field-tag {
  cursor: pointer;
}
</style>
