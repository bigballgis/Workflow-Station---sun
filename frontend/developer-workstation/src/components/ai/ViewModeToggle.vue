<template>
  <div
    class="view-mode-toggle"
    role="tablist"
  >
    <button
      v-for="mode in MODES"
      :key="mode.value"
      type="button"
      role="tab"
      class="view-mode-toggle__seg"
      :class="{ 'is-active': modelValue === mode.value }"
      :aria-selected="modelValue === mode.value"
      @click="$emit('update:modelValue', mode.value)"
    >
      {{ t(mode.labelKey) }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ViewMode } from '@/types/aiGeneration'

const { t } = useI18n()

const LABEL_KEYS: Record<ViewMode, string> = {
  xml: 'ai.doc.xmlView',
  markdown: 'ai.doc.markdownView',
  process: 'ai.doc.processView',
  table: 'ai.doc.tableView'
}

// 模式集合可由调用方裁剪：DESIGN 文档多两段结构化预览，其余文档仍是 xml / markdown 两段。
const props = withDefaults(defineProps<{
  modelValue: ViewMode
  modes?: ViewMode[]
}>(), {
  // 字面量不能提出去：defineProps 的默认值工厂会被提升到 setup() 之外，引用不到局部常量。
  modes: () => ['xml', 'markdown'] as ViewMode[]
})

const MODES = computed(() =>
  props.modes.map(value => ({ value, labelKey: LABEL_KEYS[value] })))

defineEmits<{
  'update:modelValue': [mode: ViewMode]
}>()
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

// 紧凑分段控件：mist 底槽，激活段白纸浮起
.view-mode-toggle {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  background: ai.$ai-mist-deep;
  border-radius: 7px;
  flex-shrink: 0;
}

.view-mode-toggle__seg {
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  padding: 5px 10px;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: ai.$ai-graphite;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;

  &:hover {
    color: ai.$ai-ink;
  }

  &.is-active {
    background: ai.$ai-paper;
    color: ai.$ai-ink;
    font-weight: 600;
    box-shadow: 0 1px 2px rgba(35, 40, 46, 0.12);
  }

  &:focus-visible {
    outline: 2px solid ai.$ai-red;
    outline-offset: 1px;
  }
}
</style>
