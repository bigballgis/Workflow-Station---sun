<template>
  <div class="chat-dialog">
    <!-- Top: Phase Indicator -->
    <PhaseIndicator :current-phase="phase" :completed-phases="completedPhases" />

    <!-- Middle: Message List -->
    <div class="chat-dialog__messages">
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

      <!-- Generation Preview -->
      <div v-if="previewData && generatedData" class="chat-dialog__preview">
        <GenerationPreview
          :preview-data="previewData"
          :generated-data="generatedData"
          @apply="handleApply"
          @regenerate="handleRegenerate"
        />
      </div>

      <!-- Validation Errors -->
      <div v-if="validationErrors.length" class="chat-dialog__validation">
        <el-alert
          :title="t('ai.chat.validationFailed')"
          type="error"
          :closable="false"
          show-icon
        >
          <ul class="chat-dialog__error-list">
            <li v-for="(err, idx) in validationErrors" :key="idx">
              <span class="chat-dialog__error-type">[{{ err.errorType }}]</span>
              <span class="chat-dialog__error-path">{{ err.fieldPath }}</span>
              {{ err.description }}
            </li>
          </ul>
        </el-alert>
      </div>

      <!-- Error Alert with Retry -->
      <div v-if="error" class="chat-dialog__error">
        <el-alert
          :title="error"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #default>
            <el-button v-if="canRetry" size="small" type="primary" @click="handleRetry">
              {{ t('ai.chat.retry') }}
            </el-button>
          </template>
        </el-alert>
      </div>

      <!-- Phase Complete: Enter Next Phase Button -->
      <div v-if="showPhaseCompleteBtn" class="chat-dialog__phase-action">
        <el-button type="success" @click="handleNextPhase">
          {{ t('ai.chat.nextPhase') }}
        </el-button>
      </div>

      <!-- Scroll anchor -->
      <div ref="scrollAnchorRef" />
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
        type="primary"
        :icon="Promotion"
        :disabled="isSendDisabled"
        :loading="isStreaming"
        @click="handleSend"
      >
        {{ t('ai.chat.send') }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount, provide } from 'vue'
import { useI18n } from 'vue-i18n'
import { Promotion } from '@element-plus/icons-vue'
import PhaseIndicator from './PhaseIndicator.vue'
import ChatMessage from './ChatMessage.vue'
import GenerationPreview from './GenerationPreview.vue'
import InlineDocumentViewer from './InlineDocumentViewer.vue'
import { useAiChat } from '@/composables/useAiChat'
import type {
  AiPhase,
  AiMode,
  AiMessage,
  AiGeneratedData,
  GenerationPreviewData,
  AiValidationError,
  InlineDocument,
  AiDocumentType
} from '@/types/aiGeneration'

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
}>()

// Chat composable
const {
  messages,
  isStreaming,
  streamingContent,
  error,
  canRetry,
  sendMessage,
  retry,
  cancel: _cancel,
  onDocument,
  onPhaseComplete,
  onGeneratedData,
  setMessages
} = useAiChat()

// i18n
const { t } = useI18n()

// Local state
const inputText = ref('')
const scrollAnchorRef = ref<HTMLElement | null>(null)
const showPhaseCompleteBtn = ref(false)
const generatedData = ref<AiGeneratedData | null>(null)
const previewData = ref<GenerationPreviewData | null>(null)
const validationErrors = ref<AiValidationError[]>([])

// Inline documents state
const inlineDocuments = ref<InlineDocument[]>([])
let inlineDocIdCounter = 0

// Messages container height for InlineDocumentViewer max-height
const messagesHeight = ref(400)
let resizeObserver: ResizeObserver | null = null
provide('chatMessagesHeight', messagesHeight)
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
  // Observe messages container height for InlineDocumentViewer
  const messagesEl = document.querySelector('.chat-dialog__messages')
  if (messagesEl) {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        messagesHeight.value = entry.contentRect.height
      }
    })
    resizeObserver.observe(messagesEl)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

// Register event callbacks
onPhaseComplete((_phase: AiPhase) => {
  showPhaseCompleteBtn.value = true
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
})

// Compute GenerationPreviewData from AiGeneratedData
function computePreviewData(data: AiGeneratedData): GenerationPreviewData {
  const tables = data.tableDefinitions || []
  const forms = data.formDefinitions || []
  const actions = data.actionDefinitions || []
  const process = data.processDefinition

  let totalFieldCount = 0
  for (const table of tables) {
    totalFieldCount += (table.fieldDefinitions || []).length
  }

  let processNodeCount = 0
  let processGatewayCount = 0
  if (process?.bpmnXml) {
    // Simple counting from BPMN XML
    const xml = process.bpmnXml as string
    const taskMatches = xml.match(/<bpmn:userTask|<bpmn:serviceTask|<bpmn:scriptTask|<bpmn:startEvent|<bpmn:endEvent|<bpmn:task/g)
    processNodeCount = taskMatches ? taskMatches.length : 0
    const gatewayMatches = xml.match(/<bpmn:exclusiveGateway|<bpmn:parallelGateway|<bpmn:inclusiveGateway|<bpmn:eventBasedGateway/g)
    processGatewayCount = gatewayMatches ? gatewayMatches.length : 0
  }

  const actionTypes = [...new Set(actions.map((a: any) => a.actionType).filter(Boolean))]

  return {
    tableCount: tables.length,
    totalFieldCount,
    formCount: forms.length,
    actionCount: actions.length,
    actionTypes,
    processNodeCount,
    processGatewayCount,
    iconSvg: data.icon?.svgContent
  }
}

// Send message
function handleSend() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  inputText.value = ''
  validationErrors.value = []

  sendMessage({
    functionUnitId: props.functionUnitId,
    sessionId: props.sessionId,
    message: text,
    phase: props.phase,
    mode: props.mode
  })

  emit('sendMessage')
}

function handleRetry() {
  retry()
}

function handleNextPhase() {
  showPhaseCompleteBtn.value = false
  emit('phaseComplete', props.phase)
}

function handleApply() {
  if (generatedData.value) {
    emit('apply', generatedData.value)
  }
}

function handleRegenerate() {
  generatedData.value = null
  previewData.value = null
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

defineExpose({
  setValidationErrors
})
</script>

<style lang="scss" scoped>
.chat-dialog {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.chat-dialog__messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
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
  font-weight: 600;
  color: #f56c6c;
  margin-right: 4px;
}

.chat-dialog__error-path {
  color: #909399;
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
  border-top: 1px solid #ebeef5;
  background: #fff;
}
</style>
