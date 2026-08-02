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
  regenerateScope?: string
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
  decisionDefinitions?: any[]
  tableRelations?: any[]
  processDefinition?: any
  icon?: any
  explanations?: Record<string, string>
}

export interface AiQualityScore {
  totalScore: number
  dimensions: Record<string, number>
  suggestions: string[]
}

export interface GenerationPreviewData {
  tableCount: number
  totalFieldCount: number
  formCount: number
  actionCount: number
  actionTypes: string[]
  processNodeCount: number
  processGatewayCount: number
  decisionCount: number
  tableRelationCount: number
  qualityScore?: AiQualityScore
  iconSvg?: string
}

export interface ApplyGeneratedDataRequest {
  sessionId: string
  generatedData: AiGeneratedData
  regenerateScope?: string
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

export type ViewMode = 'xml' | 'markdown' | 'process' | 'table'

export interface InlineDocument {
  id: number
  documentType: AiDocumentType
  content: string
}

/** Diff result for comparing current vs generated data */
export interface DiffItem {
  type: string
  name: string
  changes?: string[]
}

export interface DiffResult {
  added: DiffItem[]
  removed: DiffItem[]
  modified: DiffItem[]
}

/**
 * Compute diff between current and generated AiGeneratedData.
 * Categorizes entities as added (green), removed (red), or modified (yellow).
 */
export function computeDiff(current: AiGeneratedData, generated: AiGeneratedData): DiffResult {
  const diff: DiffResult = { added: [], removed: [], modified: [] }

  // Table definitions diff
  diffEntities(
    current.tableDefinitions || [],
    generated.tableDefinitions || [],
    'tableName',
    'table',
    diff,
    (cur, gen) => computeTableFieldDiff(cur, gen)
  )

  // Form definitions diff
  diffEntities(
    current.formDefinitions || [],
    generated.formDefinitions || [],
    'formName',
    'form',
    diff,
    (cur, gen) => {
      const changes: string[] = []
      if (cur.formType !== gen.formType) changes.push(`formType: ${cur.formType} → ${gen.formType}`)
      return changes
    }
  )

  // Action definitions diff
  diffEntities(
    current.actionDefinitions || [],
    generated.actionDefinitions || [],
    'actionName',
    'action',
    diff,
    (cur, gen) => {
      const changes: string[] = []
      if (cur.actionType !== gen.actionType) changes.push(`actionType: ${cur.actionType} → ${gen.actionType}`)
      return changes
    }
  )

  // Decision definitions diff
  diffEntities(
    current.decisionDefinitions || [],
    generated.decisionDefinitions || [],
    'decisionKey',
    'decision',
    diff
  )

  // Table relations diff
  diffEntities(
    current.tableRelations || [],
    generated.tableRelations || [],
    (r: Record<string, unknown>) => `${r.sourceTableName}-${r.relationType}-${r.targetTableName}`,
    'tableRelation',
    diff
  )

  return diff
}

function diffEntities(
  currentList: Record<string, unknown>[],
  generatedList: Record<string, unknown>[],
  nameKeyOrFn: string | ((item: Record<string, unknown>) => string),
  entityType: string,
  diff: DiffResult,
  changeDetector?: (cur: Record<string, unknown>, gen: Record<string, unknown>) => string[]
): void {
  const getName = typeof nameKeyOrFn === 'function'
    ? nameKeyOrFn
    : (item: Record<string, unknown>) => String(item[nameKeyOrFn] || '')

  const currentMap = new Map<string, Record<string, unknown>>()
  for (const item of currentList) {
    currentMap.set(getName(item), item)
  }

  const generatedMap = new Map<string, Record<string, unknown>>()
  for (const item of generatedList) {
    generatedMap.set(getName(item), item)
  }

  for (const [name, genItem] of generatedMap) {
    if (!currentMap.has(name)) {
      diff.added.push({ type: entityType, name })
    } else if (changeDetector) {
      const changes = changeDetector(currentMap.get(name)!, genItem)
      if (changes.length > 0) {
        diff.modified.push({ type: entityType, name, changes })
      }
    } else {
      // Simple JSON comparison
      if (JSON.stringify(currentMap.get(name)) !== JSON.stringify(genItem)) {
        diff.modified.push({ type: entityType, name })
      }
    }
  }

  for (const [name] of currentMap) {
    if (!generatedMap.has(name)) {
      diff.removed.push({ type: entityType, name })
    }
  }
}

function computeTableFieldDiff(cur: Record<string, unknown>, gen: Record<string, unknown>): string[] {
  const changes: string[] = []
  const curFields = (cur.fieldDefinitions || []) as Record<string, unknown>[]
  const genFields = (gen.fieldDefinitions || []) as Record<string, unknown>[]

  const curFieldMap = new Map(curFields.map(f => [String(f.fieldName), f]))
  const genFieldMap = new Map(genFields.map(f => [String(f.fieldName), f]))

  for (const [name] of genFieldMap) {
    if (!curFieldMap.has(name)) changes.push(`+ field: ${name}`)
  }
  for (const [name] of curFieldMap) {
    if (!genFieldMap.has(name)) changes.push(`- field: ${name}`)
  }
  for (const [name, genField] of genFieldMap) {
    const curField = curFieldMap.get(name)
    if (curField) {
      const curType = String(curField.dataType || curField.fieldType || '')
      const genType = String(genField.dataType || genField.fieldType || '')
      if (curType !== genType) changes.push(`~ field ${name}: ${curType} → ${genType}`)
    }
  }

  return changes
}
