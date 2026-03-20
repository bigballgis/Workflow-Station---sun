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

const mockMessages = ref<any[]>([])
const mockIsStreaming = ref(false)
const mockStreamingContent = ref('')
const mockError = ref<string | null>(null)
const mockCanRetry = ref(false)

vi.mock('@/composables/useAiChat', () => ({
  useAiChat: () => ({
    messages: mockMessages,
    isStreaming: mockIsStreaming,
    streamingContent: mockStreamingContent,
    error: mockError,
    canRetry: mockCanRetry,
    sendMessage: mockSendMessage,
    retry: mockRetry,
    cancel: mockCancel,
    onDocument: mockOnDocument,
    onPhaseComplete: mockOnPhaseComplete,
    onGeneratedData: mockOnGeneratedData,
    setMessages: mockSetMessages
  })
}))

// Stub Element Plus and child components
const globalStubs = {
  PhaseIndicator: { template: '<div class="phase-indicator" />', props: ['currentPhase', 'completedPhases'] },
  ChatMessage: { template: '<div class="chat-message" />', props: ['message', 'isStreaming'] },
  GenerationPreview: {
    template: '<div class="generation-preview" />',
    props: ['previewData', 'generatedData'],
    emits: ['apply', 'regenerate']
  },
  'el-input': {
    template: '<textarea class="el-input" />',
    props: ['modelValue', 'type', 'rows', 'placeholder', 'disabled', 'resize'],
    emits: ['update:modelValue']
  },
  'el-button': {
    template: '<button class="el-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'icon', 'disabled', 'loading'],
    emits: ['click']
  },
  'el-alert': { template: '<div class="el-alert"><slot /></div>', props: ['title', 'type', 'closable', 'showIcon'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  Promotion: { template: '<span />' }
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
    mockCanRetry.value = false
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
