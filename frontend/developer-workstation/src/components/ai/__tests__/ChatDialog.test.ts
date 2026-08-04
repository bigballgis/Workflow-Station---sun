import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { ref } from 'vue'
import { createI18n } from 'vue-i18n'
import ChatDialog from '@/components/ai/ChatDialog.vue'
import type { AiPhase, AiMessage, InlineDocument } from '@/types/aiGeneration'

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
const mockOnSession = vi.fn()

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
    onSession: mockOnSession,
    setMessages: mockSetMessages
  }),
  loadDraft: () => null,
  clearDraft: vi.fn(),
  // markApplySuccess 用它把生成结果以 applied 标记写回草稿槽——mock 掉整个模块时必须补上，
  // 否则 ChatDialog 里的具名导入是 undefined，Apply 成功路径直接抛错。
  // 必须就地 vi.fn()：工厂会被提升到文件顶部，引用外层变量会命中 TDZ。
  saveDraft: vi.fn()
}))

vi.mock('@/composables/useAiTemplates', () => ({
  useAiTemplates: () => ({ templates: [] })
}))

vi.mock('@/api/functionUnit', () => ({
  functionUnitApi: { getById: vi.fn().mockResolvedValue({ data: null }) }
}))

const mockSetSsoReturnPath = vi.fn()
const mockRedirectToUnifiedLogin = vi.fn()
vi.mock('@/utils/sso', () => ({
  setSsoReturnPath: (path: string) => mockSetSsoReturnPath(path),
  redirectToUnifiedLogin: (clientId: string, options: unknown) => mockRedirectToUnifiedLogin(clientId, options)
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
  InlineDocumentViewer: {
    template: '<div class="inline-document-viewer" />',
    props: ['documentType', 'content', 'busy', 'version', 'generatedAt', 'fresh'],
    emits: ['regenerate']
  },
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

  // 过期的 AMToken 重试多少次都是同一个 401，唯一出路是重新登录
  it('should offer sign-in-again instead of retry when the AMToken was rejected', async () => {
    mockError.value = 'AI gateway rejected the AMToken with HTTP 401'
    mockErrorCode.value = 'AI_GATEWAY_UNAUTHORIZED'
    mockCanRetry.value = false

    const wrapper = mountComponent()
    const buttons = wrapper.find('.chat-dialog__error').findAll('button')
    expect(buttons.length).toBe(1)

    await buttons[0].trigger('click')
    expect(mockSetSsoReturnPath).toHaveBeenCalled()
    expect(mockRedirectToUnifiedLogin).toHaveBeenCalledWith('developer-workstation', { autoSso: true })
    expect(mockRetry).not.toHaveBeenCalled()
  })

  it('should not offer sign-in-again for an ordinary retryable error', () => {
    mockError.value = 'AI service timed out'
    mockErrorCode.value = 'AI_WEBHOOK_TIMEOUT'
    mockCanRetry.value = true

    const wrapper = mountComponent()
    expect(wrapper.find('.chat-dialog__error').findAll('button').length).toBe(1)
    expect(mockRedirectToUnifiedLogin).not.toHaveBeenCalled()
  })

  it('should register event callbacks on mount', () => {
    mountComponent()
    expect(mockOnPhaseComplete).toHaveBeenCalled()
    expect(mockOnGeneratedData).toHaveBeenCalled()
  })

  // --- 相位闸门：模型说"完成了"只是提议，推进要用户点 ---

  /** 取出组件注册给 useAiChat 的 phase_complete 回调并触发它。 */
  function firePhaseComplete(phase: AiPhase) {
    const cb = mockOnPhaseComplete.mock.calls.at(-1)?.[0] as (p: AiPhase) => void
    cb(phase)
  }

  it('should offer a button instead of cascading into the next phase', async () => {
    const wrapper = mountComponent()

    firePhaseComplete('REQUIREMENTS')
    await wrapper.vm.$nextTick()

    // 不 emit 就不会有 AiPanel 的 advancePhase + autoTriggerPhase，级联到此为止
    expect(wrapper.emitted('phaseComplete')).toBeFalsy()
    expect(wrapper.find('.chat-dialog__phase-action').exists()).toBe(true)
  })

  it('should advance only when the user clicks the phase button', async () => {
    const wrapper = mountComponent()
    firePhaseComplete('REQUIREMENTS')
    await wrapper.vm.$nextTick()

    await wrapper.find('.chat-dialog__phase-action button').trigger('click')

    expect(wrapper.emitted('phaseComplete')).toBeTruthy()
    expect(wrapper.find('.chat-dialog__phase-action').exists()).toBe(false)
  })

  // GENERATION 没有下一相位可提议，按钮无意义，照原样交给 AiPanel
  it('should still emit directly on the last phase', async () => {
    const wrapper = mountComponent({ ...defaultProps, phase: 'GENERATION' as AiPhase })

    firePhaseComplete('GENERATION')
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('phaseComplete')).toBeTruthy()
    expect(wrapper.find('.chat-dialog__phase-action').exists()).toBe(false)
  })

  /**
   * 重开面板：按钮必须从落库的产物重新推导出来。
   *
   * 闸门下这个按钮是唯一推进相位的入口，而它原本只由本次挂载收到的 phase_complete 点亮——
   * 关掉面板再打开就没了，用户只能再发一轮对话赌模型再说一次 PHASE_COMPLETE 才能前进。
   */
  it('should restore the phase button from the current phase document after reopening', async () => {
    const wrapper = mountComponent({ ...defaultProps, phase: 'DESIGN' as AiPhase })
    expect(wrapper.find('.chat-dialog__phase-action').exists()).toBe(false)

    ;(wrapper.vm as unknown as { setInlineDocuments: (d: InlineDocument[]) => void }).setInlineDocuments([
      { id: 1, documentType: 'REQUIREMENTS', content: '# Req' },
      { id: 2, documentType: 'DESIGN', content: '# Design' }
    ])
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.chat-dialog__phase-action').exists()).toBe(true)
  })

  // 当前相位还没有产物 → 没有什么可推进的，不能凭上游文档就亮按钮
  it('should not offer the phase button when the current phase produced nothing yet', async () => {
    const wrapper = mountComponent({ ...defaultProps, phase: 'DESIGN' as AiPhase })

    ;(wrapper.vm as unknown as { setInlineDocuments: (d: InlineDocument[]) => void }).setInlineDocuments([
      { id: 1, documentType: 'REQUIREMENTS', content: '# Req' }
    ])
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.chat-dialog__phase-action').exists()).toBe(false)
  })

  // --- Regenerate 输入框：定向修改 vs 整篇重出 ---

  /** ChatDialog 通过 defineExpose 暴露的那部分，测试里只用得到这一个方法。 */
  type ExposedChatDialog = { setInlineDocuments: (docs: InlineDocument[]) => void }

  async function mountWithDesignDoc() {
    const wrapper = mountComponent({ ...defaultProps, phase: 'GENERATION' as AiPhase })
    ;(wrapper.vm as unknown as ExposedChatDialog).setInlineDocuments([
      { id: 7, documentType: 'DESIGN', content: '# Design', version: 2, generatedAt: '2026-08-03T06:32:10Z' }
    ])
    await wrapper.vm.$nextTick()
    return wrapper
  }

  it('should keep the blank-input Regenerate on the original full-rewrite prompt', async () => {
    const wrapper = await mountWithDesignDoc()

    wrapper.findComponent(globalStubs.InlineDocumentViewer).vm.$emit('regenerate', 'DESIGN', '')
    await wrapper.vm.$nextTick()

    const sent = mockSendMessage.mock.calls.at(-1)?.[0]
    expect(sent.message).toContain('Rewrite it from scratch')
    expect(sent.message).not.toContain('USER CORRECTION REQUEST')
  })

  it('should turn a filled-in Regenerate into a keep-what-is-correct instruction', async () => {
    const wrapper = await mountWithDesignDoc()
    const instruction = 'shipment 表缺了 carrier 和 tracking_no'

    wrapper.findComponent(globalStubs.InlineDocumentViewer).vm.$emit('regenerate', 'DESIGN', instruction)
    await wrapper.vm.$nextTick()

    const sent = mockSendMessage.mock.calls.at(-1)?.[0]
    // 反漂移契约取代"从头重写"：为了改两个字段把整篇重滚一遍，正确的部分每次都在重新抽奖
    expect(sent.message).not.toContain('Rewrite it from scratch')
    expect(sent.message).toContain('Keep everything in the current version that is already correct')
    // 用户那段话必须落在最末尾——前面几千 token 的 schema metadata 很容易把夹在中间的指令冲掉
    expect(sent.message.indexOf(instruction))
      .toBeGreaterThan(sent.message.indexOf('USER CORRECTION REQUEST'))
    // 相位保护不能丢：否则一次纠错会把会话推进到 GENERATION 并级联自动重跑
    expect(sent.regenerateOnly).toBe(true)
    expect(sent.phase).toBe('DESIGN')
  })

  it('should surface the backend document version on the card', async () => {
    const wrapper = await mountWithDesignDoc()
    const card = wrapper.findComponent(globalStubs.InlineDocumentViewer)
    expect(card.props('version')).toBe(2)
    expect(card.props('generatedAt')).toBe('2026-08-03T06:32:10Z')
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
