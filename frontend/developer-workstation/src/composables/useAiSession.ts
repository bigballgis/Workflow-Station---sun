import { ref } from 'vue'
import type { AiSession, AiMessage, AiPhase, AiMode } from '@/types/aiGeneration'
import { aiGenerationApi } from '@/api/aiGeneration'

const PHASE_ORDER: AiPhase[] = ['REQUIREMENTS', 'DESIGN', 'GENERATION']

/**
 * Composable for managing AI session lifecycle:
 * create, restore, end, phase switching, and history viewing.
 */
export function useAiSession() {
  const currentSession = ref<AiSession | null>(null)
  const sessions = ref<AiSession[]>([])
  const currentPhase = ref<AiPhase>('REQUIREMENTS')
  const sessionMessages = ref<AiMessage[]>([])

  async function loadSessions(functionUnitId: number): Promise<void> {
    const response = await aiGenerationApi.getSessions(functionUnitId)
    sessions.value = response.data
  }

  async function loadMessages(sessionId: string): Promise<AiMessage[]> {
    const response = await aiGenerationApi.getMessages(sessionId, { page: 0, size: 1000 })
    sessionMessages.value = response.data.content
    return sessionMessages.value
  }

  /**
   * Session creation is handled by the chat stream endpoint on the backend.
   * This method prepares the local state for a new session.
   */
  function createSession(_functionUnitId: number, _mode: AiMode): void {
    currentSession.value = null
    currentPhase.value = 'REQUIREMENTS'
    sessionMessages.value = []
  }

  /**
   * Restore an existing session: set current session, phase, and load messages.
   */
  async function restoreSession(session: AiSession): Promise<AiMessage[]> {
    currentSession.value = session
    currentPhase.value = session.currentPhase
    return await loadMessages(session.sessionId)
  }

  /**
   * Find an active or recent completed session for the given function unit.
   */
  function findActiveSession(functionUnitId: number): AiSession | null {
    return sessions.value.find(
      s => s.functionUnitId === functionUnitId && s.status === 'ACTIVE'
    ) || null
  }

  function findLatestCompletedSession(functionUnitId: number): AiSession | null {
    return sessions.value.find(
      s => s.functionUnitId === functionUnitId && s.status === 'COMPLETED'
    ) || null
  }

  function endSession(): void {
    currentSession.value = null
    currentPhase.value = 'REQUIREMENTS'
    sessionMessages.value = []
  }

  function advancePhase(): boolean {
    const currentIndex = PHASE_ORDER.indexOf(currentPhase.value)
    if (currentIndex < PHASE_ORDER.length - 1) {
      currentPhase.value = PHASE_ORDER[currentIndex + 1]
      return true
    }
    return false
  }

  function setPhase(phase: AiPhase): void {
    currentPhase.value = phase
  }

  function setCurrentSession(session: AiSession | null): void {
    currentSession.value = session
  }

  return {
    currentSession,
    sessions,
    currentPhase,
    sessionMessages,
    loadSessions,
    loadMessages,
    createSession,
    restoreSession,
    findActiveSession,
    findLatestCompletedSession,
    endSession,
    advancePhase,
    setPhase,
    setCurrentSession
  }
}
