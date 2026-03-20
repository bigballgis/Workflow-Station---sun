<template>
  <el-radio-group :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event as ViewMode)">
    <el-radio-button value="xml">{{ t('ai.doc.xmlView') }}</el-radio-button>
    <el-radio-button value="markdown">{{ t('ai.doc.markdownView') }}</el-radio-button>
  </el-radio-group>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ViewMode } from '@/types/aiGeneration'

const { t } = useI18n()

defineProps<{
  modelValue: ViewMode
}>()

defineEmits<{
  'update:modelValue': [mode: ViewMode]
}>()
</script>
