<script setup lang="ts">
import { inject, computed } from 'vue'

interface DesignerSubBinding {
  id: number
  tableName: string
  tableDescription: string
  bindingType: string
}

interface SubTableBindingSelectProps {
  modelValue: number | null
  subBindings?: DesignerSubBinding[]
}

interface SubTableBindingSelectEmits {
  'update:modelValue': (val: number | null) => void
}

const props = defineProps<SubTableBindingSelectProps>()
const emit = defineEmits<SubTableBindingSelectEmits>()

// Get subBindings from inject (provided by FormDesigner) or fall back to prop
const injectedSubBindings = inject<() => DesignerSubBinding[]>('designerSubBindings', () => [])
const subBindings = computed(() => props.subBindings?.length ? props.subBindings : injectedSubBindings())
</script>

<template>
  <el-select
    :model-value="modelValue"
    clearable
    placeholder="请选择 Sub Table"
    @change="emit('update:modelValue', $event ?? null)"
  >
    <el-option
      v-for="b in subBindings"
      :key="b.id"
      :value="b.id"
      :label="b.tableDescription ? `${b.tableName}（${b.tableDescription}）` : b.tableName"
    />
    <template v-if="subBindings.length === 0" #empty>
      <span class="el-select-dropdown__empty">暂无可用 Sub Table</span>
    </template>
  </el-select>
</template>
