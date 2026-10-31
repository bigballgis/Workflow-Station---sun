<script setup lang="ts">
import { computed } from 'vue'
import { ElDatePicker, ElTimePicker } from 'element-plus'

const props = defineProps<{
  kind: 'date' | 'datetime' | 'daterange' | 'datetimerange' | 'time' | 'timerange'
  modelValue: string | number | Date | string[] | number[] | Date[] | null | undefined
  disabled?: boolean
  placeholder?: string
  startPlaceholder?: string
  endPlaceholder?: string
  format?: string
  valueFormat?: string
  popperClass?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: unknown]; change: [value: unknown] }>()
const isTime = computed(() => props.kind === 'time' || props.kind === 'timerange')
const valueFormat = computed(() => props.valueFormat ?? (isTime.value ? 'HH:mm:ss'
  : props.kind === 'datetime' || props.kind === 'datetimerange' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'))
</script>

<template>
  <component
    :is="isTime ? ElTimePicker : ElDatePicker"
    :model-value="modelValue"
    :type="isTime ? undefined : kind"
    :is-range="kind === 'timerange'"
    :disabled="disabled"
    :placeholder="placeholder"
    :start-placeholder="startPlaceholder"
    :end-placeholder="endPlaceholder"
    :format="format"
    :value-format="valueFormat"
    :popper-class="popperClass"
    :teleported="true"
    style="width: 100%"
    @update:model-value="(value: unknown) => emit('update:modelValue', value)"
    @change="(value: unknown) => emit('change', value)"
  />
</template>
