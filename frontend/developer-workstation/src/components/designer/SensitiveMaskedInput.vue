<template>
  <el-input
    :model-value="displayValue"
    :type="inputType"
    :placeholder="placeholder"
    :maxlength="maxlength"
    :show-word-limit="showWordLimit"
    :clearable="clearable && !showMasked"
    :disabled="disabled"
    :readonly="effectiveReadonly"
    :rows="rows"
    @update:model-value="onInput"
    @focus="onFocus"
    @blur="onBlur"
  />
</template>

<script setup lang="ts">
/**
 * Drop-in form-create Input wrapper: display-only sensitive mask.
 * Raw modelValue is never replaced with the masked string.
 */
import { computed, ref } from 'vue'
import {
  applySensitiveMask,
  normalizeSensitiveMaskConfig,
  shouldShowMaskedDisplay,
} from '@/utils/sensitiveMask'

const props = defineProps<{
  modelValue?: string | number | null
  type?: string
  placeholder?: string
  maxlength?: number | string
  showWordLimit?: boolean
  clearable?: boolean
  disabled?: boolean
  readonly?: boolean
  rows?: number
  sensitiveMask?: unknown
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | number | null | undefined]
  change: [value: string | number | null | undefined]
  blur: [event: FocusEvent]
  focus: [event: FocusEvent]
}>()

const focused = ref(false)

const inputType = computed(() => props.type || 'text')

const maskConfig = computed(() => normalizeSensitiveMaskConfig(props.sensitiveMask))

const showMasked = computed(() =>
  shouldShowMaskedDisplay(
    maskConfig.value,
    {
      isReadonly: props.disabled === true || props.readonly === true,
      isFocused: focused.value,
    },
    inputType.value,
  ),
)

const rawString = computed(() => {
  if (props.modelValue == null) return ''
  return String(props.modelValue)
})

const displayValue = computed(() => {
  if (!showMasked.value || rawString.value === '') return props.modelValue ?? ''
  return applySensitiveMask(rawString.value, maskConfig.value!)
})

const effectiveReadonly = computed(
  () => props.readonly === true || showMasked.value,
)

function onInput(value: string) {
  if (showMasked.value) return
  emit('update:modelValue', value)
  emit('change', value)
}

function onFocus(event: FocusEvent) {
  focused.value = true
  emit('focus', event)
}

function onBlur(event: FocusEvent) {
  focused.value = false
  emit('blur', event)
}
</script>
