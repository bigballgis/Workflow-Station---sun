<template>
  <div
    class="record-note-preview"
    :class="{ 'is-compact': compact }"
  >
    <div
      class="rn-header"
      :class="{ clickable: compact }"
      @click="compact ? (expanded = !expanded) : undefined"
    >
      <el-icon><ChatLineSquare /></el-icon>
      <span class="rn-title">{{ panelTitle }}</span>
      <el-tag
        size="small"
        round
        type="info"
      >{{ sampleNotes.length }}</el-tag>
      <div class="rn-header-actions">
        <el-button
          v-if="bodyVisible && !readonly"
          size="small"
          type="primary"
          link
          @click.stop="showEditor = !showEditor"
        >
          + {{ t('form.recordNoteAdd') }}
        </el-button>
        <!--
          Readonly is a To Do-form setting, so say so rather than just hiding Add: on a request
          form the panel stays writable for audit roles regardless of this switch, and a silently
          missing button would read as a broken preview.
        -->
        <span
          v-if="bodyVisible && readonly"
          class="rn-readonly-hint"
        >{{ t('form.recordNoteReadonlyHint') }}</span>
        <el-icon
          v-if="compact"
          class="rn-caret"
        >
          <component :is="expanded ? 'ArrowUp' : 'ArrowDown'" />
        </el-icon>
      </div>
    </div>

    <template v-if="bodyVisible">
      <!--
        Add area: a non-functional stand-in for the portal's rich-text editor. Preview has no
        record to attach a note to, so this shows the shape (toolbar / body / attach / actions)
        without loading the editor engine.
      -->
      <div
        v-if="showEditor"
        class="rn-editor"
      >
        <div class="rn-editor-toolbar">
          <span
            v-for="icon in editorToolbarIcons"
            :key="icon"
            class="rn-editor-tool"
          >{{ icon }}</span>
        </div>
        <div class="rn-editor-body">{{ t('form.recordNotePreviewPlaceholder') }}</div>
        <div
          v-if="allowAttachment"
          class="rn-editor-files"
        >
          <el-button
            size="small"
            link
            type="primary"
            disabled
          >
            <el-icon><Paperclip /></el-icon>
            {{ t('form.recordNoteAttach') }}
          </el-button>
          <span class="rn-file-limit">{{ t('form.recordNoteMaxSize', { size: maxFileSizeMb }) }}</span>
        </div>
        <div class="rn-editor-actions">
          <el-button
            size="small"
            @click="showEditor = false"
          >{{ t('common.cancel') }}</el-button>
          <el-button
            size="small"
            type="primary"
            disabled
          >{{ t('form.recordNotePost') }}</el-button>
        </div>
      </div>

      <!-- Sample note stream: shows how posted notes read, capped at the designed page size. -->
      <div class="rn-list">
        <div
          v-for="note in sampleNotes"
          :key="note.author"
          class="rn-item"
        >
          <div class="rn-item-meta">
            <span class="rn-author">{{ note.author }}</span>
            <span class="rn-time">{{ note.time }}</span>
          </div>
          <div class="rn-body-text">{{ note.body }}</div>
          <div
            v-if="allowAttachment && note.attachment"
            class="rn-chips"
          >
            <el-tag
              size="small"
              class="rn-chip"
            >
              <el-icon><Paperclip /></el-icon>
              {{ note.attachment }}
            </el-tag>
          </div>
        </div>
        <div
          v-if="pageSize < SAMPLE_NOTES.length"
          class="rn-load-more"
        >
          <el-button
            link
            type="primary"
            size="small"
            disabled
          >{{ t('form.recordNoteLoadMore') }}</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
// Icons are globally registered in main.ts via ElementPlusIconsVue
import { useI18n } from 'vue-i18n'

/**
 * Preview rendering of the Record Note component — visual parity with the portal's
 * RecordNoteField (frontend/user-portal/src/components/RecordNoteField.vue), driven by
 * sample data. Preview has no process instance or row id, so the real panel would only
 * ever show "Notes become available after the record is saved"; showing the designed
 * shape instead is what makes Preview useful. The designer canvas keeps its own dashed
 * placeholder (RecordNotePlaceholderWidget) — that one is a drop target, this one is not.
 */
const props = defineProps<{
  config?: {
    scope?: string
    panelTitle?: string
    allowAttachment?: boolean
    maxFileSizeMb?: number
    pageSize?: number
    readonly?: boolean
  }
}>()

const { t } = useI18n()

const scope = computed(() => (props.config?.scope === 'TABLE' ? 'TABLE' : 'RECORD'))
/** RECORD scope renders as a collapsible card in the portal (one panel per sub-table row). */
const compact = computed(() => scope.value === 'RECORD')
const panelTitle = computed(() => props.config?.panelTitle || t('form.recordNoteDefaultTitle'))
const allowAttachment = computed(() => props.config?.allowAttachment !== false)
const maxFileSizeMb = computed(() => Math.min(Number(props.config?.maxFileSizeMb) || 10, 10))
const pageSize = computed(() => Math.max(1, Number(props.config?.pageSize) || 5))
/** Opt-in, matching the runtime extractors: only an explicit true seals the panel. */
const readonly = computed(() => props.config?.readonly === true)

const expanded = ref(false)
const bodyVisible = computed(() => !compact.value || expanded.value)
const showEditor = ref(false)

const editorToolbarIcons = ['B', 'I', 'U', 'S', 'A', '•', '1.', '🔗', '🖼']

const SAMPLE_NOTES = [
  { author: 'Alice Chen', time: '2026-01-15 09:24', body: 'Checked the attached invoice — amounts match the PO.', attachment: 'invoice-2026-0115.pdf' },
  { author: 'Ben Walker', time: '2026-01-14 17:02', body: 'Forwarded to Finance for the second approval.', attachment: '' },
  { author: 'Cara Diaz', time: '2026-01-14 11:38', body: 'Requester confirmed the delivery address change.', attachment: '' },
]

/** Honour the designed "Visible Notes" page size so the setting is visible in Preview. */
const sampleNotes = computed(() => SAMPLE_NOTES.slice(0, pageSize.value))
</script>

<style scoped>
/*
 * Kept visually identical to the portal panel (RecordNoteField.vue) — design parity is the
 * whole point of Preview, so these rules mirror that file's styles rather than restating
 * the designer canvas look.
 */
.record-note-preview {
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--el-bg-color, #fff);
}

.record-note-preview.is-compact {
  padding: 8px 12px;
}

.rn-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rn-header.clickable {
  cursor: pointer;
}

.rn-title {
  font-weight: 500;
}

.rn-header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.rn-readonly-hint {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}

.rn-editor {
  margin-top: 10px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 4px;
}

.rn-editor-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}

.rn-editor-tool {
  min-width: 14px;
  text-align: center;
}

.rn-editor-body {
  min-height: 90px;
  padding: 10px;
  color: var(--el-text-color-placeholder, #a8abb2);
  font-size: 13px;
}

.rn-editor-files {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.rn-file-limit {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}

.rn-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 8px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.rn-list {
  margin-top: 8px;
}

.rn-item {
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.rn-item:first-child {
  border-top: none;
}

.rn-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.rn-author {
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
}

.rn-body-text {
  margin-top: 4px;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}

.rn-chips {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.rn-load-more {
  padding-top: 6px;
  text-align: center;
}
</style>
