<template>
  <div class="validation-rule-list">
    <div
      v-for="(rule, idx) in rules"
      :key="idx"
      class="rule-row"
    >
      <el-select
        v-model="rule.type"
        :placeholder="t('businessLogic.ruleType')"
        size="small"
        style="width: 120px"
        @change="onTypeChange(idx)"
      >
        <el-option
          v-for="rt in ruleTypes"
          :key="rt.value"
          :label="rt.label"
          :value="rt.value"
        />
      </el-select>
      <el-input
        v-if="rule.type === 'pattern'"
        v-model="rule.pattern"
        :placeholder="t('businessLogic.regexPattern')"
        size="small"
        style="width: 160px"
      />
      <template v-if="rule.type === 'number'">
        <el-input-number
          v-model="rule.min"
          :placeholder="t('businessLogic.minValue')"
          size="small"
          style="width: 100px"
          :controls="false"
        />
        <span>-</span>
        <el-input-number
          v-model="rule.max"
          :placeholder="t('businessLogic.maxValue')"
          size="small"
          style="width: 100px"
          :controls="false"
        />
      </template>
      <el-input
        v-model="rule.message"
        :placeholder="t('businessLogic.errorMessage')"
        size="small"
        style="width: 180px"
      />
      <el-button
        link
        type="danger"
        size="small"
        @click="removeRule(idx)"
      >
        {{ t('common.delete') }}
      </el-button>
    </div>
    <el-button
      size="small"
      @click="addRule"
    >
      {{ t('businessLogic.addRule') }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ValidationRule } from './formBusinessLogicTypes'

const { t } = useI18n()

const props = defineProps<{
  modelValue: ValidationRule[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ValidationRule[]): void
}>()

const ruleTypes = computed(() => [
  { value: 'required', label: t('businessLogic.ruleRequired') },
  { value: 'pattern', label: t('businessLogic.rulePattern') },
  { value: 'number', label: t('businessLogic.ruleNumber') },
  { value: 'email', label: t('businessLogic.ruleEmail') },
  { value: 'phone', label: t('businessLogic.rulePhone') },
  { value: 'custom', label: t('businessLogic.ruleCustom') },
])

const rules = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

function addRule() {
  emit('update:modelValue', [
    ...props.modelValue,
    { type: 'required' as const, message: '' },
  ])
}

function removeRule(idx: number) {
  const updated = [...props.modelValue]
  updated.splice(idx, 1)
  emit('update:modelValue', updated)
}

function onTypeChange(idx: number) {
  const updated = [...props.modelValue]
  const rule = { ...updated[idx] }
  // Reset type-specific fields
  delete rule.pattern
  delete rule.min
  delete rule.max
  delete rule.minLength
  delete rule.maxLength
  updated[idx] = rule
  emit('update:modelValue', updated)
}
</script>

<style scoped lang="scss">
.validation-rule-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rule-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
