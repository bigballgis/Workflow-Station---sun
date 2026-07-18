<template>
  <div
    class="record-note-placeholder-widget"
    :class="`is-${effectiveScope.toLowerCase()}`"
    @click.stop="onWidgetClick"
  >
    <div class="rn-header">
      <el-icon><ChatLineSquare /></el-icon>
      <span class="rn-title">{{ panelTitle || t('form.recordNoteDefaultTitle') }}</span>
      <el-tag
        size="small"
        :type="effectiveScope === 'TABLE' ? 'info' : 'primary'"
      >
        {{ effectiveScope === 'TABLE' ? t('form.recordNoteScopeTable') : t('form.recordNoteScopeRecord') }}
      </el-tag>
      <el-button
        link
        type="primary"
        size="small"
        class="rn-add-btn"
        disabled
      >
        + {{ t('form.recordNoteAdd') }}
      </el-button>
    </div>
    <div class="rn-mock-list">
      <div class="rn-mock-line" />
      <div class="rn-mock-line short" />
    </div>
    <div class="rn-hint">{{ t('form.recordNotePlaceholderHint') }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
// Icons are globally registered in main.ts via ElementPlusIconsVue
import { useI18n } from 'vue-i18n'

// form-create passes rule props flat as component props
const props = defineProps<{
  scope?: string
  panelTitle?: string
  allowAttachment?: boolean
  maxFileSizeMb?: number
  allowEditOwn?: boolean
  pageSize?: number
  formCreateInject?: any
}>()

const { t } = useI18n()

const effectiveScope = computed(() => (props.scope === 'TABLE' ? 'TABLE' : 'RECORD'))
const panelTitle = computed(() => props.panelTitle)

// When clicked in designer, manually trigger DragTool selection
// by finding the parent _fd-drag-tool and dispatching a click on it
function onWidgetClick(e: MouseEvent) {
  e.stopPropagation()
  const el = e.currentTarget as HTMLElement
  const dragTool = el.closest('._fd-drag-tool') as HTMLElement | null
  if (dragTool) {
    const clickEvent = new MouseEvent('click', { bubbles: false, cancelable: true })
    dragTool.dispatchEvent(clickEvent)
  }
}
</script>

<style scoped>
.record-note-placeholder-widget {
  padding: 10px 12px;
  border-radius: 4px;
  border: 1px dashed #dcdfe6;
  background: #f5f7fa;
  font-size: 13px;
  cursor: default;
  user-select: none;
}

.record-note-placeholder-widget.is-record {
  border-color: #409eff;
  background: #ecf5ff;
}

.rn-header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rn-title {
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
}

.rn-header .el-tag {
  flex-shrink: 0;
}

.rn-add-btn {
  margin-left: auto;
}

.rn-mock-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rn-mock-line {
  height: 8px;
  border-radius: 4px;
  background: #e4e7ed;
}

.rn-mock-line.short {
  width: 60%;
}

.rn-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
