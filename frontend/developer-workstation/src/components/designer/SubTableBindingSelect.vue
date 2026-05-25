<script setup lang="ts">
import { inject, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { normalizeBindingId } from '@/utils/bindingDisplayHelpers'

interface DesignerSubBinding {
  id: number
  tableName: string
  tableDisplayName?: string
  tableDescription: string
  bindingType: string
}

interface SubTableBindingSelectProps {
  modelValue: number | null
  subBindings?: DesignerSubBinding[]
}

interface SubTableBindingSelectEmits {
  'update:modelValue': [val: number | null]
}

const props = defineProps<SubTableBindingSelectProps>()
const emit = defineEmits<SubTableBindingSelectEmits>()
const { t } = useI18n()

// Get subBindings from inject (provided by FormDesigner) or fall back to prop
const injectedSubBindings = inject<() => DesignerSubBinding[]>('designerSubBindings', () => [])
const allSubBindings = computed(() => props.subBindings?.length ? props.subBindings : injectedSubBindings())

// Only show SUB type bindings for sub-table widget binding selection
const subBindings = computed(() => allSubBindings.value.filter(b => b.bindingType === 'SUB'))
const normalizedModelValue = computed(() => normalizeBindingId(props.modelValue))
</script>

<template>
  <el-select
    :model-value="normalizedModelValue"
    clearable
    :placeholder="t('designer.subTableSelectPlaceholder')"
    @change="emit('update:modelValue', $event ?? null)"
  >
    <el-option
      v-for="b in subBindings"
      :key="b.id"
      :value="b.id"
      :label="b.tableDescription
        ? `${b.tableDisplayName || b.tableName}（${b.tableDescription}）`
        : (b.tableDisplayName || b.tableName)"
    />
    <template
      v-if="subBindings.length === 0"
      #empty
    >
      <span class="el-select-dropdown__empty">{{ t('designer.subTableSelectEmpty') }}</span>
    </template>
  </el-select>
</template>
