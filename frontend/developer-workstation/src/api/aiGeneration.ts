import api from './index'
import { readAmToken } from '@/utils/amToken'
import type {
  LockInfo,
  AiSession,
  AiMessage,
  AiDocument,
  AiDocumentType,
  ApplyGeneratedDataRequest,
  PageResponse
} from '@/types/aiGeneration'

export const aiGenerationApi = {
  acquireLock: (functionUnitId: number) =>
    api.post<any, { data: LockInfo }>(`/ai-generation/lock/${functionUnitId}`),

  releaseLock: (functionUnitId: number) =>
    api.delete(`/ai-generation/lock/${functionUnitId}`),

  requestForceUnlock: (functionUnitId: number) =>
    api.post(`/ai-generation/lock/${functionUnitId}/force-unlock-request`),

  respondForceUnlock: (functionUnitId: number, accept: boolean) =>
    api.post(`/ai-generation/lock/${functionUnitId}/force-unlock-response`, { accept }),

  getSessions: (functionUnitId: number) =>
    api.get<any, { data: AiSession[] }>('/ai-generation/sessions', { params: { functionUnitId } }),

  getMessages: (sessionId: string, params?: { page?: number; size?: number }) =>
    api.get<any, { data: PageResponse<AiMessage> }>(`/ai-generation/sessions/${sessionId}/messages`, { params }),

  applyGeneratedData: (functionUnitId: number, data: ApplyGeneratedDataRequest) =>
    api.post(`/ai-generation/${functionUnitId}/apply`, data),

  undoLastApply: (functionUnitId: number) =>
    api.post(`/ai-generation/${functionUnitId}/undo`),

  getDocumentVersions: (functionUnitId: number, documentType: AiDocumentType) =>
    api.get<any, { data: AiDocument[] }>(`/ai-generation/documents`, {
      params: { functionUnitId, documentType }
    }),

  getDocumentByVersion: (functionUnitId: number, documentType: AiDocumentType, version: number) =>
    api.get<any, { data: AiDocument }>(`/ai-generation/documents/version`, {
      params: { functionUnitId, documentType, version }
    }),

  saveDocument: (functionUnitId: number, documentType: AiDocumentType, content: string) =>
    api.post<any, { data: AiDocument }>('/ai-generation/documents', {
      functionUnitId, documentType, content
    }),

  updateSessionPhase: (sessionId: string, phase: string) =>
    api.put(`/ai-generation/sessions/${sessionId}/phase`, null, { params: { phase } }),

  /**
   * AI Studio Copilot 单轮对话（顾问式，无会话/锁/文档）。
   * 模型链路与 AI Generate 同源：AMToken 经 X-AM-Token 头透传，读不到就不带，
   * 后端以 AI_GATEWAY_TOKEN_MISSING 显式失败（dev 配静态 key 时无需 token）。
   */
  studioChat: (data: AiStudioChatPayload) => {
    const amToken = readAmToken()
    return api.post<any, { data: { reply: string } }>(
      '/ai-generation/studio-chat',
      data,
      amToken ? { headers: { 'X-AM-Token': amToken } } : undefined
    )
  },
}

export interface AiStudioChatPayload {
  functionUnitId: number
  phase: string
  message: string
  history: { role: 'USER' | 'ASSISTANT'; content: string }[]
}

// SSE endpoint URLs (used by composables with fetch API, not axios)
export const AI_CHAT_STREAM_URL = '/api/v1/ai-generation/chat/stream'
export const AI_EVENT_STREAM_URL = (functionUnitId: number) =>
  `/api/v1/ai-generation/events/${functionUnitId}`
