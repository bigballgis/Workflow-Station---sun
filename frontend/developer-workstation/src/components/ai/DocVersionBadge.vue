<template>
  <span
    v-if="props.version != null || props.generatedAt"
    class="doc-version-badge"
    :class="{ 'doc-version-badge--fresh': props.fresh }"
    :title="fullTimestamp"
  >
    <span v-if="props.version != null">v{{ props.version }}</span>
    <span
      v-if="props.version != null && clockTime"
      class="doc-version-badge__sep"
    >·</span>
    <span v-if="clockTime">{{ clockTime }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 产物的版本戳（vN · HH:mm:ss）。
 *
 * 存在的理由：点了 Regenerate 之后，正文往往只差一两个字段，用户没法从内容判断这一版到底
 * 重出了没有。版本号 + 时间是唯一可信的信号，所以按 ai-tokens 的"机器话语"规范走等宽字体。
 * fresh=true 时短暂高亮，指出"这就是刚回来的那一版"。
 */
const props = defineProps<{
  version?: number
  /** ISO 时间串；后端 document 事件给的是文档落库时间，Preview 给的是本地生成时刻。 */
  generatedAt?: string
  fresh?: boolean
}>()

const parsedDate = computed(() => {
  if (!props.generatedAt) return null
  const date = new Date(props.generatedAt)
  return Number.isNaN(date.getTime()) ? null : date
})

const clockTime = computed(() => parsedDate.value?.toLocaleTimeString() ?? '')

const fullTimestamp = computed(() => parsedDate.value?.toLocaleString() ?? '')
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.doc-version-badge {
  @include ai.ai-mono-num;

  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 6px;
  border: 1px solid ai.$ai-hairline;
  border-radius: 999px;
  font-size: 10px;
  line-height: 16px;
  color: ai.$ai-faint;
  white-space: nowrap;
  transition: color 0.3s, border-color 0.3s, background-color 0.3s;
}

// 刚重出的那一版：红色描边把用户的视线拉到版本号上，几秒后由父组件撤掉
.doc-version-badge--fresh {
  border-color: ai.$ai-red;
  background: ai.$ai-red-soft;
  color: ai.$ai-red-deep;
}

.doc-version-badge__sep {
  color: ai.$ai-hairline;
}
</style>
