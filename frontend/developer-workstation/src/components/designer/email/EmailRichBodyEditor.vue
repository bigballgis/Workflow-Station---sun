<template>
  <div class="email-rich-body-editor">
    <div class="erb-toolbar-row">
      <el-select
        :model-value="''"
        :placeholder="t('emailTemplate.insertVariable')"
        size="small"
        filterable
        class="erb-insert-select"
        @change="onInsert"
      >
        <template v-for="group in variableGroups" :key="group.label">
          <el-option-group :label="groupLabel(group.label)">
            <el-option
              v-for="opt in group.options"
              :key="opt.token"
              :label="opt.label"
              :value="opt.token"
            />
          </el-option-group>
        </template>
      </el-select>
      <span class="erb-hint">{{ t('emailTemplate.insertVariableHint') }}</span>
    </div>

    <div class="erb-editor-shell">
      <Toolbar
        :editor="editorRef"
        :default-config="toolbarConfig"
        mode="default"
        class="erb-toolbar"
      />
      <Editor
        :model-value="modelValue"
        :default-config="editorConfig"
        mode="default"
        class="erb-editor"
        @on-created="onCreated"
        @on-change="onChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { shallowRef, ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import {
  resolveEmailVariableGroupLabel,
  useEmailTemplateVariables,
  type EmailVariableGroup,
} from '@/composables/email/useEmailTemplateVariables'
import { buildEmailRichEditorConfig, buildEmailRichToolbarConfig } from './emailRichEditorConfig'
import { htmlFromVisualEditor } from './emailPreviewShell'

const props = defineProps<{
  modelValue: string
  functionUnitId: number
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const { t } = useI18n()

const editorRef = shallowRef<any>(null)
const variableGroups = ref<EmailVariableGroup[]>([])

const toolbarConfig = buildEmailRichToolbarConfig()
const editorConfig = computed(() =>
  buildEmailRichEditorConfig(props.placeholder || t('emailTemplate.bodyPlaceholder'))
)

const { groups, load } = useEmailTemplateVariables(props.functionUnitId)

function groupLabel(label: string): string {
  return resolveEmailVariableGroupLabel(label, t)
}

function emitSerializedHtml(editor: { getHtml: () => string }): void {
  const html = htmlFromVisualEditor(editor)
  if (html == null) return
  emit('update:modelValue', html)
}

function onCreated(editor: { getHtml: () => string }): void {
  editorRef.value = editor
  void nextTick(() => {
    if (editorRef.value !== editor) return
    emitSerializedHtml(editor)
  })
}

function onChange(editor: { getHtml: () => string }): void {
  emitSerializedHtml(editor)
}

function onInsert(token: string) {
  if (!token) return
  const editor = editorRef.value
  if (!editor) return
  editor.restoreSelection()
  editor.insertText(token)
}

onMounted(async () => {
  await load()
  variableGroups.value = groups.value
})

onBeforeUnmount(() => {
  const editor = editorRef.value
  if (editor) {
    editor.destroy()
    editorRef.value = null
  }
})
</script>

<style scoped lang="scss">
.email-rich-body-editor {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.erb-toolbar-row {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
  padding: 8px 10px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-bottom: none;
  border-radius: 4px 4px 0 0;
}
.erb-insert-select {
  flex: 1 1 180px;
  min-width: 160px;
  max-width: 280px;
}
.erb-hint {
  flex: 1 1 140px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
.erb-editor-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 0 0 4px 4px;
  overflow: hidden;
  background: #fff;
}
.erb-toolbar {
  flex-shrink: 0;
  border-bottom: 1px solid #e4e7ed;
  :deep(.w-e-toolbar) {
    flex-wrap: wrap;
    gap: 2px 4px;
    padding: 6px 8px;
    background: #fafafa;
  }
  :deep(.w-e-bar-item) {
    height: 28px;
  }
  :deep(.w-e-bar-item button) {
    padding: 3px 5px;
  }
  :deep(.w-e-bar-divider) {
    height: 16px;
    margin: 6px 2px;
  }
  :deep(.w-e-select-list) {
    min-width: 72px;
  }
}
.erb-editor {
  flex: 1;
  min-height: 0;
  height: auto !important;
  overflow: hidden;
  :deep(.w-e-text-container),
  :deep(.w-e-scroll) {
    min-height: 0;
    height: 100% !important;
  }
}
</style>
