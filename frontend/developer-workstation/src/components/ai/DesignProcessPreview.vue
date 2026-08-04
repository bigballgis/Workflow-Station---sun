<template>
  <div class="design-process-preview">
    <p
      v-if="!layout.nodes.length"
      class="design-process-preview__empty"
    >
      {{ t('ai.doc.processPreviewEmpty') }}
    </p>

    <template v-else>
      <div class="design-process-preview__toolbar">
        <button
          type="button"
          class="design-process-preview__zoom"
          :title="t('ai.doc.zoomOut')"
          :disabled="scale <= MIN_SCALE"
          @click="zoomBy(-ZOOM_STEP)"
        >
          −
        </button>
        <button
          type="button"
          class="design-process-preview__zoom is-wide"
          :title="t('ai.doc.zoomFit')"
          @click="fitToWidth"
        >
          {{ Math.round(scale * 100) }}%
        </button>
        <button
          type="button"
          class="design-process-preview__zoom"
          :title="t('ai.doc.zoomIn')"
          :disabled="scale >= MAX_SCALE"
          @click="zoomBy(ZOOM_STEP)"
        >
          +
        </button>
      </div>

      <div
        ref="canvas"
        class="design-process-preview__canvas"
      >
        <svg
          :viewBox="`0 0 ${layout.width} ${layout.height}`"
          :width="layout.width * scale"
          :height="layout.height * scale"
          preserveAspectRatio="xMinYMin meet"
          role="img"
          :aria-label="t('ai.doc.processView')"
        >
        <defs>
          <marker
            id="design-process-arrow"
            viewBox="0 0 8 8"
            refX="7"
            refY="4"
            markerWidth="7"
            markerHeight="7"
            orient="auto-start-reverse"
          >
            <path
              d="M0,0 L8,4 L0,8 z"
              class="design-process-preview__arrow"
            />
          </marker>
        </defs>

        <g
          v-for="edge in layout.edges"
          :key="edge.id"
        >
          <polyline
            class="design-process-preview__edge"
            :points="edge.points.map(p => `${p.x},${p.y}`).join(' ')"
            marker-end="url(#design-process-arrow)"
          />
          <text
            v-if="edge.condition"
            class="design-process-preview__edge-label"
            :x="edge.labelX"
            :y="edge.labelY"
            text-anchor="middle"
          >
            {{ truncate(edge.condition, 22) }}
            <title>{{ edge.condition }}</title>
          </text>
        </g>

        <g
          v-for="node in layout.nodes"
          :key="node.id"
        >
          <circle
            v-if="node.shape === 'event'"
            class="design-process-preview__event"
            :class="{ 'is-end': isEndEvent(node.type) }"
            :cx="node.x + node.width / 2"
            :cy="node.y + node.height / 2"
            :r="node.width / 2"
          />
          <polygon
            v-else-if="node.shape === 'gateway'"
            class="design-process-preview__gateway"
            :points="diamond(node)"
          />
          <rect
            v-else
            class="design-process-preview__task"
            :x="node.x"
            :y="node.y"
            :width="node.width"
            :height="node.height"
            rx="6"
          />

          <text
            v-if="node.shape === 'task'"
            class="design-process-preview__task-label"
            :x="node.x + node.width / 2"
            :y="node.y + (node.actions.length ? 22 : node.height / 2 + 4)"
            text-anchor="middle"
          >
            {{ truncate(node.name, 18) }}
            <title>{{ nodeTooltip(node) }}</title>
          </text>
          <text
            v-if="node.shape === 'task' && node.actions.length"
            class="design-process-preview__task-actions"
            :x="node.x + node.width / 2"
            :y="node.y + 38"
            text-anchor="middle"
          >
            {{ truncate(node.actions.join(' · '), 22) }}
            <title>{{ node.actions.join(', ') }}</title>
          </text>
          <text
            v-if="node.shape !== 'task'"
            class="design-process-preview__outside-label"
            :x="node.x + node.width / 2"
            :y="node.y + node.height + 14"
            text-anchor="middle"
          >
            {{ truncate(node.name, 16) }}
            <title>{{ nodeTooltip(node) }}</title>
          </text>
        </g>
        </svg>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  parseDesignProcess,
  layoutDesignProcess,
  type ProcessLayoutNode
} from '@/utils/designDocumentPreview'

const props = defineProps<{
  content: string
}>()

const { t } = useI18n()

const layout = computed(() => layoutDesignProcess(parseDesignProcess(props.content)))

const MIN_SCALE = 0.3
const MAX_SCALE = 3
const ZOOM_STEP = 0.25

const canvas = ref<HTMLElement | null>(null)
/**
 * 显示比例。初值按容器宽度自适应——聊天区只有几百像素宽，一比一放进去只看得见头两个节点。
 * 缩放改的是 svg 的 width/height（viewBox 不动），所以文字与线条一起放大，不会糊。
 */
const scale = ref(1)

function zoomBy(delta: number): void {
  scale.value = Math.min(MAX_SCALE, Math.max(MIN_SCALE, Number((scale.value + delta).toFixed(2))))
}

/** 缩到刚好放进容器；图本来就比容器窄时保持一比一，不做无谓放大。 */
function fitToWidth(): void {
  const available = canvas.value?.clientWidth
  if (!available || !layout.value.width) return
  scale.value = Math.min(1, Math.max(MIN_SCALE, Number((available / layout.value.width).toFixed(2))))
}

onMounted(() => nextTick(fitToWidth))
// 换文档（重新生成 / 切会话）后图的尺寸变了，比例要跟着重算，否则沿用上一张图的缩放。
watch(() => props.content, () => nextTick(fitToWidth))

function isEndEvent(type: string): boolean {
  return type.toLowerCase().includes('end')
}

function diamond(node: ProcessLayoutNode): string {
  const cx = node.x + node.width / 2
  const cy = node.y + node.height / 2
  const half = node.width / 2
  return `${cx},${cy - half} ${cx + half},${cy} ${cx},${cy + half} ${cx - half},${cy}`
}

/** 图上放不下的信息（id / 类型 / 绑定表单）走 tooltip，画布保持干净。 */
function nodeTooltip(node: ProcessLayoutNode): string {
  const parts = [`${node.name} (${node.id})`]
  if (node.type) parts.push(node.type)
  if (node.form) parts.push(`${t('ai.doc.boundForm')}: ${node.form}`)
  if (node.actions.length) parts.push(`${t('ai.doc.nodeActions')}: ${node.actions.join(', ')}`)
  return parts.join('\n')
}

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text
}
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.design-process-preview__empty {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  color: ai.$ai-graphite;
}

.design-process-preview__toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 0 0 4px;
}

.design-process-preview__zoom {
  font: inherit;
  font-size: 12px;
  line-height: 1;
  min-width: 24px;
  padding: 4px 6px;
  border: 1px solid ai.$ai-hairline;
  border-radius: 4px;
  background: ai.$ai-paper;
  color: ai.$ai-graphite;
  cursor: pointer;

  &.is-wide {
    min-width: 46px;
    font-family: ai.$ai-mono;
  }

  &:hover:not(:disabled) {
    color: ai.$ai-ink;
    background: ai.$ai-mist-deep;
  }

  &:disabled {
    opacity: 0.4;
    cursor: default;
  }
}

// 放大后横竖都可能溢出：两个方向都给滚动，图本身不再受容器宽度约束。
.design-process-preview__canvas {
  overflow: auto;
  max-height: 420px;
  padding: 4px 0;
}

.design-process-preview__edge {
  fill: none;
  stroke: ai.$ai-faint;
  stroke-width: 1.5;
}

.design-process-preview__arrow {
  fill: ai.$ai-faint;
}

.design-process-preview__edge-label {
  font-size: 10px;
  fill: ai.$ai-graphite;
}

.design-process-preview__task {
  fill: ai.$ai-paper;
  stroke: ai.$ai-red;
  stroke-width: 1.5;
}

.design-process-preview__gateway {
  fill: ai.$ai-paper;
  stroke: ai.$ai-graphite;
  stroke-width: 1.5;
}

.design-process-preview__event {
  fill: ai.$ai-paper;
  stroke: ai.$ai-graphite;
  stroke-width: 1.5;

  &.is-end {
    stroke-width: 3;
  }
}

.design-process-preview__task-label {
  font-size: 11px;
  font-weight: 600;
  fill: ai.$ai-ink;
}

.design-process-preview__task-actions {
  font-size: 10px;
  fill: ai.$ai-graphite;
}

.design-process-preview__outside-label {
  font-size: 10px;
  fill: ai.$ai-graphite;
}
</style>
