<template>
  <div
    class="record-note-field"
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
      >{{ total }}</el-tag>
      <div class="rn-header-actions">
        <el-button
          v-if="canWrite && !showEditor && bodyVisible"
          size="small"
          type="primary"
          link
          @click.stop="openEditor()"
        >
          + {{ t('recordNote.add') }}
        </el-button>
        <el-icon
          v-if="compact"
          class="rn-caret"
        >
          <component :is="expanded ? 'ArrowUp' : 'ArrowDown'" />
        </el-icon>
      </div>
    </div>

    <template v-if="bodyVisible">
      <div
        v-if="unavailable"
        class="rn-disabled"
      >
        {{ t('recordNote.saveFirst') }}
      </div>
      <template v-else>
        <!-- Add / edit area: appears only after clicking Add -->
        <div
          v-if="showEditor"
          class="rn-editor"
        >
          <Toolbar
            class="rn-editor-toolbar"
            :editor="editorInstance"
            :default-config="toolbarConfig"
            mode="simple"
          />
          <Editor
            v-model="editorHtml"
            class="rn-editor-body"
            :default-config="editorConfig"
            mode="simple"
            @on-created="onEditorCreated"
          />
          <div
            v-if="allowAttachment"
            class="rn-editor-files"
          >
            <el-upload
              :auto-upload="false"
              :show-file-list="true"
              multiple
              :on-change="onFilePicked"
              :on-remove="onFileRemoved"
              :file-list="uploadList"
            >
              <el-button
                size="small"
                link
                type="primary"
              >
                <el-icon><Paperclip /></el-icon>
                {{ t('recordNote.attach') }}
              </el-button>
            </el-upload>
          </div>
          <div class="rn-editor-actions">
            <el-button
              size="small"
              @click="closeEditor"
            >{{ t('recordNote.cancel') }}</el-button>
            <el-button
              size="small"
              type="primary"
              :loading="submitting"
              @click="submit"
            >
              {{ t('recordNote.submit') }}
            </el-button>
          </div>
        </div>

        <!-- Note list -->
        <div
          v-loading="loading"
          class="rn-list"
        >
          <div
            v-if="!notes.length && !loading"
            class="rn-empty"
          >
            {{ t('recordNote.empty') }}
          </div>
          <div
            v-for="note in notes"
            :key="note.id"
            class="rn-item"
          >
            <div class="rn-item-meta">
              <span class="rn-author">{{ note.createdByName || note.createdBy }}</span>
              <span class="rn-time">{{ formatTime(note.createdAt) }}</span>
              <span
                v-if="isEdited(note)"
                class="rn-edited"
              >{{ t('recordNote.edited') }}</span>
              <span class="rn-item-actions">
                <el-button
                  v-if="note.noteType === 'COMMENT' && note.editable && allowEditOwn && canWrite"
                  size="small"
                  link
                  @click="startEdit(note)"
                >{{ t('recordNote.edit') }}</el-button>
                <el-button
                  v-if="note.editable && canWrite && allowDelete"
                  size="small"
                  link
                  type="danger"
                  @click="removeNote(note)"
                >{{ t('recordNote.delete') }}</el-button>
              </span>
            </div>
            <div
              v-if="note.subject"
              class="rn-subject"
            >{{ note.subject }}</div>
            <template v-if="note.noteType === 'COMMENT'">
              <div
                v-if="note.bodyHtml || displayHtml[note.id]"
                class="rn-body-html"
                v-html="displayHtml[note.id] ?? note.bodyHtml"
              />
              <div
                v-else-if="note.bodyText"
                class="rn-body-text"
              >
                {{ note.bodyText }}
              </div>
            </template>
            <div
              v-else
              class="rn-standalone-file"
            >
              <el-link
                type="primary"
                @click="download(note.id, note.fileName || '')"
              >
                <el-icon><Paperclip /></el-icon>
                {{ note.fileName }}
              </el-link>
              <span class="rn-file-size">{{ formatSize(note.fileSize) }}</span>
            </div>
            <div
              v-if="note.attachments && note.attachments.length"
              class="rn-chips"
            >
              <el-tag
                v-for="att in note.attachments"
                :key="att.id"
                size="small"
                class="rn-chip"
                @click="download(att.id, att.fileName)"
              >
                <el-icon><Paperclip /></el-icon>
                {{ att.fileName }} <span class="rn-file-size">{{ formatSize(att.fileSize) }}</span>
              </el-tag>
            </div>
          </div>
          <div
            v-if="hasNext"
            class="rn-load-more"
          >
            <el-button
              link
              type="primary"
              size="small"
              @click="loadMore"
            >{{ t('recordNote.loadMore') }}</el-button>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, provide, reactive, ref, shallowRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formContextKey } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { i18nChangeLanguage } from '@wangeditor/editor'
import '@wangeditor/editor/dist/css/style.css'

// wangeditor ships with a Chinese UI by default; the platform is English-first.
i18nChangeLanguage('en')
import {
  createRecordNote,
  deleteRecordNote,
  downloadRecordNoteAttachment,
  fetchRecordNoteBlobUrl,
  getRecordNoteDetail,
  listRecordNotes,
  recordNoteContentUrl,
  updateRecordNote,
  uploadInlineImage,
  type RecordNoteItem,
  type RecordNoteTargetParams,
} from '@/api/recordNote'
import { notifyRecordNoteChanged } from './formRendererHelpers/recordNoteFields'

export interface RecordNoteConfig {
  scope?: string
  panelTitle?: string
  allowAttachment?: boolean
  maxFileSizeMb?: number
  allowEditOwn?: boolean
  /** Designer switch, default OFF: notes are an audit trail, so deletion is opt-in. */
  allowDelete?: boolean
  pageSize?: number
}

const props = defineProps<{
  config?: RecordNoteConfig
  tableKind?: 'DW' | 'RT'
  tableId?: number | string | null
  /** Resolved record identity: process instance id (main form) or row id (sub-table row). */
  recordId?: string | null
  /** Current process instance id — TABLE scope always anchors here; falls back to recordId. */
  processInstanceId?: string | null
  functionUnitId?: number | string | null
  readonly?: boolean
}>()

const { t } = useI18n()

// Notes stay writable inside readonly forms (My Requests / completed views):
// reset the injected el-form context so the outer form's `disabled` does not
// propagate to the panel's buttons and upload控件. Write access is governed by
// the `readonly` prop and server-side permission checks instead.
provide(formContextKey, undefined as never)

const scope = computed(() => (props.config?.scope === 'TABLE' ? 'TABLE' : 'RECORD'))
const compact = computed(() => scope.value === 'RECORD')
const panelTitle = computed(() => props.config?.panelTitle || t('recordNote.defaultTitle'))
const allowAttachment = computed(() => props.config?.allowAttachment !== false)
const allowEditOwn = computed(() => props.config?.allowEditOwn !== false)
// Opt-in, unlike every other switch here: a note is a record of what was said, so the
// designer must explicitly enable removal (absent config => no Delete button).
const allowDelete = computed(() => props.config?.allowDelete === true)
const pageSize = computed(() => Math.max(1, Number(props.config?.pageSize) || 5))
const maxFileBytes = computed(() => Math.min(Number(props.config?.maxFileSizeMb) || 10, 10) * 1024 * 1024)

const expanded = ref(false)
const bodyVisible = computed(() => !compact.value || expanded.value)

// Notes never cross process instances. TABLE scope = the hosting table's
// shared stream within the current process (targetId = instance id, or the
// New-Request draft id); RECORD scope = one sub-table row (targetId = row id).
const target = computed<RecordNoteTargetParams | null>(() => {
  if (props.tableId == null || props.tableId === '') return null
  const targetId = scope.value === 'TABLE'
    ? (props.processInstanceId ?? props.recordId)
    : props.recordId
  if (!targetId) return null
  return {
    targetType: scope.value,
    targetId: String(targetId),
    tableKind: props.tableKind ?? 'DW',
    tableId: String(props.tableId),
    functionUnitId: props.functionUnitId ?? null,
    // RECORD scope authorizes against the hosting request — a row id alone identifies no instance.
    processInstanceId: props.processInstanceId ?? null,
  }
})

const unavailable = computed(() => target.value == null)
const canWrite = computed(() => !props.readonly && target.value != null)

// ---- list state ----
const notes = ref<RecordNoteItem[]>([])
const total = ref(0)
const page = ref(0)
const hasNext = ref(false)
const loading = ref(false)

async function load(reset = true) {
  if (!target.value) return
  loading.value = true
  try {
    const result = await listRecordNotes(target.value, reset ? 0 : page.value + 1, pageSize.value)
    if (result) {
      notes.value = reset ? result.content : [...notes.value, ...result.content]
      total.value = result.totalElements
      page.value = result.page
      hasNext.value = result.hasNext
      void hydrateInlineImages(result.content)
    }
  } catch {
    // interceptor already surfaced the error message
  } finally {
    loading.value = false
  }
}

function loadMore() {
  void load(false)
}

// Load as soon as the target resolves — the compact (collapsed) card must show
// the real note count, and the list query is summary-only so it stays cheap.
watch(
  () => [target.value?.targetType, target.value?.targetId],
  () => {
    if (target.value) void load(true)
  },
  { immediate: true },
)

// ---- rich body display ----
// Bodies render directly from the list payload; comments containing inline
// images get their <img> src swapped to authenticated blob URLs (plain <img>
// requests cannot carry the Authorization header).
const displayHtml = reactive<Record<string, string>>({})
const objectUrls: string[] = []

async function hydrateInlineImages(items: RecordNoteItem[]) {
  for (const note of items) {
    if (note.noteType !== 'COMMENT' || !note.bodyHtml) continue
    if (!note.bodyHtml.includes('/api/portal/record-notes/')) continue
    if (displayHtml[note.id]) continue
    try {
      displayHtml[note.id] = await resolveInlineImages(note.bodyHtml)
    } catch {
      /* keep the raw body as fallback */
    }
  }
}

async function resolveInlineImages(html: string): Promise<string> {
  const container = document.createElement('div')
  container.innerHTML = html
  const images = Array.from(container.querySelectorAll('img'))
  await Promise.all(
    images.map(async (img) => {
      const src = img.getAttribute('src') || ''
      const match = src.match(/^\/api\/portal\/record-notes\/([^/]+)\/content$/)
      if (!match) return
      try {
        const url = await fetchRecordNoteBlobUrl(match[1], props.processInstanceId ?? null)
        objectUrls.push(url)
        img.setAttribute('src', url)
      } catch {
        /* keep original src as fallback */
      }
    }),
  )
  return container.innerHTML
}

onBeforeUnmount(() => {
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
})

// ---- editor state ----
const showEditor = ref(false)
const editorHtml = ref('')
const editingNoteId = ref<string | null>(null)
const submitting = ref(false)
const inlineImageIds = ref<string[]>([])
const pickedFiles = ref<File[]>([])
const uploadList = ref<UploadUserFile[]>([])
const editorInstance = shallowRef<any>(null)

const toolbarConfig = {
  toolbarKeys: [
    'bold', 'italic', 'underline', 'through', 'color', 'bgColor',
    '|', 'bulletedList', 'numberedList', 'insertLink', 'uploadImage', 'clearStyle',
  ],
}

const editorConfig = computed(() => ({
  placeholder: t('recordNote.placeholder'),
  MENU_CONF: {
    uploadImage: {
      async customUpload(file: File, insertFn: (url: string, alt?: string) => void) {
        if (!target.value) return
        if (file.size > maxFileBytes.value) {
          ElMessage.warning(t('recordNote.fileTooLarge', { size: props.config?.maxFileSizeMb || 10 }))
          return
        }
        try {
          const created = await uploadInlineImage(target.value, file)
          if (created) {
            inlineImageIds.value.push(created.id)
            insertFn(recordNoteContentUrl(created.id), file.name)
          }
        } catch {
          /* surfaced by interceptor */
        }
      },
    },
  },
}))

function onEditorCreated(editor: any) {
  editorInstance.value = editor
}

function openEditor() {
  editingNoteId.value = null
  editorHtml.value = ''
  inlineImageIds.value = []
  pickedFiles.value = []
  uploadList.value = []
  showEditor.value = true
}

async function startEdit(note: RecordNoteItem) {
  let body = note.bodyHtml
  if (!body) {
    const detail = await getRecordNoteDetail(note.id, props.processInstanceId ?? null)
    body = detail?.bodyHtml || ''
  }
  editingNoteId.value = note.id
  editorHtml.value = body || ''
  inlineImageIds.value = []
  pickedFiles.value = []
  uploadList.value = []
  showEditor.value = true
}

function closeEditor() {
  showEditor.value = false
  editingNoteId.value = null
  editorHtml.value = ''
  pickedFiles.value = []
  uploadList.value = []
  inlineImageIds.value = []
}

onBeforeUnmount(() => {
  if (editorInstance.value) {
    editorInstance.value.destroy()
    editorInstance.value = null
  }
})

function onFilePicked(file: UploadFile, fileList: UploadUserFile[]) {
  const raw = file.raw as File | undefined
  if (!raw) return
  if (raw.size > maxFileBytes.value) {
    ElMessage.warning(t('recordNote.fileTooLarge', { size: props.config?.maxFileSizeMb || 10 }))
    uploadList.value = fileList.filter((f) => f.uid !== file.uid)
    return
  }
  uploadList.value = fileList
  pickedFiles.value = fileList.map((f) => (f as UploadFile).raw as File).filter(Boolean)
}

function onFileRemoved(_file: UploadFile, fileList: UploadUserFile[]) {
  uploadList.value = fileList
  pickedFiles.value = fileList.map((f) => (f as UploadFile).raw as File).filter(Boolean)
}

async function submit() {
  if (!target.value) return
  const editor = editorInstance.value
  const html = editor && !editor.isEmpty() ? editor.getHtml() : ''
  if (!html && !pickedFiles.value.length) {
    ElMessage.warning(t('recordNote.emptySubmit'))
    return
  }
  submitting.value = true
  try {
    if (editingNoteId.value) {
      await updateRecordNote(editingNoteId.value, {
        bodyHtml: html,
        processInstanceId: props.processInstanceId ?? null,
      })
      delete displayHtml[editingNoteId.value]
    } else {
      await createRecordNote(target.value, {
        bodyHtml: html || undefined,
        inlineImageIds: inlineImageIds.value,
        files: allowAttachment.value ? pickedFiles.value : [],
        processInstanceId: props.processInstanceId ?? null,
      })
    }
    closeEditor()
    notifyRecordNoteChanged(props.processInstanceId ?? null)
    await load(true)
  } catch {
    /* surfaced by interceptor */
  } finally {
    submitting.value = false
  }
}

async function removeNote(note: RecordNoteItem) {
  try {
    await ElMessageBox.confirm(t('recordNote.deleteConfirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteRecordNote(note.id, props.processInstanceId ?? null)
    notifyRecordNoteChanged(props.processInstanceId ?? null)
    await load(true)
  } catch {
    /* surfaced by interceptor */
  }
}

function download(noteId: string, fileName: string) {
  void downloadRecordNoteAttachment(noteId, fileName, props.processInstanceId ?? null)
}

// Hibernate @UpdateTimestamp fills updated_at on INSERT too (microseconds after
// created_at), so plain inequality would flag every fresh note as edited.
function isEdited(note: RecordNoteItem): boolean {
  if (!note.updatedAt || !note.createdAt) return false
  const delta = new Date(note.updatedAt).getTime() - new Date(note.createdAt).getTime()
  return Number.isFinite(delta) && delta > 2000
}

function formatTime(value?: string): string {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function formatSize(bytes?: number): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.record-note-field {
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--el-bg-color, #fff);
}

.record-note-field.is-compact {
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

.rn-disabled {
  margin-top: 8px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
}

.rn-editor {
  margin-top: 10px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 4px;
}

.rn-editor-toolbar {
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
}

.rn-editor-body {
  min-height: 120px;
  overflow-y: hidden;
}

.rn-editor-files {
  padding: 4px 8px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
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

.rn-empty {
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
  padding: 8px 0;
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

.rn-item-actions {
  margin-left: auto;
}

.rn-subject {
  margin-top: 4px;
  font-weight: 500;
  font-size: 13px;
}

.rn-body-text {
  margin-top: 4px;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}

.rn-body-text.clickable {
  cursor: pointer;
}

.rn-body-html {
  margin-top: 4px;
  font-size: 13px;
  word-break: break-word;
}

.rn-body-html :deep(img) {
  max-width: 100%;
}

.rn-body-html :deep(ul) {
  list-style: disc;
  padding-left: 1.5em;
  margin: 0.3em 0;
}

.rn-body-html :deep(ol) {
  list-style: decimal;
  padding-left: 1.5em;
  margin: 0.3em 0;
}

.rn-body-html :deep(p) {
  margin: 0.2em 0;
}

.rn-body-html :deep(blockquote) {
  border-left: 3px solid var(--el-border-color, #dcdfe6);
  padding-left: 8px;
  margin: 0.3em 0;
  color: var(--el-text-color-secondary, #909399);
}

.rn-standalone-file {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.rn-chips {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.rn-chip {
  cursor: pointer;
}

.rn-file-size {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  margin-left: 4px;
}

.rn-load-more {
  padding-top: 6px;
  text-align: center;
}
</style>
