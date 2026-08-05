import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useAiSession } from '@/composables/useAiSession'
import type { AiSession } from '@/types/aiGeneration'

const mockGetSessions = vi.fn()
const mockGetMessages = vi.fn()
const mockGetDocumentVersions = vi.fn()

vi.mock('@/api/aiGeneration', () => ({
  aiGenerationApi: {
    getSessions: (...args: any[]) => mockGetSessions(...args),
    getMessages: (...args: any[]) => mockGetMessages(...args),
    getDocumentVersions: (...args: any[]) => mockGetDocumentVersions(...args),
  }
}))

/** 造一份文档版本记录，只填本用例关心的字段。 */
function doc(id: number, documentType: string, version: number, content: string) {
  return {
    id, functionUnitId: 1, documentType, version, content,
    createdBy: 'developer', createdAt: '2026-08-02T09:00:00Z'
  }
}

describe('useAiSession', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should initialize with default state', () => {
    const { currentSession, sessions, currentPhase, sessionMessages } = useAiSession()

    expect(currentSession.value).toBeNull()
    expect(sessions.value).toEqual([])
    expect(currentPhase.value).toBe('REQUIREMENTS')
    expect(sessionMessages.value).toEqual([])
  })

  it('createSession should reset state for new session', () => {
    const session = useAiSession()

    // Set some state first
    session.currentPhase.value = 'DESIGN'

    session.createSession(1, 'NEW')

    expect(session.currentSession.value).toBeNull()
    expect(session.currentPhase.value).toBe('REQUIREMENTS')
    expect(session.sessionMessages.value).toEqual([])
  })

  it('loadSessions should fetch and store sessions', async () => {
    const mockSessions: AiSession[] = [
      {
        sessionId: 'session-1',
        functionUnitId: 1,
        currentPhase: 'DESIGN',
        mode: 'NEW',
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T01:00:00Z'
      }
    ]
    mockGetSessions.mockResolvedValue({ data: mockSessions })

    const { sessions, loadSessions } = useAiSession()
    await loadSessions(1)

    expect(sessions.value).toEqual(mockSessions)
    expect(mockGetSessions).toHaveBeenCalledWith(1)
  })

  it('findActiveSession should return active session for functionUnitId', async () => {
    const activeSess: AiSession = {
      sessionId: 'active-1',
      functionUnitId: 1,
      currentPhase: 'REQUIREMENTS',
      mode: 'NEW',
      status: 'ACTIVE',
      createdAt: '',
      updatedAt: ''
    }
    const completedSess: AiSession = {
      sessionId: 'completed-1',
      functionUnitId: 1,
      currentPhase: 'GENERATION',
      mode: 'NEW',
      status: 'COMPLETED',
      createdAt: '',
      updatedAt: ''
    }
    mockGetSessions.mockResolvedValue({ data: [activeSess, completedSess] })

    const { loadSessions, findActiveSession } = useAiSession()
    await loadSessions(1)

    const result = findActiveSession(1)
    expect(result).toEqual(activeSess)
  })

  it('findActiveSession should return null when no active session', async () => {
    const completedSess: AiSession = {
      sessionId: 'completed-1',
      functionUnitId: 1,
      currentPhase: 'GENERATION',
      mode: 'NEW',
      status: 'COMPLETED',
      createdAt: '',
      updatedAt: ''
    }
    mockGetSessions.mockResolvedValue({ data: [completedSess] })

    const { loadSessions, findActiveSession } = useAiSession()
    await loadSessions(1)

    expect(findActiveSession(1)).toBeNull()
  })

  it('findLatestCompletedSession should return completed session', async () => {
    const completedSess: AiSession = {
      sessionId: 'completed-1',
      functionUnitId: 1,
      currentPhase: 'GENERATION',
      mode: 'NEW',
      status: 'COMPLETED',
      createdAt: '',
      updatedAt: ''
    }
    mockGetSessions.mockResolvedValue({ data: [completedSess] })

    const { loadSessions, findLatestCompletedSession } = useAiSession()
    await loadSessions(1)

    expect(findLatestCompletedSession(1)).toEqual(completedSess)
  })

  it('restoreSession should set current session and load messages', async () => {
    const session: AiSession = {
      sessionId: 'session-1',
      functionUnitId: 1,
      currentPhase: 'DESIGN',
      mode: 'MODIFY',
      status: 'ACTIVE',
      createdAt: '',
      updatedAt: ''
    }
    const mockMessages = [
      { id: 1, sessionId: 'session-1', role: 'USER', content: 'Hi', phase: 'REQUIREMENTS', createdAt: '' },
      { id: 2, sessionId: 'session-1', role: 'ASSISTANT', content: 'Hello', phase: 'REQUIREMENTS', createdAt: '' }
    ]
    mockGetMessages.mockResolvedValue({ data: { content: mockMessages } })

    const { currentSession, currentPhase, restoreSession } = useAiSession()
    const msgs = await restoreSession(session)

    expect(currentSession.value).toEqual(session)
    expect(currentPhase.value).toBe('DESIGN')
    expect(msgs).toEqual(mockMessages)
  })

  it('advancePhase should transition REQUIREMENTS -> DESIGN', () => {
    const { currentPhase, advancePhase } = useAiSession()

    expect(currentPhase.value).toBe('REQUIREMENTS')

    const result = advancePhase()
    expect(result).toBe(true)
    expect(currentPhase.value).toBe('DESIGN')
  })

  it('advancePhase should transition DESIGN -> GENERATION', () => {
    const { currentPhase, advancePhase } = useAiSession()

    currentPhase.value = 'DESIGN'
    const result = advancePhase()
    expect(result).toBe(true)
    expect(currentPhase.value).toBe('GENERATION')
  })

  it('advancePhase should return false at GENERATION (no next phase)', () => {
    const { currentPhase, advancePhase } = useAiSession()

    currentPhase.value = 'GENERATION'
    const result = advancePhase()
    expect(result).toBe(false)
    expect(currentPhase.value).toBe('GENERATION')
  })

  it('endSession should clear all state', () => {
    const { currentSession, currentPhase, sessionMessages, endSession } = useAiSession()

    currentSession.value = { sessionId: 's1' } as AiSession
    currentPhase.value = 'DESIGN'
    sessionMessages.value = [{ id: 1 } as any]

    endSession()

    expect(currentSession.value).toBeNull()
    expect(currentPhase.value).toBe('REQUIREMENTS')
    expect(sessionMessages.value).toEqual([])
  })

  it('setPhase should update currentPhase', () => {
    const { currentPhase, setPhase } = useAiSession()

    setPhase('GENERATION')
    expect(currentPhase.value).toBe('GENERATION')
  })

  it('setCurrentSession should update currentSession', () => {
    const session: AiSession = {
      sessionId: 'test',
      functionUnitId: 1,
      currentPhase: 'REQUIREMENTS',
      mode: 'NEW',
      status: 'ACTIVE',
      createdAt: '',
      updatedAt: ''
    }
    const { currentSession, setCurrentSession } = useAiSession()

    setCurrentSession(session)
    expect(currentSession.value).toEqual(session)

    setCurrentSession(null)
    expect(currentSession.value).toBeNull()
  })

  /**
   * 重开面板必须把需求/设计文档以卡片形式放回聊天区。
   *
   * 模型把整段回答写进文档标记时，后端有意不写 ASSISTANT 消息（避免空气泡），
   * 于是那一轮只剩用户自己那句话；文档一直在 dw_ai_documents 里，缺的只是把它取回来这一步。
   */
  it('loadInlineDocuments should restore the latest version of each document type', async () => {
    mockGetDocumentVersions.mockImplementation((_fuId: number, type: string) =>
      Promise.resolve({
        data: type === 'REQUIREMENTS'
          ? [doc(5, 'REQUIREMENTS', 1, 'req v1'), doc(9, 'REQUIREMENTS', 2, 'req v2')]
          : [doc(6, 'DESIGN', 1, 'design v1')]
      }))

    const { loadInlineDocuments } = useAiSession()
    const restored = await loadInlineDocuments(1)

    // version/generatedAt 必须一起带回来：文档卡靠它显示 "v2 · 09:00"，重开面板后没有这两个字段
    // 用户就无从判断手上这份是不是自己刚重出过的那一版。
    expect(restored).toEqual([
      { id: 9, documentType: 'REQUIREMENTS', content: 'req v2', version: 2, generatedAt: '2026-08-02T09:00:00Z' },
      { id: 6, documentType: 'DESIGN', content: 'design v1', version: 1, generatedAt: '2026-08-02T09:00:00Z' }
    ])
  })

  it('loadInlineDocuments should skip document types that do not exist yet', async () => {
    mockGetDocumentVersions.mockImplementation((_fuId: number, type: string) =>
      Promise.resolve({ data: type === 'REQUIREMENTS' ? [doc(5, 'REQUIREMENTS', 1, 'req v1')] : [] }))

    const { loadInlineDocuments } = useAiSession()

    expect(await loadInlineDocuments(1)).toEqual([
      { id: 5, documentType: 'REQUIREMENTS', content: 'req v1', version: 1, generatedAt: '2026-08-02T09:00:00Z' }
    ])
  })

  /** 单个文档取不回来不该让整个面板打不开——其余文档照常恢复。 */
  it('loadInlineDocuments should survive a failing document request', async () => {
    mockGetDocumentVersions.mockImplementation((_fuId: number, type: string) =>
      type === 'REQUIREMENTS'
        ? Promise.reject(new Error('boom'))
        : Promise.resolve({ data: [doc(6, 'DESIGN', 1, 'design v1')] }))

    const { loadInlineDocuments } = useAiSession()

    expect(await loadInlineDocuments(1)).toEqual([
      { id: 6, documentType: 'DESIGN', content: 'design v1', version: 1, generatedAt: '2026-08-02T09:00:00Z' }
    ])
  })
})
