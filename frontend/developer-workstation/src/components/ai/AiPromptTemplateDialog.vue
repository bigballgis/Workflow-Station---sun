<template>
  <el-dialog
    v-model="innerVisible"
    :title="t('ai.prompts.title')"
    width="920px"
    top="6vh"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    :before-close="handleBeforeClose"
    class="ai-prompts"
  >
    <div
      v-loading="loading"
      class="ai-prompts__body"
    >
      <p class="ai-prompts__hint">
        {{ t('ai.prompts.hint') }}
      </p>

      <el-tabs v-model="activePhase">
        <el-tab-pane
          v-for="phase in AI_PROMPT_PHASES"
          :key="phase"
          :name="phase"
        >
          <template #label>
            <span class="ai-prompts__tab-label">
              {{ t(`ai.prompts.phase.${phase}`) }}
              <span
                v-if="isDirty(phase)"
                class="ai-prompts__dot"
                :title="t('ai.prompts.unsaved')"
              />
            </span>
          </template>

          <div class="ai-prompts__meta">
            <el-tag
              size="small"
              :type="drafts[phase]?.source === 'CUSTOM' ? 'warning' : 'info'"
            >
              {{ drafts[phase]?.source === 'CUSTOM' ? t('ai.prompts.sourceCustom') : t('ai.prompts.sourceBuiltIn') }}
            </el-tag>
            <span
              v-if="drafts[phase]?.source === 'CUSTOM'"
              class="ai-prompts__meta-text"
            >
              {{ t('ai.prompts.lastEdited', {
                user: drafts[phase]?.updatedBy || t('ai.prompts.unknownUser'),
                time: formatTime(drafts[phase]?.updatedAt)
              }) }}
            </span>
            <span class="ai-prompts__meta-spacer" />
            <span class="ai-prompts__meta-text">
              {{ t('ai.prompts.charCount', { count: (drafts[phase]?.content || '').length }) }}
            </span>
          </div>

          <div class="ai-prompts__toolbar">
            <el-button
              size="small"
              :icon="Upload"
              @click="triggerImport(phase)"
            >
              {{ t('ai.prompts.import') }}
            </el-button>
            <el-button
              size="small"
              :icon="Download"
              @click="handleExport(phase)"
            >
              {{ t('ai.prompts.export') }}
            </el-button>
            <el-button
              size="small"
              :icon="RefreshLeft"
              :disabled="!canRevertToDefault(phase)"
              @click="handleResetToDefault(phase)"
            >
              {{ t('ai.prompts.resetToDefault') }}
            </el-button>
            <el-button
              size="small"
              :disabled="!isDirty(phase)"
              @click="handleDiscard(phase)"
            >
              {{ t('ai.prompts.discardChanges') }}
            </el-button>
          </div>

          <el-input
            v-model="drafts[phase].content"
            type="textarea"
            :rows="20"
            resize="vertical"
            spellcheck="false"
            class="ai-prompts__editor"
            :placeholder="t('ai.prompts.editorPlaceholder')"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 隐藏的文件选择器：导入 = 读本地 .txt/.md 文本填进编辑器，保存时才落库 -->
    <input
      ref="fileInputRef"
      type="file"
      accept=".txt,.md,text/plain,text/markdown"
      class="ai-prompts__file-input"
      @change="handleFileChosen"
    >

    <template #footer>
      <div class="ai-prompts__footer">
        <span
          v-if="dirtyPhases.length"
          class="ai-prompts__footer-hint"
        >
          {{ t('ai.prompts.dirtyCount', { count: dirtyPhases.length }) }}
        </span>
        <el-button @click="handleBeforeClose(close)">
          {{ t('ai.prompts.close') }}
        </el-button>
        <el-button
          type="primary"
          :disabled="!dirtyPhases.length || saving"
          :loading="saving"
          @click="handleSaveAll"
        >
          {{ t('ai.prompts.save') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Download, RefreshLeft } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  aiPromptTemplateApi,
  AI_PROMPT_PHASES,
  type AiPromptPhase,
  type AiPromptTemplate
} from '@/api/aiPromptTemplates'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { t } = useI18n()

/** 导入文件大小上限，与后端 content 的 200000 字符上限对齐 */
const MAX_IMPORT_BYTES = 200_000

const loading = ref(false)
const saving = ref(false)
const activePhase = ref<AiPromptPhase>('REQUIREMENTS')
const fileInputRef = ref<HTMLInputElement | null>(null)
const importTargetPhase = ref<AiPromptPhase>('REQUIREMENTS')

/** 编辑中的草稿（含来源与内置默认值） */
const drafts = reactive<Record<AiPromptPhase, AiPromptTemplate>>(emptyDrafts())
/** 服务端当前值，用于判断脏与"放弃修改" */
const saved = reactive<Record<AiPromptPhase, string>>({
  REQUIREMENTS: '',
  DESIGN: '',
  GENERATION: ''
})

const innerVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

const dirtyPhases = computed(() => AI_PROMPT_PHASES.filter(isDirty))

watch(() => props.visible, (visible) => {
  if (visible) {
    load()
  }
})

function emptyDrafts(): Record<AiPromptPhase, AiPromptTemplate> {
  const blank = (phase: AiPromptPhase): AiPromptTemplate => ({
    phase,
    content: '',
    source: 'BUILT_IN',
    defaultContent: '',
    updatedBy: null,
    updatedAt: null
  })
  return {
    REQUIREMENTS: blank('REQUIREMENTS'),
    DESIGN: blank('DESIGN'),
    GENERATION: blank('GENERATION')
  }
}

function isDirty(phase: AiPromptPhase): boolean {
  return drafts[phase].content !== saved[phase]
}

/** 只有"内容与内置默认值不同"时还原默认才有意义 */
function canRevertToDefault(phase: AiPromptPhase): boolean {
  const draft = drafts[phase]
  return draft.source === 'CUSTOM' || draft.content !== draft.defaultContent
}

async function load() {
  loading.value = true
  try {
    const res = await aiPromptTemplateApi.list()
    for (const tpl of res.data || []) {
      drafts[tpl.phase] = { ...tpl }
      saved[tpl.phase] = tpl.content
    }
  } catch {
    ElMessage.error(t('ai.prompts.loadFailed'))
  } finally {
    loading.value = false
  }
}

function close() {
  innerVisible.value = false
}

/** 有未保存改动时先确认；确认后丢弃草稿关闭 */
async function handleBeforeClose(done: () => void) {
  if (!dirtyPhases.value.length) {
    done()
    return
  }
  try {
    await ElMessageBox.confirm(
      t('ai.prompts.discardConfirm', { count: dirtyPhases.value.length }),
      t('ai.prompts.discardConfirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  for (const phase of AI_PROMPT_PHASES) {
    drafts[phase].content = saved[phase]
  }
  done()
}

function triggerImport(phase: AiPromptPhase) {
  importTargetPhase.value = phase
  fileInputRef.value?.click()
}

async function handleFileChosen(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 同一个文件连续导入两次也要能触发 change，所以读完立刻清空
  input.value = ''
  if (!file) return

  if (file.size > MAX_IMPORT_BYTES) {
    ElMessage.error(t('ai.prompts.importTooLarge', { max: MAX_IMPORT_BYTES }))
    return
  }
  let text: string
  try {
    text = await file.text()
  } catch {
    ElMessage.error(t('ai.prompts.importFailed'))
    return
  }
  if (!text.trim()) {
    ElMessage.error(t('ai.prompts.importEmpty'))
    return
  }
  const phase = importTargetPhase.value
  drafts[phase].content = text
  activePhase.value = phase
  ElMessage.success(t('ai.prompts.imported', { file: file.name }))
}

function handleExport(phase: AiPromptPhase) {
  const content = drafts[phase].content
  if (!content) {
    ElMessage.warning(t('ai.prompts.exportEmpty'))
    return
  }
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${phase.toLowerCase()}-${dayjs().format('YYYYMMDD-HHmmss')}.txt`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/** 还原默认 = 删掉库里的覆盖行；有覆盖行才需要调后端，否则只是把编辑器内容拨回默认值 */
async function handleResetToDefault(phase: AiPromptPhase) {
  try {
    await ElMessageBox.confirm(
      t('ai.prompts.resetConfirm', { phase: t(`ai.prompts.phase.${phase}`) }),
      t('ai.prompts.resetConfirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  if (drafts[phase].source !== 'CUSTOM') {
    drafts[phase].content = drafts[phase].defaultContent
    return
  }
  saving.value = true
  try {
    const res = await aiPromptTemplateApi.reset(phase)
    drafts[phase] = { ...res.data }
    saved[phase] = res.data.content
    ElMessage.success(t('ai.prompts.resetSuccess'))
  } catch {
    ElMessage.error(t('ai.prompts.resetFailed'))
  } finally {
    saving.value = false
  }
}

function handleDiscard(phase: AiPromptPhase) {
  drafts[phase].content = saved[phase]
}

async function handleSaveAll() {
  const targets = dirtyPhases.value
  if (!targets.length || saving.value) return

  const blank = targets.find(phase => !drafts[phase].content.trim())
  if (blank) {
    ElMessage.error(t('ai.prompts.saveEmpty', { phase: t(`ai.prompts.phase.${blank}`) }))
    activePhase.value = blank
    return
  }

  saving.value = true
  try {
    for (const phase of targets) {
      const res = await aiPromptTemplateApi.save(phase, drafts[phase].content)
      drafts[phase] = { ...res.data }
      saved[phase] = res.data.content
    }
    ElMessage.success(t('ai.prompts.saveSuccess', { count: targets.length }))
  } catch {
    // 逐个保存，失败点之前的已生效——重新拉一次让界面与服务端一致，不要停在半真半假的状态
    ElMessage.error(t('ai.prompts.saveFailed'))
    await load()
  } finally {
    saving.value = false
  }
}

function formatTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.ai-prompts__body {
  min-height: 200px;
}

.ai-prompts__hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.6;
}

.ai-prompts__tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.ai-prompts__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: ai.$ai-red;
}

.ai-prompts__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-prompts__meta-spacer {
  flex: 1;
}

.ai-prompts__meta-text {
  font-size: 12px;
  color: #6b7280;
}

.ai-prompts__toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-prompts__editor :deep(.el-textarea__inner) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.ai-prompts__file-input {
  display: none;
}

.ai-prompts__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.ai-prompts__footer-hint {
  margin-right: auto;
  font-size: 12px;
  color: #6b7280;
}
</style>
