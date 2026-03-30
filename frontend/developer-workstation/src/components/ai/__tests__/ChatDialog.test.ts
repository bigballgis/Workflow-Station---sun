import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { ref } from 'vue'
import { createI18n } from 'vue-i18n'
import ChatDialog from '@/components/ai/ChatDialog.vue'
import type { AiPhase, AiMessage } from '@/types/aiGeneration'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} }
})

// Mock scrollIntoView for jsdom
Element.prototype.scrollIntoView = vi.fn()

// Mock composables
const mockSendMessage = vi.fn()
const mockRetry = vi.fn()
const mockCancel = vi.fn()
const mockSetMessages = vi.fn()
const mockOnDocument = vi.fn()
const mockOnPhaseComplete = vi.fn()
const mockOnGeneratedData = vi.fn()
const mockOnValidationWarning = vi.fn()

const mockMessages = ref<any[]>([])
const mockIsStreaming = ref(false)
const mockStreamingContent = ref('')
const mockError = ref<string | null>(null)
const mockErrorCode = ref<string | null>(null)
const mockCanRetry = ref(false)
const mockPartialGeneratedData = ref<any>({})
const mockIsGenerationComplete = ref(false)
const mockGenerationStep = ref(0)
const mockDegradationInfo = ref<any>(null)
const mockClearCurrentDraft = vi.fn()

vi.mock('@/composables/useAiChat', () => ({
  useAiChat: () => ({
    messages: mockMessages,
    isStreaming: mockIsStreaming,
    streamingContent: mockStreamingContent,
    error: mockError,
    errorCode: mockErrorCode,
    canRetry: mockCanRetry,
    partialGeneratedData: mockPartialGeneratedData,
    isGenerationComplete: mockIsGenerationComplete,
    generationStep: mockGenerationStep,
    degradationInfo: mockDegradationInfo,
    sendMessage: mockSendMessage,
    retry: mockRetry,
    cancel: mockCancel,
    onDocument: mockOnDocument,
    onPhaseComplete: mockOnPhaseComplete,
    onGeneratedData: mockOnGeneratedData,
    onValidationWarning: mockOnValidationWarning,
    setMessages: mockSetMessages,
    clearCurrentDraft: mockClearCurrentDraft
  }),
  loadDraft: () => null,
  clearDraft: vi.fn()
}))

vi.mock('@/composables/useAiTemplates', () => ({
  useAiTemplates: () => ({ templates: [] })
}))

vi.mock('@/api/functionUnit', () => ({
  functionUnitApi: { getById: vi.fn().mockResolvedValue({ data: null }) }
}))

vi.mock('@/api/aiGeneration', () => ({
  aiGenerationApi: { undoLastApply: vi.fn().mockResolvedValue({}) },
  AI_CHAT_STREAM_URL: '/api/v1/ai-generation/chat-stream'
}))

vi.mock('@/types/aiGeneration', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>
  return { ...actual, computeDiff: vi.fn().mockReturnValue(null) }
})

// Stub Element Plus and child components
const globalStubs = {
  PhaseIndicator: { template: '<div class="phase-indicator" />', props: ['currentPhase', 'completedPhases'] },
  ChatMessage: { template: '<div class="chat-message" />', props: ['message', 'isStreaming'] },
  InlineDocumentViewer: { template: '<div class="inline-document-viewer" />', props: ['documentType', 'content'] },
  GenerationPreview: {
    template: '<div class="generation-preview" />',
    props: ['previewData', 'generatedData', 'isGenerationComplete', 'isStreaming', 'mode', 'diffResult'],
    emits: ['apply', 'regenerate']
  },
  'el-input': {
    template: '<textarea class="el-input" />',
    props: ['modelValue', 'type', 'rows', 'placeholder', 'disabled', 'resize'],
    emits: ['update:modelValue']
  },
  'el-button': {
    template: '<button class="el-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'icon', 'disabled', 'loading', 'text', 'size'],
    emits: ['click']
  },
  'el-alert': { template: '<div class="el-alert"><slot /></div>', props: ['title', 'type', 'closable', 'showIcon'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-steps': { template: '<div class="el-steps"><slot /></div>', props: ['active', 'finishStatus', 'simple'] },
  'el-step': { template: '<div class="el-step" />', props: ['title'] },
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>', props: ['shadow'] },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot /></div>', props: ['span'] },
  'el-checkbox-group': { template: '<div class="el-checkbox-group"><slot /></div>', props: ['modelValue'], emits: ['update:modelValue'] },
  'el-checkbox': { template: '<div class="el-checkbox"><slot /></div>', props: ['value', 'size'] },
  Promotion: { template: '<span />' },
  Grid: { template: '<span />' },
  Stamp: { template: '<span />' },
  EditPen: { template: '<span />' },
  DataAnalysis: { template: '<span />' },
  ArrowDown: { template: '<span />' },
  ArrowUp: { template: '<span />' }
}

describe('ChatDialog', () => {
  const defaultProps = {
    functionUnitId: 1,
    sessionId: 'session-1',
    phase: 'REQUIREMENTS' as AiPhase,
    mode: 'NEW' as const,
    completedPhases: [] as AiPhase[],
    initialMessages: [] as AiMessage[]
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockMessages.value = []
    mockIsStreaming.value = false
    mockStreamingContent.value = ''
    mockError.value = null
    mockErrorCode.value = null
    mockCanRetry.value = false
    mockPartialGeneratedData.value = {}
    mockIsGenerationComplete.value = false
    mockGenerationStep.value = 0
    mockDegradationInfo.value = null
  })

  function mountComponent(props = defaultProps) {
    return shallowMount(ChatDialog, {
      props,
      global: { stubs: globalStubs, plugins: [i18n] }
    })
  }

  it('should render the chat dialog structure', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.chat-dialog').exists()).toBe(true)
    expect(wrapper.find('.phase-indicator').exists()).toBe(true)
    expect(wrapper.find('.chat-dialog__messages').exists()).toBe(true)
    expect(wrapper.find('.chat-dialog__input-area').exists()).toBe(true)
  })

  it('should render PhaseIndicator stub', () => {
    const wrapper = mountComponent({
      ...defaultProps,
      phase: 'DESIGN',
      completedPhases: ['REQUIREMENTS']
    })
    expect(wrapper.find('.phase-indicator').exists()).toBe(true)
  })

  it('should render chat messages when messages exist', async () => {
    mockMessages.value = [
      { id: 1, sessionId: 's1', role: 'USER', content: 'Hi', phase: 'REQUIREMENTS', createdAt: '' },
      { id: 2, sessionId: 's1', role: 'ASSISTANT', content: 'Hello', phase: 'REQUIREMENTS', createdAt: '' }
    ]
    const wrapper = mountComponent()
    await wrapper.vm.$nextTick()
    const chatMessages = wrapper.findAll('.chat-message')
    expect(chatMessages.length).toBe(2)
  })

  it('should show streaming message when isStreaming and streamingContent exist', async () => {
    mockIsStreaming.value = true
    mockStreamingContent.value = 'Thinking...'
    const wrapper = mountComponent()
    await wrapper.vm.$nextTick()
    // Should have the streaming ChatMessage
    const chatMessages = wrapper.findAll('.chat-message')
    expect(chatMessages.length).toBe(1)
  })

  it('should show error alert when error exists', () => {
    mockError.value = 'Connection failed'
    const wrapper = mountComponent()
    expect(wrapper.find('.chat-dialog__error').exists()).toBe(true)
  })

  it('should not show error alert when no error', () => {
    mockError.value = null
    const wrapper = mountComponent()
    expect(wrapper.find('.chat-dialog__error').exists()).toBe(false)
  })

  it('should register event callbacks on mount', () => {
    mountComponent()
    expect(mockOnPhaseComplete).toHaveBeenCalled()
    expect(mockOnGeneratedData).toHaveBeenCalled()
  })

  it('should call setMessages with initialMessages on mount', async () => {
    const initialMessages = [
      { id: 1, sessionId: 's1', role: 'USER' as const, content: 'Hi', phase: 'REQUIREMENTS' as const, createdAt: '' }
    ]
    mountComponent({ ...defaultProps, initialMessages })

    // Wait for onMounted
    await new Promise(r => setTimeout(r, 0))
    expect(mockSetMessages).toHaveBeenCalledWith(initialMessages)
  })
})
