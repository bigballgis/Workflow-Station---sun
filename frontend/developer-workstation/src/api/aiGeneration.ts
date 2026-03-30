import api from './index'
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
}

// SSE endpoint URLs (used by composables with fetch API, not axios)
export const AI_CHAT_STREAM_URL = '/api/v1/ai-generation/chat/stream'
export const AI_EVENT_STREAM_URL = (functionUnitId: number) =>
  `/api/v1/ai-generation/events/${functionUnitId}`
