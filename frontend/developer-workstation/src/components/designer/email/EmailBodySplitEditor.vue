<template>
  <div class="email-body-split" data-testid="email-body-split">
    <div class="ebs-mode-row">
      <el-radio-group :key="mode" :model-value="mode" size="small" @update:model-value="onModeChange">
        <el-radio-button value="visual" data-testid="email-body-mode-visual">
          {{ t('emailTemplate.bodyModeVisual') }}
        </el-radio-button>
        <el-radio-button value="html" data-testid="email-body-mode-html">
          {{ t('emailTemplate.bodyModeHtml') }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <div class="ebs-panes">
      <div class="ebs-editor">
        <EmailRichBodyEditor
          v-if="mode === 'visual'"
          :model-value="modelValue"
          :function-unit-id="functionUnitId"
          @update:model-value="emit('update:modelValue', $event)"
        />
        <div v-else class="ebs-html-pane">
          <div class="erb-toolbar-row">
            <el-select
              :model-value="''"
              :placeholder="t('emailTemplate.insertVariable')"
              size="small"
              filterable
              class="erb-insert-select"
              @change="insertHtmlToken"
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
          <el-input
            ref="htmlInputRef"
            :model-value="modelValue"
            type="textarea"
            :rows="14"
            class="ebs-html-input"
            :placeholder="t('emailTemplate.htmlPlaceholder')"
            @update:model-value="emit('update:modelValue', $event)"
          />
        </div>
      </div>

      <div class="ebs-preview">
        <div class="ebs-preview-label">{{ t('emailTemplate.emailPreview') }}</div>
        <iframe
          class="ebs-preview-frame"
          data-testid="email-body-preview-iframe"
          :key="previewDoc"
          :title="t('emailTemplate.emailPreview')"
          sandbox=""
          referrerpolicy="no-referrer"
          :srcdoc="previewDoc"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, withDefaults } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import EmailRichBodyEditor from './EmailRichBodyEditor.vue'
import {
  resolveEmailVariableGroupLabel,
  useEmailTemplateVariables,
  type EmailVariableGroup,
} from '@/composables/email/useEmailTemplateVariables'
import {
  insertAtCursor,
  isSwitchToVisual,
  parseEmailBodyEditorMode,
  wrapEmailPreviewDocument,
  type EmailBodyEditorMode,
} from './emailPreviewShell'

const props = withDefaults(
  defineProps<{
    modelValue: string
    functionUnitId: number
    mode?: EmailBodyEditorMode
  }>(),
  { mode: 'visual' },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:mode', value: EmailBodyEditorMode): void
}>()

const { t } = useI18n()
const mode = computed(() => parseEmailBodyEditorMode(props.mode))
const htmlInputRef = ref<{ textarea?: HTMLTextAreaElement } | null>(null)
const variableGroups = ref<EmailVariableGroup[]>([])
const { groups, load } = useEmailTemplateVariables(props.functionUnitId)

const previewDoc = computed(() => wrapEmailPreviewDocument(props.modelValue || ''))

function groupLabel(label: string): string {
  return resolveEmailVariableGroupLabel(label, t)
}

async function onModeChange(next: string) {
  const to = parseEmailBodyEditorMode(next)
  if (to === mode.value) return
  if (isSwitchToVisual(mode.value, to)) {
    try {
      await ElMessageBox.confirm(
        t('emailTemplate.switchToVisualConfirm'),
        t('common.confirm'),
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  emit('update:mode', to)
}

function insertHtmlToken(token: string) {
  if (!token) return
  const el = htmlInputRef.value?.textarea
  if (!el) {
    emit('update:modelValue', `${props.modelValue || ''}${token}`)
    return
  }
  const next = insertAtCursor(el.value, token, el.selectionStart, el.selectionEnd)
  emit('update:modelValue', next)
}

onMounted(async () => {
  await load()
  variableGroups.value = groups.value
})
</script>

<style scoped lang="scss">
.email-body-split {
  width: 100%;
}
.ebs-mode-row {
  margin-bottom: 8px;
}
.ebs-panes {
  display: flex;
  gap: 12px;
  align-items: stretch;
  height: 520px;
}
.ebs-editor,
.ebs-preview {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.ebs-editor > .email-rich-body-editor,
.ebs-editor > .ebs-html-pane {
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
}
.ebs-html-pane {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.erb-toolbar-row {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
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
.ebs-html-input {
  flex: 1;
  min-height: 0;
  :deep(.el-textarea) {
    height: 100%;
  }
  :deep(textarea) {
    height: 100% !important;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 13px;
    border: none;
    border-radius: 0;
    resize: none;
  }
}
.ebs-preview {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  overflow: hidden;
}
.ebs-preview-label {
  flex-shrink: 0;
  padding: 8px 10px;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
}
.ebs-preview-frame {
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  border: 0;
  background: #fff;
}
</style>
