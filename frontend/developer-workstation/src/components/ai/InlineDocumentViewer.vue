<template>
  <div class="inline-doc-viewer">
    <div
      class="inline-doc-viewer__card"
      :class="{ 'is-expanded': expanded }"
      :style="expanded ? { maxHeight: maxHeight + 'px' } : undefined"
    >
      <!--
        整条标题栏就是展开/收起的开关：原来展开靠点空白、收起只认按钮，
        同一块区域两套规则。收下按钮，留一个箭头做状态指示。
      -->
      <div
        class="inline-doc-viewer__header"
        role="button"
        tabindex="0"
        :aria-expanded="expanded"
        :aria-label="toggleLabel"
        :title="toggleLabel"
        @click="expanded = !expanded"
        @keydown.enter.prevent="expanded = !expanded"
        @keydown.space.prevent="expanded = !expanded"
      >
        <span
          class="inline-doc-viewer__arrow"
          :class="{ 'is-expanded': expanded }"
        >
          <el-icon><ArrowRight /></el-icon>
        </span>
        <span class="inline-doc-viewer__label">{{ docLabel }}</span>
        <DocVersionBadge
          :version="props.version"
          :generated-at="props.generatedAt"
          :fresh="props.fresh"
        />
        <!-- 视图切换/重出是各自独立的动作，点它们不该顺带收起文档 -->
        <span
          v-if="expanded"
          class="inline-doc-viewer__control"
          @click.stop
          @keydown.enter.stop
          @keydown.space.stop
        >
          <ViewModeToggle
            v-model="viewMode"
            :modes="availableModes"
          />
        </span>
        <span class="inline-doc-viewer__spacer" />
        <span
          class="inline-doc-viewer__control"
          @click.stop
          @keydown.enter.stop
          @keydown.space.stop
        >
          <RegenerateBox
            text
            :disabled="props.busy"
            @confirm="instruction => emit('regenerate', props.documentType, instruction)"
          />
        </span>
      </div>
      <div
        v-if="expanded"
        class="inline-doc-viewer__body"
      >
        <XmlTreeView
          v-show="viewMode === 'xml'"
          :content="content"
        />
        <MarkdownRenderer
          v-show="viewMode === 'markdown'"
          :content="content"
        />
        <DesignProcessPreview
          v-if="viewMode === 'process'"
          :content="content"
        />
        <DesignTablePreview
          v-if="viewMode === 'table'"
          :content="content"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight } from '@element-plus/icons-vue'
import ViewModeToggle from './ViewModeToggle.vue'
import XmlTreeView from './XmlTreeView.vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import DesignProcessPreview from './DesignProcessPreview.vue'
import DesignTablePreview from './DesignTablePreview.vue'
import DocVersionBadge from './DocVersionBadge.vue'
import RegenerateBox from './RegenerateBox.vue'
import type { AiDocumentType, ViewMode } from '@/types/aiGeneration'

const props = defineProps<{
  documentType: AiDocumentType
  content: string
  /** AI 正在回复：重出按钮置灰，避免在流式过程中再发一轮请求（会被 useAiChat 直接丢弃）。 */
  busy?: boolean
  /** 版本戳：告诉用户手上这份是不是刚重出的那一版，见 DocVersionBadge。 */
  version?: number
  generatedAt?: string
  fresh?: boolean
}>()

const emit = defineEmits<{
  /**
   * 只重出这一份文档，不推进会话相位——由 ChatDialog 转成 regenerateOnly 请求。
   * instruction 为用户填的定向修改指令，空串表示整篇重出。
   */
  regenerate: [documentType: AiDocumentType, instruction: string]
}>()

const { t } = useI18n()

const expanded = ref(false)
const viewMode = ref<ViewMode>('xml')
const chatMessagesHeight = inject<{ value: number }>('chatMessagesHeight', ref(400))

const maxHeight = computed(() => Math.floor(chatMessagesHeight.value * 0.7))

/**
 * 设计文档多出流程图与数据模型两段预览：设计阶段还没有 BPMN XML，
 * 但文档里的节点/连线/字段矩阵已经够画出来了，省得用户在上万字正文里靠肉眼拼流程。
 */
const availableModes = computed<ViewMode[]>(() =>
  props.documentType === 'DESIGN'
    ? ['xml', 'markdown', 'process', 'table']
    : ['xml', 'markdown'])

const docLabel = computed(() =>
  t(`ai.doc.${props.documentType === 'REQUIREMENTS' ? 'requirements' : 'design'}`)
)

/** 按钮没了，标题栏本身要能被读屏和鼠标悬停问出来它是干什么的。 */
const toggleLabel = computed(() => t(expanded.value ? 'ai.doc.collapse' : 'ai.doc.expand'))
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.inline-doc-viewer {
  margin: 8px 16px;
}

.inline-doc-viewer__card {
  background: ai.$ai-paper;
  border: 1px solid ai.$ai-hairline;
  border-left: 2px solid ai.$ai-red;
  border-radius: 6px;
  transition: box-shadow 0.2s;

  &:not(.is-expanded):hover {
    box-shadow: 0 2px 8px rgba(35, 40, 46, 0.06);
  }

  &.is-expanded {
    overflow-y: auto;
  }
}

// 把 Regenerate 顶到行尾，与左侧的展开/收起动作分开——两者的破坏性完全不同
.inline-doc-viewer__spacer {
  flex: 1;
}

.inline-doc-viewer__label {
  font-size: 12px;
  font-weight: 600;
  color: ai.$ai-ink;
}

.inline-doc-viewer__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: ai.$ai-paper;
  position: sticky;
  top: 0;
  z-index: 1;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid ai.$ai-red;
    outline-offset: -2px;
  }
}

.inline-doc-viewer__card.is-expanded .inline-doc-viewer__header {
  border-bottom: 1px solid ai.$ai-hairline;
}

// 与 XML 树里的节点箭头同一套语汇：向右是收着的，转 90° 是打开的
.inline-doc-viewer__arrow {
  display: inline-flex;
  align-items: center;
  color: ai.$ai-graphite;
  transition: transform 0.2s, color 0.2s;

  &.is-expanded {
    transform: rotate(90deg);
  }

  // 红色留给左侧那条竖线，箭头只在鼠标停到标题栏上时亮起来——按钮没了，
  // 悬停反馈就是「这一整行能点」的唯一提示
  .inline-doc-viewer__header:hover & {
    color: ai.$ai-red;
  }
}

// 标题栏整条可点，但这些控件点下去只做自己的事
.inline-doc-viewer__control {
  display: inline-flex;
  align-items: center;
  cursor: default;
}

.inline-doc-viewer__body {
  padding: 8px;
}
</style>
