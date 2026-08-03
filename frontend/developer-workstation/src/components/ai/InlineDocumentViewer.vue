<template>
  <div class="inline-doc-viewer">
    <!-- Collapsed state -->
    <div
      v-if="!expanded"
      class="inline-doc-viewer__collapsed"
      @click="expanded = true"
    >
      <span class="inline-doc-viewer__label">{{ docLabel }}</span>
      <DocVersionBadge
        :version="props.version"
        :generated-at="props.generatedAt"
        :fresh="props.fresh"
      />
      <el-button
        size="small"
        text
        type="primary"
      >
        {{ t('ai.doc.expand') }}
      </el-button>
      <span class="inline-doc-viewer__spacer" />
      <RegenerateBox
        text
        :disabled="props.busy"
        @confirm="instruction => emit('regenerate', props.documentType, instruction)"
      />
    </div>

    <!-- Expanded state -->
    <div
      v-else
      class="inline-doc-viewer__expanded"
      :style="{ maxHeight: maxHeight + 'px' }"
    >
      <div class="inline-doc-viewer__header">
        <span class="inline-doc-viewer__label">{{ docLabel }}</span>
        <DocVersionBadge
          :version="props.version"
          :generated-at="props.generatedAt"
          :fresh="props.fresh"
        />
        <ViewModeToggle
          v-model="viewMode"
          :modes="availableModes"
        />
        <el-button
          size="small"
          text
          type="primary"
          @click="expanded = false"
        >
          {{ t('ai.doc.collapse') }}
        </el-button>
        <span class="inline-doc-viewer__spacer" />
        <RegenerateBox
          text
          :disabled="props.busy"
          @confirm="instruction => emit('regenerate', props.documentType, instruction)"
        />
      </div>
      <div class="inline-doc-viewer__body">
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
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.inline-doc-viewer {
  margin: 8px 16px;
}

.inline-doc-viewer__collapsed {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: ai.$ai-paper;
  border: 1px solid ai.$ai-hairline;
  border-left: 2px solid ai.$ai-red;
  border-radius: 6px;
  cursor: pointer;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 8px rgba(35, 40, 46, 0.06);
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

.inline-doc-viewer__expanded {
  border: 1px solid ai.$ai-hairline;
  border-left: 2px solid ai.$ai-red;
  border-radius: 6px;
  overflow-y: auto;
  background: ai.$ai-paper;
}

.inline-doc-viewer__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid ai.$ai-hairline;
  background: ai.$ai-paper;
  position: sticky;
  top: 0;
  z-index: 1;
}

.inline-doc-viewer__body {
  padding: 8px;
}
</style>
