export type AiPhase = 'REQUIREMENTS' | 'DESIGN' | 'GENERATION'
export type AiMode = 'NEW' | 'MODIFY'
export type AiSessionStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
export type AiMessageRole = 'USER' | 'ASSISTANT'

export interface AiChatRequest {
  functionUnitId: number
  sessionId?: string
  message: string
  phase: AiPhase
  mode: AiMode
}

export interface AiSseEvent {
  eventType: string
  data: any
}

export interface LockInfo {
  functionUnitId: number
  userId: string
  userName: string
  lockedAt: string
  locked: boolean
}

export interface AiSession {
  sessionId: string
  functionUnitId: number
  currentPhase: AiPhase
  mode: AiMode
  status: AiSessionStatus
  createdAt: string
  updatedAt: string
}

export interface AiMessage {
  id: number
  sessionId: string
  role: AiMessageRole
  content: string
  phase: AiPhase
  createdAt: string
}

export interface AiGeneratedData {
  name?: string
  description?: string
  tableDefinitions?: any[]
  formDefinitions?: any[]
  actionDefinitions?: any[]
  processDefinition?: any
  icon?: any
}

export interface GenerationPreviewData {
  tableCount: number
  totalFieldCount: number
  formCount: number
  actionCount: number
  actionTypes: string[]
  processNodeCount: number
  processGatewayCount: number
  iconSvg?: string
}

export interface ApplyGeneratedDataRequest {
  sessionId: string
  generatedData: AiGeneratedData
}

export interface AiValidationError {
  errorType: string
  fieldPath: string
  description: string
}

export type AiDocumentType = 'REQUIREMENTS' | 'DESIGN'

export interface AiDocument {
  id: number
  functionUnitId: number
  documentType: AiDocumentType
  version: number
  content: string
  summary?: string
  createdBy: string
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type ViewMode = 'xml' | 'markdown'

export interface InlineDocument {
  id: number
  documentType: AiDocumentType
  content: string
}
