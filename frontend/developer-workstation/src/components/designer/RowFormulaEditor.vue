<template>
  <div class="row-formula-editor">
    <el-divider content-position="left">
      {{ t('businessLogic.rowFormulas') }}
    </el-divider>
    <div
      v-for="(formula, idx) in formulas"
      :key="'rf-' + idx"
      class="formula-row"
    >
      <el-select
        v-model="formula.targetColumn"
        :placeholder="t('businessLogic.targetColumn')"
        size="small"
        style="width: 130px"
      >
        <el-option
          v-for="c in columns"
          :key="c"
          :label="c"
          :value="c"
        />
      </el-select>
      <el-input
        v-model="formula.expression"
        :placeholder="t('businessLogic.expressionPlaceholder')"
        size="small"
        style="width: 200px"
        @blur="detectDeps(formula)"
      />
      <el-tag
        v-for="dep in formula.dependsOn"
        :key="dep"
        size="small"
        type="info"
      >
        {{ dep }}
      </el-tag>
      <el-button
        link
        type="danger"
        size="small"
        @click="removeFormula(idx)"
      >
        {{ t('common.delete') }}
      </el-button>
    </div>
    <el-button
      size="small"
      @click="addFormula"
    >
      {{ t('businessLogic.addRowFormula') }}
    </el-button>

    <el-divider content-position="left">
      {{ t('businessLogic.summaryRules') }}
    </el-divider>
    <div
      v-for="(sr, idx) in summaries"
      :key="'sr-' + idx"
      class="summary-row"
    >
      <el-select
        v-model="sr.sourceColumn"
        :placeholder="t('businessLogic.sourceColumn')"
        size="small"
        style="width: 130px"
      >
        <el-option
          v-for="c in columns"
          :key="c"
          :label="c"
          :value="c"
        />
      </el-select>
      <el-select
        v-model="sr.targetField"
        :placeholder="t('businessLogic.mainFormField')"
        size="small"
        style="width: 140px"
      >
        <el-option
          v-for="f in mainFormFields"
          :key="f"
          :label="f"
          :value="f"
        />
      </el-select>
      <el-select
        v-model="sr.aggregation"
        :placeholder="t('businessLogic.aggregation')"
        size="small"
        style="width: 100px"
      >
        <el-option
          v-for="agg in aggregations"
          :key="agg"
          :label="agg"
          :value="agg"
        />
      </el-select>
      <el-button
        link
        type="danger"
        size="small"
        @click="removeSummary(idx)"
      >
        {{ t('common.delete') }}
      </el-button>
    </div>
    <el-button
      size="small"
      @click="addSummary"
    >
      {{ t('businessLogic.addSummaryRule') }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RowFormulaRule, SummaryRule } from './formBusinessLogicTypes'

const { t } = useI18n()

const aggregations = ['SUM', 'AVG', 'COUNT', 'MIN', 'MAX'] as const

const props = defineProps<{
  rowFormulas: RowFormulaRule[]
  summaryRules: SummaryRule[]
  columns: string[]
  mainFormFields: string[]
}>()

const emit = defineEmits<{
  (e: 'update:rowFormulas', value: RowFormulaRule[]): void
  (e: 'update:summaryRules', value: SummaryRule[]): void
}>()

const formulas = computed({
  get: () => props.rowFormulas,
  set: (val) => emit('update:rowFormulas', val),
})

const summaries = computed({
  get: () => props.summaryRules,
  set: (val) => emit('update:summaryRules', val),
})

function addFormula() {
  emit('update:rowFormulas', [
    ...props.rowFormulas,
    { targetColumn: '', expression: '', dependsOn: [] },
  ])
}

function removeFormula(idx: number) {
  const updated = [...props.rowFormulas]
  updated.splice(idx, 1)
  emit('update:rowFormulas', updated)
}

function detectDeps(formula: RowFormulaRule) {
  formula.dependsOn = props.columns.filter((c) => formula.expression.includes(c))
}

function addSummary() {
  emit('update:summaryRules', [
    ...props.summaryRules,
    { sourceBindingId: 0, sourceColumn: '', targetField: '', aggregation: 'SUM' as const },
  ])
}

function removeSummary(idx: number) {
  const updated = [...props.summaryRules]
  updated.splice(idx, 1)
  emit('update:summaryRules', updated)
}
</script>

<style scoped lang="scss">
.row-formula-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.formula-row,
.summary-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
