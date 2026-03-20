import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { ref } from 'vue'
import { createI18n } from 'vue-i18n'
import AiPanel from '@/components/ai/AiPanel.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      ai: {
        panel: {
          title: 'AI 生成助手',
          lockTitle: '功能单元已被锁定',
          lockUser: '锁定用户：',
          lockTime: '锁定时间：',
          unknownUser: '未知用户',
          requestForceUnlock: '请求强制解锁',
          initializing: '初始化中...',
          attach: '吸附',
          detach: '分离'
        }
      }
    }
  }
})

// Mock composables
const mockAcquireLock = vi.fn()
const mockReleaseLock = vi.fn()
const mockRequestForceUnlock = vi.fn()
const mockRespondForceUnlock = vi.fn()
const mockLockReset = vi.fn()
const mockLockConflict = ref(false)
const mockConflictLockInfo = ref(null)
const mockIsLocked = ref(false)

vi.mock('@/composables/useAiLock', () => ({
  useAiLock: () => ({
    lockInfo: ref(null),
    isLocked: mockIsLocked,
    lockConflict: mockLockConflict,
    conflictLockInfo: mockConflictLockInfo,
    acquireLock: mockAcquireLock,
    releaseLock: mockReleaseLock,
    requestForceUnlock: mockRequestForceUnlock,
    respondForceUnlock: mockRespondForceUnlock,
    reset: mockLockReset
  })
}))

const mockCurrentSession = ref(null)
const mockCurrentPhase = ref('REQUIREMENTS')
const mockLoadSessions = vi.fn()
const mockFindActiveSession = vi.fn()
const mockFindLatestCompletedSession = vi.fn()
const mockCreateSession = vi.fn()
const mockRestoreSession = vi.fn()
const mockEndSession = vi.fn()
const mockAdvancePhase = vi.fn()

vi.mock('@/composables/useAiSession', () => ({
  useAiSession: () => ({
    currentSession: mockCurrentSession,
    sessions: ref([]),
    currentPhase: mockCurrentPhase,
    sessionMessages: ref([]),
    loadSessions: mockLoadSessions,
    createSession: mockCreateSession,
    restoreSession: mockRestoreSession,
    findActiveSession: mockFindActiveSession,
    findLatestCompletedSession: mockFindLatestCompletedSession,
    endSession: mockEndSession,
    advancePhase: mockAdvancePhase,
    loadMessages: vi.fn(),
    setPhase: vi.fn(),
    setCurrentSession: vi.fn()
  })
}))

const mockConnect = vi.fn()
const mockDisconnect = vi.fn()

vi.mock('@/composables/useAiEvents', () => ({
  useAiEvents: () => ({
    connected: ref(false),
    connect: mockConnect,
    disconnect: mockDisconnect,
    onForceUnlockRequest: vi.fn(),
    onForceUnlockResponse: vi.fn(),
    onWriteSuccess: vi.fn(),
    onWriteError: vi.fn()
  })
}))

vi.mock('@/composables/useAiChat', () => ({
  useAiChat: () => ({
    messages: ref([]),
    isStreaming: ref(false),
    streamingContent: ref(''),
    error: ref(null),
    canRetry: ref(false),
    sendMessage: vi.fn(),
    retry: vi.fn(),
    cancel: vi.fn(),
    onDocument: vi.fn(),
    onPhaseComplete: vi.fn(),
    onGeneratedData: vi.fn(),
    setMessages: vi.fn()
  })
}))

vi.mock('@/api/aiGeneration', () => ({
  aiGenerationApi: {
    applyGeneratedData: vi.fn(),
    getSessions: vi.fn(),
    getMessages: vi.fn()
  },
  AI_CHAT_STREAM_URL: '/api/v1/ai-generation/chat/stream',
  AI_EVENT_STREAM_URL: vi.fn((id: number) => `/api/v1/ai-generation/events/${id}`)
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), info: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('dayjs', () => ({
  default: vi.fn(() => ({ format: vi.fn(() => '2026-01-01 00:00:00') }))
}))

// Stub components
const globalStubs = {
  Teleport: { template: '<div><slot /></div>' },
  Transition: { template: '<div><slot /></div>' },
  ChatDialog: { template: '<div class="chat-dialog" />', props: ['functionUnitId', 'sessionId', 'phase', 'mode', 'completedPhases', 'initialMessages'] },
  DocumentPanel: { template: '<div class="document-panel" />', props: ['functionUnitId'] },
  'el-button': {
    template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
    props: ['icon', 'circle', 'size', 'type'],
    emits: ['click']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>', props: ['size', 'color'] },
  Close: { template: '<span />' },
  Lock: { template: '<span />' },
  MagicStick: { template: '<span />' },
  Loading: { template: '<span />' },
  FullScreen: { template: '<span />' },
  ScaleToOriginal: { template: '<span />' },
  'el-tooltip': { template: '<span><slot /></span>', props: ['content'] }
}

describe('AiPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLockConflict.value = false
    mockConflictLockInfo.value = null
    mockIsLocked.value = false
    mockCurrentSession.value = null
    mockCurrentPhase.value = 'REQUIREMENTS'
  })

  function mountComponent(visible = false) {
    return shallowMount(AiPanel, {
      props: { functionUnitId: 1, visible },
      global: { stubs: globalStubs, plugins: [i18n] }
    })
  }

  it('should not render panel content when visible is false', () => {
    const wrapper = mountComponent(false)
    expect(wrapper.find('.ai-panel').exists()).toBe(false)
  })

  it('should render panel when visible is true', () => {
    const wrapper = mountComponent(true)
    expect(wrapper.find('.ai-panel').exists()).toBe(true)
  })

  it('should render header with title', () => {
    const wrapper = mountComponent(true)
    expect(wrapper.find('.ai-panel__header').exists()).toBe(true)
    expect(wrapper.text()).toContain('AI 生成助手')
  })

  it('should show loading state initially', () => {
    const wrapper = mountComponent(true)
    // ready is false by default, so loading should show
    expect(wrapper.find('.ai-panel__loading').exists()).toBe(true)
  })

  it('should show lock conflict overlay when lockConflict is true', async () => {
    mockLockConflict.value = true
    mockConflictLockInfo.value = { userName: 'Other User', lockedAt: '2026-01-01T00:00:00Z' } as any

    const wrapper = mountComponent(true)
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.ai-panel__lock-overlay').exists()).toBe(true)
    expect(wrapper.text()).toContain('功能单元已被锁定')
  })

  it('should emit update:visible false when close button is clicked', async () => {
    mockIsLocked.value = false
    const wrapper = mountComponent(true)

    // Find close button in header-actions (second button, after detach toggle)
    const headerActions = wrapper.find('.ai-panel__header-actions')
    const buttons = headerActions.findAll('.el-button')
    expect(buttons.length).toBe(2)
    // Close button is the last one
    await buttons[1].trigger('click')

    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible')![0]).toEqual([false])
  })

  it('should render panel with Teleport wrapper when visible', () => {
    const wrapper = mountComponent(true)
    expect(wrapper.find('.ai-panel').exists()).toBe(true)
  })
})
