<template>
  <div class="cross-field-rule-editor">
    <div
      v-for="(rule, idx) in rules"
      :key="idx"
      class="rule-row"
    >
      <el-select
        v-model="rule.fields[0]"
        :placeholder="t('businessLogic.field1')"
        size="small"
        style="width: 130px"
      >
        <el-option
          v-for="f in fields"
          :key="f"
          :label="f"
          :value="f"
        />
      </el-select>
      <el-select
        v-model="rule.operator"
        :placeholder="t('businessLogic.selectOperator')"
        size="small"
        style="width: 130px"
      >
        <el-option
          v-for="op in operators"
          :key="op.value"
          :label="op.label"
          :value="op.value"
        />
      </el-select>
      <el-select
        v-model="rule.fields[1]"
        :placeholder="t('businessLogic.field2')"
        size="small"
        style="width: 130px"
      >
        <el-option
          v-for="f in fields"
          :key="f"
          :label="f"
          :value="f"
        />
      </el-select>
      <el-input
        v-model="rule.message"
        :placeholder="t('businessLogic.errorMessage')"
        size="small"
        style="width: 200px"
      />
      <el-select
        v-model="rule.targetField"
        :placeholder="t('businessLogic.errorTarget')"
        size="small"
        style="width: 130px"
      >
        <el-option
          v-for="f in fields"
          :key="f"
          :label="f"
          :value="f"
        />
      </el-select>
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
      {{ t('businessLogic.addCrossFieldRule') }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CrossFieldRule } from './formBusinessLogicTypes'

const { t } = useI18n()

const props = defineProps<{
  modelValue: CrossFieldRule[]
  fields: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: CrossFieldRule[]): void
}>()

const operators = computed(() => [
  { value: 'greater-than', label: t('businessLogic.opGreaterThan') },
  { value: 'less-than', label: t('businessLogic.opLessThan') },
  { value: 'equals', label: t('businessLogic.opEquals') },
  { value: 'not-equals', label: t('businessLogic.opNotEquals') },
  { value: 'date-after', label: t('businessLogic.opDateAfter') },
  { value: 'date-before', label: t('businessLogic.opDateBefore') },
])

const rules = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

function addRule() {
  emit('update:modelValue', [
    ...props.modelValue,
    { fields: ['', ''], operator: 'greater-than' as const, message: '', targetField: '' },
  ])
}

function removeRule(idx: number) {
  const updated = [...props.modelValue]
  updated.splice(idx, 1)
  emit('update:modelValue', updated)
}
</script>

<style scoped lang="scss">
.cross-field-rule-editor {
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
