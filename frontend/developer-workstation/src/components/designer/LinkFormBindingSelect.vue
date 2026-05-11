<script setup lang="ts">
import { inject, computed } from 'vue'
import { useI18n } from 'vue-i18n'

interface LinkFormComponentInfo {
  id: number
  componentName: string
  linkedFormName?: string
}

interface LinkFormBindingSelectProps {
  modelValue: number | null
  components?: LinkFormComponentInfo[]
}

interface LinkFormBindingSelectEmits {
  'update:modelValue': [val: number | null]
}

const props = defineProps<LinkFormBindingSelectProps>()
const emit = defineEmits<LinkFormBindingSelectEmits>()
const { t } = useI18n()

// Get linkFormComponents from inject (provided by FormDesigner) or fall back to prop
const injectedComponents = inject<() => LinkFormComponentInfo[]>('linkFormComponents', () => [])
const allComponents = computed(() => props.components?.length ? props.components : injectedComponents())
</script>

<template>
  <el-select
    :model-value="modelValue"
    clearable
    :placeholder="t('designer.linkFormSelectPlaceholder')"
    @change="emit('update:modelValue', $event ?? null)"
  >
    <el-option
      v-for="c in allComponents"
      :key="c.id"
      :value="c.id"
      :label="c.linkedFormName ? `${c.componentName} → ${c.linkedFormName}` : c.componentName"
    />
    <template
      v-if="allComponents.length === 0"
      #empty
    >
      <span class="el-select-dropdown__empty">{{ t('designer.linkFormSelectEmpty') }}</span>
    </template>
  </el-select>
</template>
