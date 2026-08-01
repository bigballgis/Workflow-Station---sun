<template>
  <div class="chat-dialog">
    <!-- Top: Phase Indicator -->
    <PhaseIndicator
      :current-phase="phase"
      :completed-phases="completedPhases"
      :busy="isStreaming"
    />

    <!-- Task 16.2: Multi-step generation progress ticker -->
    <div
      v-if="isStreaming && generationStep > 0 && generationStep < 6"
      class="chat-dialog__ticker"
    >
      <span class="chat-dialog__ticker-step">{{ String(generationStep).padStart(2, '0') }}/06</span>
      <span class="chat-dialog__ticker-text">{{ progressText }}</span>
      <span class="chat-dialog__ticker-bar"><i /></span>
    </div>

    <!-- Middle: Message List -->
    <div class="chat-dialog__messages">
      <!-- Template selection when no messages -->
      <div
        v-if="messages.length === 0 && !isStreaming"
        class="chat-dialog__templates"
      >
        <p class="chat-dialog__templates-eyebrow">
          BLUEPRINTS
        </p>
        <p class="chat-dialog__templates-title">
          {{ t('ai.template.selectTitle') }}
        </p>
        <div class="chat-dialog__template-grid">
          <button
            v-for="(tpl, idx) in templates"
            :key="tpl.id"
            type="button"
            class="chat-dialog__template-card"
            @click="applyTemplate(tpl)"
          >
            <span class="chat-dialog__template-idx">{{ String(idx + 1).padStart(2, '0') }}</span>
            <span class="chat-dialog__template-main">
              <span class="chat-dialog__template-header">
                <el-icon :size="14"><component :is="tpl.icon" /></el-icon>
                <span>{{ t(tpl.nameKey) }}</span>
              </span>
              <span class="chat-dialog__template-desc">
                {{ t(tpl.descriptionKey) }}
              </span>
            </span>
          </button>
        </div>
      </div>

      <!-- Task 16.4: Draft restoration alert -->
      <el-alert
        v-if="hasDraft"
        type="info"
        :closable="false"
        class="chat-dialog__draft-alert"
      >
        {{ t('ai.draft.found') }}
        <el-button
          size="small"
          type="primary"
          @click="restoreDraft"
        >
          {{ t('ai.draft.restore') }}
        </el-button>
        <el-button
          size="small"
          @click="dismissDraft"
        >
          {{ t('ai.draft.dismiss') }}
        </el-button>
      </el-alert>

      <!-- Task 17.1: Generation draft restoration alert -->
      <el-alert
        v-if="hasGenerationDraft"
        type="info"
        :closable="false"
        class="chat-dialog__draft-alert"
      >
        {{ t('ai.draft.found') }}
        <el-button
          size="small"
          type="primary"
          @click="restoreGenerationDraft"
        >
          {{ t('ai.draft.restore') }}
        </el-button>
        <el-button
          size="small"
          @click="dismissGenerationDraft"
        >
          {{ t('ai.draft.dismiss') }}
        </el-button>
      </el-alert>

      <ChatMessage
        v-for="msg in messages"
        :key="msg.id"
        :message="msg"
      />

      <!-- Inline document viewers -->
      <InlineDocumentViewer
        v-for="doc in inlineDocuments"
        :key="doc.id"
        :document-type="doc.documentType"
        :content="doc.content"
      />

      <!-- Streaming message (AI is responding) -->
      <ChatMessage
        v-if="isStreaming && streamingContent"
        :message="streamingMessage"
        :is-streaming="true"
      />

      <!-- Thinking indicator (waiting for first response from AI) -->
      <div
        v-if="isStreaming && !streamingContent"
        class="chat-dialog__thinking"
      >
        <span class="chat-dialog__thinking-avatar">AI</span>
        <div class="chat-dialog__thinking-bubble">
          <span class="chat-dialog__thinking-dots">
            <span class="dot">●</span>
            <span class="dot">●</span>
            <span class="dot">●</span>
          </span>
          <span class="chat-dialog__thinking-text">{{ t('ai.chat.thinking') }}</span>
        </div>
      </div>

      <!-- Generation Preview -->
      <div
        v-if="previewData && generatedData"
        class="chat-dialog__preview"
      >
        <GenerationPreview
          :preview-data="previewData"
          :generated-data="generatedData"
          :is-generation-complete="isGenerationComplete"
          :is-streaming="isStreaming"
          :mode="props.mode"
          :diff-result="diffResult"
          :apply-state="applyState"
          @apply="handleApply"
          @regenerate="handleRegenerate"
        />
      </div>

      <!-- Task 17.3: Undo button with countdown -->
      <div
        v-if="showUndoButton"
        class="chat-dialog__undo"
      >
        <el-button
          type="warning"
          size="small"
          @click="handleUndo"
        >
          {{ t('ai.undo.button') }} ({{ undoCountdown }}s)
        </el-button>
      </div>

      <!-- Validation Errors -->
      <div
        v-if="validationErrors.length"
        class="chat-dialog__validation"
      >
        <el-alert
          :title="t('ai.chat.validationFailed')"
          type="error"
          :closable="false"
          show-icon
        >
          <ul class="chat-dialog__error-list">
            <li
              v-for="(err, idx) in validationErrors"
              :key="idx"
            >
              <span class="chat-dialog__error-type">[{{ err.errorType }}]</span>
              <span class="chat-dialog__error-path">{{ err.fieldPath }}</span>
              {{ err.description }}
            </li>
          </ul>
        </el-alert>
      </div>

      <!-- Validation Warnings -->
      <div
        v-if="validationWarnings.length"
        class="chat-dialog__validation"
      >
        <el-alert
          :title="t('ai.chat.validationWarnings')"
          type="warning"
          :closable="true"
          show-icon
          @close="validationWarnings = []"
        >
          <ul class="chat-dialog__error-list">
            <li
              v-for="(warn, idx) in validationWarnings"
              :key="idx"
            >
              <span class="chat-dialog__error-type chat-dialog__error-type--warning">[{{ warn.errorType }}]</span>
              <span class="chat-dialog__error-path">{{ warn.fieldPath }}</span>
              {{ warn.description }}
            </li>
          </ul>
        </el-alert>
      </div>

      <!-- Error Alert with Retry -->
      <div
        v-if="error"
        class="chat-dialog__error"
      >
        <el-alert
          :title="errorMessage"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #default>
            <el-button
              v-if="canRetry"
              size="small"
              type="primary"
              @click="handleRetry"
            >
              {{ t('ai.chat.retry') }}
            </el-button>
            <!-- 过期的 AMToken 重试多少次都是同一个 401：只给"重新登录"这一条出路 -->
            <el-button
              v-if="needsReauth"
              size="small"
              type="primary"
              @click="handleSignInAgain"
            >
              {{ t('ai.chat.signInAgain') }}
            </el-button>
          </template>
        </el-alert>
      </div>

      <!-- Task 16.4: AI webhook Degradation Panel -->
      <div
        v-if="degradationInfo"
        class="chat-dialog__degradation"
      >
        <el-alert
          type="error"
          :closable="false"
          show-icon
        >
          <template #title>
            {{ t('ai.degradation.title') }}
          </template>
          <template #default>
            <p
              v-if="degradationInfo.lastSuccessTime"
              class="chat-dialog__degradation-time"
            >
              {{ t('ai.degradation.lastSuccess', { time: formatRelativeTime(degradationInfo.lastSuccessTime) }) }}
            </p>
            <div class="chat-dialog__degradation-actions">
              <el-button
                size="small"
                @click="saveDraftToLocalStorage"
              >
                {{ t('ai.degradation.saveDraft') }}
              </el-button>
              <el-button
                size="small"
                type="primary"
                @click="navigateToManualCreate"
              >
                {{ t('ai.degradation.manualCreate') }}
              </el-button>
            </div>
          </template>
        </el-alert>
      </div>

      <!-- Phase Complete: Enter Next Phase Button -->
      <div
        v-if="showPhaseCompleteBtn"
        class="chat-dialog__phase-action"
      >
        <el-button
          type="success"
          @click="handleNextPhase"
        >
          {{ t('ai.chat.nextPhase') }}
        </el-button>
      </div>

      <!-- Scroll anchor -->
      <div ref="scrollAnchorRef" />
    </div>

    <!-- Task 16.3: Regenerate scope selector (MODIFY mode only) -->
    <div
      v-if="props.mode === 'MODIFY'"
      class="chat-dialog__scope"
    >
      <el-button
        text
        size="small"
        @click="showScopeSelector = !showScopeSelector"
      >
        {{ t('ai.chat.regenerateScope') }}
        <el-icon><ArrowDown v-if="!showScopeSelector" /><ArrowUp v-else /></el-icon>
      </el-button>
      <el-checkbox-group
        v-if="showScopeSelector"
        v-model="selectedScopes"
        class="chat-dialog__scope-group"
      >
        <el-checkbox
          v-for="scope in SCOPE_OPTIONS"
          :key="scope"
          :value="scope"
          size="small"
        >
          {{ t(`ai.scope.${scopeKeyMap[scope]}`) }}
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <!-- Bottom: Input Box + Send Button -->
    <div class="chat-dialog__input-area">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        :placeholder="inputPlaceholder"
        :disabled="false"
        resize="none"
        @keydown.enter.exact.prevent="handleSend"
      />
      <el-button
        v-if="isStreaming"
        type="danger"
        plain
        @click="handleStop"
      >
        {{ t('ai.chat.stop') }}
      </el-button>
      <el-button
        v-else
        type="primary"
        :icon="Promotion"
        :disabled="isSendDisabled"
        @click="handleSend"
      >
        {{ t('ai.chat.send') }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { Promotion, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import PhaseIndicator from './PhaseIndicator.vue'
import ChatMessage from './ChatMessage.vue'
import GenerationPreview from './GenerationPreview.vue'
import InlineDocumentViewer from './InlineDocumentViewer.vue'
import { useAiChat } from '@/composables/useAiChat'
import { clearDraft as clearGenerationDraft } from '@/composables/useAiChat'
import { useAiTemplates } from '@/composables/useAiTemplates'
import type {
  AiPhase,
  AiMode,
  AiMessage,
  AiGeneratedData,
  GenerationPreviewData,
  AiValidationError,
  InlineDocument,
  AiDocumentType,
  DiffResult
} from '@/types/aiGeneration'
import { computeDiff } from '@/types/aiGeneration'
import type { AiTemplate } from '@/composables/useAiTemplates'
import { functionUnitApi } from '@/api/functionUnit'
import { redirectToUnifiedLogin, setSsoReturnPath } from '@/utils/sso'
import { useChatDialogScope } from '@/composables/chatDialog/useChatDialogScope'
import { useChatDialogPreview } from '@/composables/chatDialog/useChatDialogPreview'
import { useChatDialogUndo } from '@/composables/chatDialog/useChatDialogUndo'
import { useChatDialogDraft, type RestoredGenerationDraft } from '@/composables/chatDialog/useChatDialogDraft'
import { useChatDialogMessagesHeight } from '@/composables/chatDialog/useChatDialogMessagesHeight'

const props = withDefaults(defineProps<{
  functionUnitId: number
  sessionId: string
  phase: AiPhase
  mode: AiMode
  completedPhases?: AiPhase[]
  initialMessages?: AiMessage[]
}>(), {
  completedPhases: () => [],
  initialMessages: () => []
})

const emit = defineEmits<{
  phaseComplete: [phase: AiPhase]
  apply: [data: AiGeneratedData]
  regenerate: []
  sendMessage: []
  document: [type: string, content: string]
  sessionCreated: [sessionId: string]
}>()

// Chat composable
const {
  messages,
  isStreaming,
  streamingContent,
  error,
  errorCode,
  canRetry,
  partialGeneratedData: _partialGeneratedData,
  isGenerationComplete,
  generationStep,
  degradationInfo,
  sendMessage,
  retry,
  cancel,
  onDocument,
  onPhaseComplete,
  onGeneratedData,
  onValidationWarning,
  onSession,
  setMessages,
  clearCurrentDraft
} = useAiChat()

// i18n
const { t } = useI18n()

// Templates
const { templates } = useAiTemplates()

// Local state
const inputText = ref('')
const scrollAnchorRef = ref<HTMLElement | null>(null)
const showPhaseCompleteBtn = ref(false)
const generatedData = ref<AiGeneratedData | null>(null)
const previewData = ref<GenerationPreviewData | null>(null)
const validationErrors = ref<AiValidationError[]>([])
const validationWarnings = ref<AiValidationError[]>([])

// Task 17.2: Diff preview state
const diffResult = ref<DiffResult | null>(null)
const currentFunctionUnitData = ref<AiGeneratedData | null>(null)

// Inline documents state
const inlineDocuments = ref<InlineDocument[]>([])
let inlineDocIdCounter = 0

// Preview/degradation helpers (pure)
const { computePreviewData, formatRelativeTime } = useChatDialogPreview()

// Task 16.3: Regenerate scope selection
const {
  SCOPE_OPTIONS,
  selectedScopes,
  showScopeSelector,
  scopeKeyMap,
  regenerateScope
} = useChatDialogScope()

// Task 17.3: Undo state
const {
  showUndoButton,
  undoCountdown,
  startUndoCountdown,
  handleUndo,
  clearUndoTimer
} = useChatDialogUndo(
  () => props.functionUnitId,
  t,
  () => emit('regenerate') // Refresh data
)

// Task 16.4 + 17.1: Draft state (degradation draft + generation draft)
const {
  hasDraft,
  hasGenerationDraft,
  checkForDraft,
  saveDraftToLocalStorage: saveDraftToStorage,
  restoreDraft,
  restoreGenerationDraft,
  dismissDraft,
  dismissGenerationDraft
} = useChatDialogDraft(
  () => props.functionUnitId,
  () => props.sessionId,
  t,
  {
    setInputText: (text: string) => { inputText.value = text },
    restoreGeneration: (draft: RestoredGenerationDraft) => {
      generatedData.value = draft.generatedData
      previewData.value = draft.previewData || computePreviewData(draft.generatedData)
    }
  }
)

// Messages container height for InlineDocumentViewer max-height
useChatDialogMessagesHeight()

// Streaming message placeholder
const streamingMessage = computed<AiMessage>(() => ({
  id: -1,
  sessionId: props.sessionId,
  role: 'ASSISTANT',
  content: streamingContent.value,
  phase: props.phase,
  createdAt: new Date().toISOString()
}))

const isSendDisabled = computed(() => isStreaming.value || !inputText.value.trim())

/**
 * AMToken 已过期或缺失：AI gateway 回 401，重试用的还是同一个凭证，只能重新登录。
 * 走统一登录（autoSso）而不是 reload——它会重跑 DSP 认证并刷新 AMToken cookie。
 */
const REAUTH_ERROR_CODES = ['AI_GATEWAY_UNAUTHORIZED', 'AI_GATEWAY_TOKEN_MISSING']
const needsReauth = computed(() => !!errorCode.value && REAUTH_ERROR_CODES.includes(errorCode.value))

function handleSignInAgain() {
  setSsoReturnPath(window.location.pathname + window.location.search)
  redirectToUnifiedLogin('developer-workstation', { autoSso: true })
}

// Compute i18n-aware error message based on errorCode
const errorMessage = computed(() => {
  if (!error.value) return ''
  if (errorCode.value) {
    const i18nKey = `ai.error.${errorCode.value}`
    const translated = t(i18nKey)
    // If translation key is not found, vue-i18n returns the key itself
    if (translated !== i18nKey) return translated
  }
  return error.value
})

// Task 16.2: 进度 ticker 文案（generationStep 1..5 映射 ai.progress.* key）
const PROGRESS_KEYS = ['analyzing', 'designingTables', 'creatingForms', 'generatingProcess', 'validating', 'ready'] as const
const progressText = computed(() => {
  const key = PROGRESS_KEYS[generationStep.value - 1]
  return key ? t(`ai.progress.${key}`) : ''
})

const inputPlaceholder = computed(() => {
  if (previewData.value) return t('ai.chat.inputFeedback')
  if (isStreaming.value) return t('ai.chat.aiReplying')
  return t('ai.chat.inputMessage')
})

// Initialize messages from props
onMounted(() => {
  if (props.initialMessages.length) {
    setMessages([...props.initialMessages])
  }
  // Task 16.4: Check for saved draft on mount
  checkForDraft()
  // Task 17.2: Fetch current function unit data for diff in MODIFY mode
  if (props.mode === 'MODIFY') {
    fetchCurrentFunctionUnitData()
  }
})

onBeforeUnmount(() => {
  // Task 17.3: Clear undo timer
  clearUndoTimer()
})

/**
 * Task 17.2: Fetch current function unit data for diff comparison in MODIFY mode.
 */
async function fetchCurrentFunctionUnitData() {
  try {
    const res = await functionUnitApi.getById(props.functionUnitId)
    if (res.data) {
      // Map the function unit response to AiGeneratedData-like structure for diff
      const fu = res.data as unknown as Record<string, unknown>
      currentFunctionUnitData.value = {
        tableDefinitions: (fu.tableDefinitions || []) as any[],
        formDefinitions: (fu.formDefinitions || []) as any[],
        actionDefinitions: (fu.actionDefinitions || []) as any[],
        decisionDefinitions: (fu.decisionDefinitions || []) as any[],
        tableRelations: (fu.tableRelations || []) as any[],
        processDefinition: fu.processDefinition as any
      }
    }
  } catch {
    // Silently ignore — diff is a nice-to-have feature
  }
}

// Register event callbacks
onPhaseComplete((phase: AiPhase) => {
  // DESIGN 和 REQUIREMENTS 阶段完成后会自动触发下一阶段，不需要显示按钮
  // 只在 GENERATION 阶段完成时不显示按钮（因为没有下一阶段了）
  // 但仍然 emit 事件让 AiPanel 处理阶段推进
  emit('phaseComplete', phase)
})

onDocument((type: string, content: string) => {
  emit('document', type, content)
  // Also add to inline documents for in-chat display
  inlineDocuments.value.push({
    id: ++inlineDocIdCounter,
    documentType: type as AiDocumentType,
    content
  })
})

onGeneratedData((data: any) => {
  generatedData.value = data as AiGeneratedData
  previewData.value = computePreviewData(data as AiGeneratedData)
  // Fresh generation result → the Apply button must be actionable again
  applyState.value = 'idle'
  // Task 17.2: Compute diff in MODIFY mode
  if (props.mode === 'MODIFY' && currentFunctionUnitData.value) {
    diffResult.value = computeDiff(currentFunctionUnitData.value, data as AiGeneratedData)
  }
})

onValidationWarning((warnings: any[]) => {
  validationWarnings.value = warnings as AiValidationError[]
  scrollToBottom()
})

onSession((sessionId: string) => {
  emit('sessionCreated', sessionId)
})

// Send message
function handleSend() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  inputText.value = ''
  validationErrors.value = []
  validationWarnings.value = []

  sendMessage({
    functionUnitId: props.functionUnitId,
    sessionId: props.sessionId,
    message: text,
    phase: props.phase,
    mode: props.mode,
    regenerateScope: props.mode === 'MODIFY' ? regenerateScope.value : undefined
  })

  emit('sendMessage')
}

function handleRetry() {
  retry()
}

// Stop the in-flight generation: aborts the SSE fetch client-side and re-enables input.
// The backend call keeps running to completion — any document it produces is still saved
// server-side; only the streamed reply for this turn is discarded.
function handleStop() {
  cancel()
}

function handleNextPhase() {
  showPhaseCompleteBtn.value = false
  emit('phaseComplete', props.phase)
}

// Apply lifecycle surfaced on the preview's Apply button. The parent (AiPanel) owns the
// API call and reports the outcome back via markApplySuccess / markApplyFailed.
const applyState = ref<'idle' | 'applying' | 'applied'>('idle')

function handleApply() {
  if (!generatedData.value || applyState.value === 'applying') {
    return
  }
  applyState.value = 'applying'
  emit('apply', generatedData.value)
}

/** Called by AiPanel when the apply API call succeeded. */
function markApplySuccess() {
  applyState.value = 'applied'
  // Clear generation draft only after a CONFIRMED apply (previously this happened on
  // click, so a failed apply still wiped the draft and started the undo countdown).
  clearCurrentDraft()
  if (props.sessionId) {
    clearGenerationDraft(props.functionUnitId, props.sessionId)
  }
  // Task 17.3: Start undo countdown
  startUndoCountdown()
}

/** Called by AiPanel when the apply API call failed (incl. validation errors). */
function markApplyFailed() {
  applyState.value = 'idle'
}

function handleRegenerate() {
  generatedData.value = null
  previewData.value = null
  diffResult.value = null
  applyState.value = 'idle'
  // Actually rerun the generation (previously this only cleared the preview and the
  // click appeared to do nothing). The auto-trigger hint doubles as user feedback.
  autoSendMessage('[AUTO_TRIGGER] Please regenerate the complete function unit component data '
    + 'based on the existing requirements and design documents. '
    + 'Ignore any previously generated component data and produce a fresh version.')
  emit('regenerate')
}

function applyTemplate(tpl: AiTemplate) {
  inputText.value = tpl.promptTemplate
}

// Wrap the draft composable so the template can call it without arguments.
function saveDraftToLocalStorage() {
  saveDraftToStorage(inputText.value)
}

function navigateToManualCreate() {
  // Navigate to the function unit designer manual edit mode
  // This emits an event for the parent to handle navigation
  emit('regenerate')
}

// Auto-scroll to bottom
function scrollToBottom() {
  nextTick(() => {
    scrollAnchorRef.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

// Watch for new messages
watch(() => messages.value.length, () => {
  scrollToBottom()
})

// Watch streaming content for auto-scroll
watch(streamingContent, () => {
  scrollToBottom()
})

// Expose for parent to set validation errors
function setValidationErrors(errors: AiValidationError[]) {
  validationErrors.value = errors
  scrollToBottom()
}

function setValidationWarnings(warnings: AiValidationError[]) {
  validationWarnings.value = warnings
  scrollToBottom()
}

/**
 * Auto-send a message programmatically (triggered by phase transition).
 * Shows a brief system hint in the chat, then sends the trigger message to AI.
 */
function autoSendMessage(message: string) {
  // Add a system-style hint so user knows what's happening
  const hintMessage: AiMessage = {
    id: Date.now() - 1,
    sessionId: props.sessionId,
    role: 'ASSISTANT',
    content: t('ai.chat.autoGenerating'),
    phase: props.phase,
    createdAt: new Date().toISOString()
  }
  messages.value.push(hintMessage)

  // Send the trigger message to AI backend
  sendMessage({
    functionUnitId: props.functionUnitId,
    sessionId: props.sessionId,
    message,
    phase: props.phase,
    mode: props.mode
  })

  emit('sendMessage')
  scrollToBottom()
}

defineExpose({
  setValidationErrors,
  setValidationWarnings,
  autoSendMessage,
  setMessages,
  markApplySuccess,
  markApplyFailed,
  // AiPanel must act on THIS component's useAiChat instance (the one holding the SSE
  // fetch) — a useAiChat() call in the parent creates unrelated state and is a no-op.
  // cancel 供关闭面板时中止流式请求；isStreaming 供头部状态灯与防重复触发 guard 读取。
  cancel,
  isStreaming
})
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.chat-dialog {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: ai.$ai-mist;
}

.chat-dialog__messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

// 生成进度 ticker：一行等宽文案 + 红色发丝进度线
.chat-dialog__ticker {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 16px;
  background: ai.$ai-paper;
  border-bottom: 1px solid ai.$ai-hairline;
}

.chat-dialog__ticker-step {
  @include ai.ai-mono-num;
  font-size: 11px;
  font-weight: 600;
  color: ai.$ai-red;
  flex-shrink: 0;
}

.chat-dialog__ticker-text {
  font-size: 12px;
  color: ai.$ai-graphite;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-dialog__ticker-bar {
  flex: 1;
  height: 2px;
  border-radius: 1px;
  background: ai.$ai-hairline;
  overflow: hidden;
  min-width: 60px;

  i {
    display: block;
    height: 100%;
    width: 36%;
    border-radius: 1px;
    background: ai.$ai-red;
    animation: ticker-slide 1.4s ease-in-out infinite;
  }
}

@keyframes ticker-slide {
  0% { transform: translateX(-110%); }
  100% { transform: translateX(310%); }
}

.chat-dialog__preview {
  padding: 0 16px;
}

.chat-dialog__validation {
  padding: 8px 16px;
}

.chat-dialog__error-list {
  margin: 8px 0 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.8;
}

.chat-dialog__error-type {
  @include ai.ai-mono-num;
  font-weight: 600;
  color: ai.$ai-red;
  margin-right: 4px;

  &--warning {
    color: #e6a23c;
  }
}

.chat-dialog__error-path {
  font-family: ai.$ai-mono;
  font-size: 12px;
  color: ai.$ai-faint;
  margin-right: 4px;
}

.chat-dialog__error {
  padding: 8px 16px;
}

.chat-dialog__phase-action {
  display: flex;
  justify-content: center;
  padding: 12px 16px;
}

.chat-dialog__input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid ai.$ai-hairline;
  background: ai.$ai-paper;

  :deep(.el-textarea__inner) {
    border-radius: 8px;
    box-shadow: 0 0 0 1px ai.$ai-hairline inset;
    background: ai.$ai-mist;
    font-size: 13px;
    transition: box-shadow 0.2s, background 0.2s;

    &:hover {
      box-shadow: 0 0 0 1px #c9ced6 inset;
    }

    &:focus {
      background: ai.$ai-paper;
      box-shadow: 0 0 0 1.5px ai.$ai-red inset;
    }
  }
}

.chat-dialog__scope {
  padding: 4px 16px;
  border-top: 1px solid ai.$ai-hairline;
  background: ai.$ai-paper;
}

.chat-dialog__scope-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px 0;
}

.chat-dialog__degradation {
  padding: 8px 16px;
}

.chat-dialog__degradation-time {
  font-size: 13px;
  color: ai.$ai-faint;
  margin: 4px 0 8px;
}

.chat-dialog__degradation-actions {
  display: flex;
  gap: 8px;
}

.chat-dialog__draft-alert {
  margin: 8px 16px;
}

.chat-dialog__undo {
  display: flex;
  justify-content: center;
  padding: 8px 16px;
}

.chat-dialog__templates {
  padding: 28px 20px 16px;
}

.chat-dialog__templates-eyebrow {
  @include ai.ai-eyebrow;
  text-align: center;
  margin-bottom: 6px;
}

.chat-dialog__templates-title {
  font-size: 15px;
  font-weight: 600;
  color: ai.$ai-ink;
  margin-bottom: 16px;
  text-align: center;
}

.chat-dialog__template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
}

// 蓝图卡：左侧等宽编号 + 悬停红色左缘
.chat-dialog__template-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  text-align: left;
  background: ai.$ai-paper;
  border: 1px solid ai.$ai-hairline;
  border-radius: 8px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  font: inherit;
  transition: border-color 0.2s, box-shadow 0.2s;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 2px;
    background: ai.$ai-red;
    transform: scaleY(0);
    transform-origin: top;
    transition: transform 0.2s ease;
  }

  &:hover,
  &:focus-visible {
    border-color: #c9ced6;
    box-shadow: 0 2px 8px rgba(35, 40, 46, 0.06);

    &::before {
      transform: scaleY(1);
    }
  }

  &:focus-visible {
    outline: 2px solid ai.$ai-red;
    outline-offset: 1px;
  }
}

.chat-dialog__template-idx {
  @include ai.ai-mono-num;
  font-size: 11px;
  font-weight: 600;
  color: ai.$ai-faint;
  line-height: 20px;
  flex-shrink: 0;

  .chat-dialog__template-card:hover & {
    color: ai.$ai-red;
  }
}

.chat-dialog__template-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.chat-dialog__template-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 13px;
  color: ai.$ai-ink;

  .el-icon {
    color: ai.$ai-graphite;
  }
}

.chat-dialog__template-desc {
  font-size: 12px;
  color: ai.$ai-graphite;
  margin: 0;
  line-height: 1.55;
}

// Thinking indicator (shows while waiting for AI's first response)
.chat-dialog__thinking {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 16px;
  animation: fadeIn 0.3s ease;

  &-avatar {
    width: 26px;
    height: 26px;
    border-radius: 6px;
    background: ai.$ai-paper;
    border: 1px solid ai.$ai-hairline;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    font-family: ai.$ai-mono;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.06em;
    color: ai.$ai-red;
  }

  &-bubble {
    background: ai.$ai-paper;
    border: 1px solid ai.$ai-hairline;
    border-radius: 3px 10px 10px 10px;
    padding: 9px 14px;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &-dots {
    display: inline-flex;
    gap: 3px;

    .dot {
      font-size: 6px;
      color: ai.$ai-red;
      animation: bounce 1.4s ease infinite;

      &:nth-child(2) { animation-delay: 0.2s; }
      &:nth-child(3) { animation-delay: 0.4s; }
    }
  }

  &-text {
    font-size: 13px;
    color: ai.$ai-graphite;
  }
}

@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-4px); opacity: 1; }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .chat-dialog__ticker-bar i,
  .chat-dialog__thinking,
  .chat-dialog__thinking-dots .dot {
    animation: none !important;
  }

  .chat-dialog__template-card::before {
    transition: none;
  }
}
</style>
