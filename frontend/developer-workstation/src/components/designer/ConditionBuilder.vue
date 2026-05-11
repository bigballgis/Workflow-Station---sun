<template>
  <div class="condition-builder">
    <div
      v-for="(cond, idx) in conditions"
      :key="idx"
      class="condition-row"
    >
      <el-select
        v-model="cond.field"
        :placeholder="t('businessLogic.selectField')"
        size="small"
        style="width: 140px"
      >
        <el-option
          v-for="f in fields"
          :key="f"
          :label="f"
          :value="f"
        />
      </el-select>
      <el-select
        v-model="cond.operator"
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
      <el-input
        v-if="!noValueOperators.includes(cond.operator)"
        v-model="cond.value"
        :placeholder="t('businessLogic.enterValue')"
        size="small"
        style="width: 120px"
      />
      <el-select
        v-if="idx < conditions.length - 1"
        v-model="cond.logic"
        size="small"
        style="width: 80px"
      >
        <el-option
          label="AND"
          value="AND"
        />
        <el-option
          label="OR"
          value="OR"
        />
      </el-select>
      <el-button
        link
        type="danger"
        size="small"
        @click="removeCondition(idx)"
      >
        {{ t('common.delete') }}
      </el-button>
    </div>
    <el-button
      size="small"
      @click="addCondition"
    >
      {{ t('businessLogic.addCondition') }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ConditionExpression } from './formBusinessLogicTypes'

const { t } = useI18n()

const props = defineProps<{
  modelValue: ConditionExpression[]
  fields: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ConditionExpression[]): void
}>()

const noValueOperators = ['is-empty', 'is-not-empty']

const operators = computed(() => [
  { value: 'equals', label: t('businessLogic.opEquals') },
  { value: 'not-equals', label: t('businessLogic.opNotEquals') },
  { value: 'contains', label: t('businessLogic.opContains') },
  { value: 'greater-than', label: t('businessLogic.opGreaterThan') },
  { value: 'less-than', label: t('businessLogic.opLessThan') },
  { value: 'is-empty', label: t('businessLogic.opIsEmpty') },
  { value: 'is-not-empty', label: t('businessLogic.opIsNotEmpty') },
])

const conditions = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

function addCondition() {
  emit('update:modelValue', [
    ...props.modelValue,
    { field: '', operator: 'equals' as const, value: '', logic: 'AND' as const },
  ])
}

function removeCondition(idx: number) {
  const updated = [...props.modelValue]
  updated.splice(idx, 1)
  emit('update:modelValue', updated)
}
</script>

<style scoped lang="scss">
.condition-builder {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.condition-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
