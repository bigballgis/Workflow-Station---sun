<template>
  <div class="inline-doc-viewer">
    <!-- Collapsed state -->
    <div
      v-if="!expanded"
      class="inline-doc-viewer__collapsed"
      @click="expanded = true"
    >
      <span class="inline-doc-viewer__label">{{ docLabel }}</span>
      <el-button
        size="small"
        text
        type="primary"
      >
        {{ t('ai.doc.expand') }}
      </el-button>
    </div>

    <!-- Expanded state -->
    <div
      v-else
      class="inline-doc-viewer__expanded"
      :style="{ maxHeight: maxHeight + 'px' }"
    >
      <div class="inline-doc-viewer__header">
        <span class="inline-doc-viewer__label">{{ docLabel }}</span>
        <ViewModeToggle v-model="viewMode" />
        <el-button
          size="small"
          text
          type="primary"
          @click="expanded = false"
        >
          {{ t('ai.doc.collapse') }}
        </el-button>
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
import type { AiDocumentType, ViewMode } from '@/types/aiGeneration'

const props = defineProps<{
  documentType: AiDocumentType
  content: string
}>()

const { t } = useI18n()

const expanded = ref(false)
const viewMode = ref<ViewMode>('xml')
const chatMessagesHeight = inject<{ value: number }>('chatMessagesHeight', ref(400))

const maxHeight = computed(() => Math.floor(chatMessagesHeight.value * 0.7))

const docLabel = computed(() =>
  t(`ai.doc.${props.documentType === 'REQUIREMENTS' ? 'requirements' : 'design'}`)
)
</script>

<style lang="scss" scoped>
.inline-doc-viewer {
  margin: 8px 16px;
}

.inline-doc-viewer__collapsed {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #ebeef5;
  }
}

.inline-doc-viewer__label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.inline-doc-viewer__expanded {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow-y: auto;
}

.inline-doc-viewer__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
  position: sticky;
  top: 0;
  z-index: 1;
}

.inline-doc-viewer__body {
  padding: 8px;
}
</style>
