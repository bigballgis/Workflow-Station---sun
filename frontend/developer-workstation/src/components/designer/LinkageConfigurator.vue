<template>
  <div class="linkage-configurator">
    <div v-for="(rule, idx) in rules" :key="idx" class="linkage-row">
      <el-form size="small" label-width="100px">
        <el-form-item :label="t('businessLogic.sourceField')">
          <el-select v-model="rule.sourceField" :placeholder="t('businessLogic.selectField')" style="width: 160px">
            <el-option v-for="f in fields" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('businessLogic.targetField')">
          <el-select v-model="rule.targetField" :placeholder="t('businessLogic.selectField')" style="width: 160px">
            <el-option v-for="f in fields" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('businessLogic.linkageType')">
          <el-select v-model="rule.linkageType" style="width: 180px">
            <el-option value="option-filtering" :label="t('businessLogic.optionFiltering')" />
            <el-option value="value-auto-fill" :label="t('businessLogic.valueAutoFill')" />
            <el-option value="field-state-change" :label="t('businessLogic.fieldStateChange')" />
          </el-select>
        </el-form-item>

        <!-- option-filtering config -->
        <template v-if="rule.linkageType === 'option-filtering'">
          <el-form-item :label="t('businessLogic.filterField')">
            <el-input v-model="filterConfig(rule).filterField" :placeholder="t('businessLogic.filterFieldPlaceholder')" style="width: 160px" />
          </el-form-item>
          <el-form-item :label="t('businessLogic.filterOperator')">
            <el-select v-model="filterConfig(rule).filterOperator" style="width: 120px">
              <el-option value="equals" label="equals" />
              <el-option value="contains" label="contains" />
              <el-option value="in" label="in" />
            </el-select>
          </el-form-item>
        </template>

        <!-- value-auto-fill config -->
        <template v-if="rule.linkageType === 'value-auto-fill'">
          <el-form-item :label="t('businessLogic.valueMapping')">
            <el-input
              v-model="valueMappingStr[idx]"
              type="textarea"
              :rows="2"
              :placeholder="t('businessLogic.valueMappingPlaceholder')"
              @blur="parseValueMapping(idx)"
            />
          </el-form-item>
        </template>

        <!-- field-state-change config -->
        <template v-if="rule.linkageType === 'field-state-change'">
          <el-form-item :label="t('businessLogic.stateDisabled')">
            <el-switch v-model="stateConfig(rule).disabled" />
          </el-form-item>
          <el-form-item :label="t('businessLogic.stateRequired')">
            <el-switch v-model="stateConfig(rule).required" />
          </el-form-item>
        </template>
      </el-form>
      <el-button link type="danger" size="small" @click="removeRule(idx)">{{ t('common.delete') }}</el-button>
    </div>
    <el-button size="small" @click="addRule">{{ t('businessLogic.addLinkage') }}</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LinkageRule } from './formBusinessLogicTypes'

const { t } = useI18n()

const props = defineProps<{
  modelValue: LinkageRule[]
  fields: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: LinkageRule[]): void
}>()

const rules = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

// Track JSON string for value mapping editing
const valueMappingStr = reactive<Record<number, string>>({})

function filterConfig(rule: LinkageRule) {
  if (!rule.filterConfig) {
    rule.filterConfig = { filterField: '', filterOperator: 'equals', filterSource: '$source' }
  }
  return rule.filterConfig
}

function stateConfig(rule: LinkageRule) {
  if (!rule.stateConfig) {
    rule.stateConfig = { condition: { field: '', operator: 'equals' }, disabled: false, required: false }
  }
  return rule.stateConfig
}

function addRule() {
  emit('update:modelValue', [
    ...props.modelValue,
    { sourceField: '', targetField: '', linkageType: 'option-filtering' as const },
  ])
}

function removeRule(idx: number) {
  const updated = [...props.modelValue]
  updated.splice(idx, 1)
  emit('update:modelValue', updated)
}

function parseValueMapping(idx: number) {
  try {
    const parsed = JSON.parse(valueMappingStr[idx] || '{}')
    const updated = [...props.modelValue]
    updated[idx] = { ...updated[idx], valueMapping: parsed }
    emit('update:modelValue', updated)
  } catch {
    // Invalid JSON, ignore
  }
}
</script>

<style scoped lang="scss">
.linkage-configurator {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.linkage-row {
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
</style>
