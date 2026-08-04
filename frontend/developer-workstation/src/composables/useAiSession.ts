import { ref } from 'vue'
import type {
  AiSession, AiMessage, AiPhase, AiMode, AiDocumentType, InlineDocument
} from '@/types/aiGeneration'
import { aiGenerationApi } from '@/api/aiGeneration'

const PHASE_ORDER: AiPhase[] = ['REQUIREMENTS', 'DESIGN', 'GENERATION']

/** 会在聊天里以卡片形式回显的文档类型，按产出先后排列。 */
const INLINE_DOCUMENT_TYPES: AiDocumentType[] = ['REQUIREMENTS', 'DESIGN']

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

  /**
   * 重开面板时把已产出的需求/设计文档取回来，供聊天区以卡片回显。
   *
   * 为什么需要它：模型把整段回答放进文档标记里时，`reply` 去掉标记后是空串，
   * 后端据此**有意不写** ASSISTANT 消息（避免堆空气泡，见 AiGenerationComponentImpl 的注释）。
   * 于是重开后那一轮只剩用户自己那句话，AI 侧一片空白，对话看着像断了——
   * 文档本身一直好好存在 dw_ai_documents 里，只是没人把它放回聊天里。
   *
   * 文档是按功能单元存的（不区分会话），与右侧面板、阶段推断读的是同一份数据；
   * 因此换会话看到的仍是该功能单元最新的那版，这与既有行为一致。
   * 取每种类型的最新版本：历史版本在右侧面板的 Version History 里查，聊天区不做版本堆叠。
   */
  async function loadInlineDocuments(functionUnitId: number): Promise<InlineDocument[]> {
    const restored: InlineDocument[] = []
    for (const documentType of INLINE_DOCUMENT_TYPES) {
      try {
        const response = await aiGenerationApi.getDocumentVersions(functionUnitId, documentType)
        const versions = response.data ?? []
        if (!versions.length) continue
        const latest = versions.reduce((a, b) => (a.version >= b.version ? a : b))
        restored.push({
          id: latest.id,
          documentType,
          content: latest.content,
          version: latest.version,
          generatedAt: latest.createdAt
        })
      } catch (err) {
        // 单个文档取不回来不该让整个面板打不开：其余文档与消息照常恢复，这里留痕即可。
        console.error(`Failed to restore ${documentType} document into the chat:`, err)
      }
    }
    return restored
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
      // 注意：不再调用后端 updateSessionPhase，因为后端在 phaseComplete 时已自动推进
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
    loadInlineDocuments,
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
