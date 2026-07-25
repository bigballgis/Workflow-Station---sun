<template>
  <div class="document-panel">
    <el-tabs
      v-model="activeTab"
      @tab-change="handleTabChange"
    >
      <el-tab-pane
        :label="t('ai.doc.requirements')"
        name="REQUIREMENTS"
      >
        <div class="document-panel__body">
          <div class="document-panel__content">
            <template v-if="requirementsContent">
              <div class="document-panel__toolbar">
                <ViewModeToggle v-model="viewMode" />
                <div class="document-panel__toolbar-actions">
                  <el-button
                    v-if="isLatestVersion && !isEditing"
                    size="small"
                    @click="enterEditMode"
                  >
                    {{ t('ai.doc.edit') }}
                  </el-button>
                  <el-button
                    v-if="requirementsVersions.length"
                    size="small"
                    text
                    @click="reqSidebarOpen = !reqSidebarOpen"
                  >
                    {{ t('ai.doc.versionHistory') }}
                  </el-button>
                </div>
              </div>
              <div class="document-panel__doc">
                <XmlTreeView
                  v-show="viewMode === 'xml'"
                  :content="requirementsContent"
                />
                <MarkdownRenderer
                  v-show="viewMode === 'markdown'"
                  :content="requirementsContent"
                />
              </div>
            </template>
            <el-empty
              v-else
              :description="t('ai.doc.noRequirements')"
              :image-size="60"
            />
          </div>
          <div
            v-if="reqSidebarOpen && requirementsVersions.length"
            class="document-panel__sidebar"
          >
            <div class="document-panel__sidebar-title">
              {{ t('ai.doc.versionHistory') }}
            </div>
            <div
              v-for="v in requirementsVersions"
              :key="v.version"
              class="document-panel__version-item"
              :class="{ 'is-active': requirementsSelectedVersion === v.version }"
              @click="handleVersionClick('REQUIREMENTS', v.version)"
            >
              <span class="document-panel__version-num">v{{ v.version }}</span>
              <span class="document-panel__version-time">{{ formatTime(v.createdAt) }}</span>
              <span
                v-if="v.summary"
                class="document-panel__version-summary"
              >{{ v.summary }}</span>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane
        :label="t('ai.doc.design')"
        name="DESIGN"
      >
        <div class="document-panel__body">
          <div class="document-panel__content">
            <template v-if="designContent">
              <div class="document-panel__toolbar">
                <ViewModeToggle v-model="viewMode" />
                <div class="document-panel__toolbar-actions">
                  <el-button
                    v-if="isLatestVersion && !isEditing"
                    size="small"
                    @click="enterEditMode"
                  >
                    {{ t('ai.doc.edit') }}
                  </el-button>
                  <el-button
                    v-if="designVersions.length"
                    size="small"
                    text
                    @click="designSidebarOpen = !designSidebarOpen"
                  >
                    {{ t('ai.doc.versionHistory') }}
                  </el-button>
                </div>
              </div>
              <div class="document-panel__doc">
                <XmlTreeView
                  v-show="viewMode === 'xml'"
                  :content="designContent"
                />
                <MarkdownRenderer
                  v-show="viewMode === 'markdown'"
                  :content="designContent"
                />
              </div>
            </template>
            <el-empty
              v-else
              :description="t('ai.doc.noDesign')"
              :image-size="60"
            />
          </div>
          <div
            v-if="designSidebarOpen && designVersions.length"
            class="document-panel__sidebar"
          >
            <div class="document-panel__sidebar-title">
              {{ t('ai.doc.versionHistory') }}
            </div>
            <div
              v-for="v in designVersions"
              :key="v.version"
              class="document-panel__version-item"
              :class="{ 'is-active': designSelectedVersion === v.version }"
              @click="handleVersionClick('DESIGN', v.version)"
            >
              <span class="document-panel__version-num">v{{ v.version }}</span>
              <span class="document-panel__version-time">{{ formatTime(v.createdAt) }}</span>
              <span
                v-if="v.summary"
                class="document-panel__version-summary"
              >{{ v.summary }}</span>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="isEditing"
      :title="editDialogTitle"
      width="80vw"
      modal
      :close-on-click-modal="false"
      append-to-body
      custom-class="edit-dialog"
      :before-close="handleDialogBeforeClose"
    >
      <DocumentEditor
        v-if="isEditing && activeTab === 'REQUIREMENTS'"
        ref="reqEditorRef"
        :content="requirementsContent"
        :function-unit-id="functionUnitId"
        document-type="REQUIREMENTS"
        @saved="handleSaved"
        @cancel="handleCancelEdit"
      />
      <DocumentEditor
        v-if="isEditing && activeTab === 'DESIGN'"
        ref="designEditorRef"
        :content="designContent"
        :function-unit-id="functionUnitId"
        document-type="DESIGN"
        @saved="handleSaved"
        @cancel="handleCancelEdit"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { aiGenerationApi } from '@/api/aiGeneration'
import ViewModeToggle from './ViewModeToggle.vue'
import XmlTreeView from './XmlTreeView.vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import DocumentEditor from './DocumentEditor.vue'
import type { AiDocument, AiDocumentType, ViewMode } from '@/types/aiGeneration'

const { t } = useI18n()

const props = defineProps<{
  functionUnitId: number
}>()

const activeTab = ref<AiDocumentType>('REQUIREMENTS')
const requirementsVersions = ref<AiDocument[]>([])
const designVersions = ref<AiDocument[]>([])
const requirementsContent = ref('')
const designContent = ref('')
const requirementsSelectedVersion = ref<number | null>(null)
const designSelectedVersion = ref<number | null>(null)
const loading = ref(false)

// New state for view mode and editing
const viewMode = ref<ViewMode>('xml')
const isEditing = ref(false)
const savedViewMode = ref<ViewMode | null>(null)

// Sidebar toggle state for version history
const reqSidebarOpen = ref(false)
const designSidebarOpen = ref(false)

// Editor refs
const reqEditorRef = ref<InstanceType<typeof DocumentEditor> | null>(null)
const designEditorRef = ref<InstanceType<typeof DocumentEditor> | null>(null)

const currentEditorRef = computed(() =>
  activeTab.value === 'REQUIREMENTS' ? reqEditorRef.value : designEditorRef.value
)

const isLatestVersion = computed(() => {
  if (activeTab.value === 'REQUIREMENTS') {
    return requirementsVersions.value.length > 0 &&
      requirementsSelectedVersion.value === requirementsVersions.value[0].version
  }
  return designVersions.value.length > 0 &&
    designSelectedVersion.value === designVersions.value[0].version
})

function formatTime(time: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm') : ''
}

async function loadVersions(docType: AiDocumentType) {
  if (!props.functionUnitId || props.functionUnitId <= 0) return
  try {
    loading.value = true
    const res = await aiGenerationApi.getDocumentVersions(props.functionUnitId, docType)
    const versions = res.data || []
    if (docType === 'REQUIREMENTS') {
      requirementsVersions.value = versions
      if (versions.length > 0) {
        requirementsContent.value = versions[0].content
        requirementsSelectedVersion.value = versions[0].version
      } else {
        requirementsContent.value = ''
        requirementsSelectedVersion.value = null
      }
    } else {
      designVersions.value = versions
      if (versions.length > 0) {
        designContent.value = versions[0].content
        designSelectedVersion.value = versions[0].version
      } else {
        designContent.value = ''
        designSelectedVersion.value = null
      }
    }
  } catch {
    if (docType === 'REQUIREMENTS') {
      requirementsContent.value = ''
      requirementsSelectedVersion.value = null
    } else {
      designContent.value = ''
      designSelectedVersion.value = null
    }
  } finally {
    loading.value = false
  }
}

async function selectVersion(docType: AiDocumentType, version: number) {
  try {
    const res = await aiGenerationApi.getDocumentByVersion(props.functionUnitId, docType, version)
    const content = res.data?.content || ''
    if (docType === 'REQUIREMENTS') {
      requirementsContent.value = content
      requirementsSelectedVersion.value = version
    } else {
      designContent.value = content
      designSelectedVersion.value = version
    }
  } catch {
    // keep current content on error
  }
}

/** Check if editing is dirty and confirm discard */
async function confirmIfDirty(): Promise<boolean> {
  if (!isEditing.value) return true
  const editor = currentEditorRef.value
  if (!editor?.isDirty) return true
  try {
    await ElMessageBox.confirm(t('ai.doc.unsavedConfirm'), { type: 'warning' })
    exitEditMode()
    return true
  } catch {
    return false
  }
}

async function handleTabChange(_tab: string | number) {
  // Tab already changed by v-model, but we need to guard if editing
  if (isEditing.value && currentEditorRef.value?.isDirty) {
    try {
      await ElMessageBox.confirm(t('ai.doc.unsavedConfirm'), { type: 'warning' })
      exitEditMode()
    } catch {
      // Revert tab change - restore previous tab
      activeTab.value = activeTab.value === 'REQUIREMENTS' ? 'DESIGN' : 'REQUIREMENTS'
    }
  } else if (isEditing.value) {
    exitEditMode()
  }
}

async function handleVersionClick(docType: AiDocumentType, version: number) {
  if (isEditing.value && currentEditorRef.value?.isDirty) {
    try {
      await ElMessageBox.confirm(t('ai.doc.unsavedConfirm'), { type: 'warning' })
      exitEditMode()
      selectVersion(docType, version)
    } catch {
      // stay in edit mode
    }
  } else {
    if (isEditing.value) exitEditMode()
    selectVersion(docType, version)
  }
}

function enterEditMode() {
  savedViewMode.value = viewMode.value
  isEditing.value = true
}

function exitEditMode() {
  isEditing.value = false
  if (savedViewMode.value) {
    viewMode.value = savedViewMode.value
    savedViewMode.value = null
  }
}

const editDialogTitle = computed(() => {
  const docType = activeTab.value === 'REQUIREMENTS'
    ? t('ai.doc.requirements')
    : t('ai.doc.design')
  return `${docType} - ${t('ai.doc.edit')}`
})

async function handleDialogBeforeClose(done: () => void) {
  const editor = currentEditorRef.value
  if (editor?.isDirty) {
    try {
      await ElMessageBox.confirm(t('ai.doc.unsavedConfirm'), { type: 'warning' })
      exitEditMode()
      done()
    } catch {
      // user cancelled, keep dialog open
    }
  } else {
    exitEditMode()
    done()
  }
}

async function handleSaved(doc: AiDocument) {
  exitEditMode()
  await loadVersions(doc.documentType)
  if (doc.documentType === 'REQUIREMENTS') {
    requirementsSelectedVersion.value = doc.version
    requirementsContent.value = doc.content
  } else {
    designSelectedVersion.value = doc.version
    designContent.value = doc.content
  }
}

function handleCancelEdit() {
  exitEditMode()
}

/** Exposed for parent: can the panel be closed? */
async function canClose(): Promise<boolean> {
  return confirmIfDirty()
}

watch(() => props.functionUnitId, (id) => {
  if (!id || id <= 0) return
  loadVersions('REQUIREMENTS')
  loadVersions('DESIGN')
}, { immediate: true })

/**
 * 刷新指定类型的文档版本列表，并切换到该 tab。
 * 由父组件在收到 document SSE 事件时调用。
 */
async function refreshDocType(docType: AiDocumentType) {
  // If editing same doc type, notify editor of new AI version
  if (isEditing.value && activeTab.value === docType) {
    const editor = currentEditorRef.value
    editor?.notifyNewAiVersion()
  }
  await loadVersions(docType)
  activeTab.value = docType
}

defineExpose({
  refreshDocType,
  canClose
})
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.document-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 12px;
  background: ai.$ai-paper;

  :deep(.el-tabs__item) {
    font-size: 13px;
    color: ai.$ai-graphite;

    &.is-active {
      color: ai.$ai-ink;
      font-weight: 600;
    }
  }
}

.document-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  margin-bottom: 4px;
  position: sticky;
  top: 0;
  z-index: 2;
  background: #fff;
}

.document-panel__content {
  flex: 1;
  overflow-y: auto;
  min-height: 120px;
}

.document-panel__body {
  display: flex;
  flex-direction: row;
  flex: 1;
  overflow: hidden;
  min-height: 120px;
}

.document-panel__doc {
  padding: 8px;
}

.document-panel__sidebar {
  width: 220px;
  border-left: 1px solid ai.$ai-hairline;
  padding: 8px;
  overflow-y: auto;
  flex-shrink: 0;
}

.document-panel__sidebar-title {
  @include ai.ai-eyebrow;
  margin-bottom: 8px;
  padding: 0 8px;
}

.document-panel__toolbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.document-panel__version-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-left: 2px solid transparent;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.2s, border-color 0.2s;

  &:hover {
    background: ai.$ai-mist;
  }

  &.is-active {
    background: ai.$ai-red-soft;
    border-left-color: ai.$ai-red;

    .document-panel__version-num {
      color: ai.$ai-red;
    }
  }
}

.document-panel__version-num {
  @include ai.ai-mono-num;
  font-weight: 600;
  min-width: 30px;
  color: ai.$ai-ink;
}

.document-panel__version-time {
  @include ai.ai-mono-num;
  font-size: 11px;
  color: ai.$ai-faint;
}

.document-panel__version-summary {
  color: ai.$ai-graphite;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
</style>


<style lang="scss">
.edit-dialog .el-dialog__body {
  height: calc(80vh - 54px);
  overflow-y: auto;
}
</style>
