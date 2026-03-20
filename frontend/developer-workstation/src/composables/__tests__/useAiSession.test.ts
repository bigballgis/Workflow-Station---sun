import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useAiSession } from '@/composables/useAiSession'
import type { AiSession } from '@/types/aiGeneration'

const mockGetSessions = vi.fn()
const mockGetMessages = vi.fn()

vi.mock('@/api/aiGeneration', () => ({
  aiGenerationApi: {
    getSessions: (...args: any[]) => mockGetSessions(...args),
    getMessages: (...args: any[]) => mockGetMessages(...args),
  }
}))

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
})
