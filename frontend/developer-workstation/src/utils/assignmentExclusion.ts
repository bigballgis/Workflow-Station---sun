import type { AssignmentExclusionDraft } from '@/types/assignmentExclusion'

export function emptyAssignmentExclusion(): AssignmentExclusionDraft {
  return { schemaVersion: 1, excludeInitiator: false, excludePreviousHandlers: false,
    excludeSelectedNodeHandlers: false, selectedNodeKeys: [], backup: {
      enabled: false, mode: 'BU_ROLE', buSource: 'CASE_START_BU', businessUnitCode: '', roleCodes: [], userRefs: []
    } }
}

function object(value: unknown, keys: string[]): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid assignment exclusion object')
  const record = value as Record<string, unknown>
  if (Object.keys(record).some(key => !keys.includes(key))) throw new Error('Unknown assignment exclusion field')
  return record
}
function boolean(value: unknown): boolean {
  if (typeof value !== 'boolean') throw new Error('Invalid assignment exclusion boolean')
  return value
}
function strings(value: unknown): string[] {
  if (!Array.isArray(value) || value.length > 1000 || value.some(item => typeof item !== 'string' || !item.trim() || item.length > 255)) {
    throw new Error('Invalid assignment exclusion references')
  }
  const result = (value as string[]).map(item => item.trim())
  if (new Set(result).size !== result.length) throw new Error('Duplicate assignment exclusion references')
  return result
}

/** Invalid persisted data stays visible as an error; absence alone means default-off. */
export function parseAssignmentExclusion(raw: unknown): AssignmentExclusionDraft {
  if (raw === undefined) return emptyAssignmentExclusion()
  if (typeof raw === 'string' && raw.length > 32768) throw new Error('Oversized assignment exclusion policy')
  const value = object(typeof raw === 'string' ? JSON.parse(raw) as unknown : raw,
    ['schemaVersion', 'excludeInitiator', 'excludePreviousHandlers', 'excludeSelectedNodeHandlers', 'selectedNodeKeys', 'backup'])
  if (value.schemaVersion !== 1) throw new Error('Unsupported assignment exclusion version')
  const draft = emptyAssignmentExclusion()
  draft.excludeInitiator = boolean(value.excludeInitiator)
  draft.excludePreviousHandlers = boolean(value.excludePreviousHandlers)
  draft.excludeSelectedNodeHandlers = boolean(value.excludeSelectedNodeHandlers)
  draft.selectedNodeKeys = strings(value.selectedNodeKeys)
  if (!draft.excludeSelectedNodeHandlers && draft.selectedNodeKeys.length) throw new Error('Inactive assignment node references')
  const backup = object(value.backup, ['enabled', 'mode', 'buSource', 'businessUnitCode', 'roleCodes', 'userRefs'])
  draft.backup.enabled = boolean(backup.enabled)
  if (!draft.backup.enabled) return draft
  if (backup.mode !== 'BU_ROLE' && backup.mode !== 'USERS') throw new Error('Invalid assignment backup mode')
  draft.backup.mode = backup.mode
  if (backup.mode === 'USERS') {
    if (['buSource', 'businessUnitCode', 'roleCodes'].some(key => key in backup)) throw new Error('Inactive backup source fields')
    draft.backup.userRefs = strings(backup.userRefs)
  } else {
    if ('userRefs' in backup) throw new Error('Inactive backup user references')
    if (backup.buSource !== 'CASE_START_BU' && backup.buSource !== 'NODE_ASSIGNMENT_BU' && backup.buSource !== 'FIXED_BU') {
      throw new Error('Invalid assignment backup BU source')
    }
    draft.backup.buSource = backup.buSource
    draft.backup.roleCodes = strings(backup.roleCodes)
    if (backup.buSource === 'FIXED_BU') {
      if (typeof backup.businessUnitCode !== 'string') throw new Error('Invalid assignment backup BU code')
      draft.backup.businessUnitCode = backup.businessUnitCode
    } else if ('businessUnitCode' in backup) throw new Error('Inactive backup fixed BU')
  }
  return draft
}

export function assignmentExclusionEnabled(draft: AssignmentExclusionDraft): boolean {
  return draft.excludeInitiator || draft.excludePreviousHandlers || draft.excludeSelectedNodeHandlers
}

export function assignmentExclusionProblem(draft: AssignmentExclusionDraft, nodeIds: ReadonlySet<string>, nodeBuSupported: boolean): string {
  if (draft.excludeSelectedNodeHandlers && (!draft.selectedNodeKeys.length || draft.selectedNodeKeys.some(id => !nodeIds.has(id)))) return 'selectNodes'
  if (!draft.backup.enabled) return ''
  if (!assignmentExclusionEnabled(draft)) return 'selectRule'
  if (draft.backup.mode === 'USERS') return draft.backup.userRefs.length ? '' : 'selectUsers'
  if (draft.backup.buSource === 'NODE_ASSIGNMENT_BU' && !nodeBuSupported) return 'invalidNodeBu'
  if (draft.backup.buSource === 'FIXED_BU' && !draft.backup.businessUnitCode.trim()) return 'selectBu'
  return draft.backup.roleCodes.length ? '' : 'selectRoles'
}

export function serializeAssignmentExclusion(draft: AssignmentExclusionDraft): string {
  const enabled = assignmentExclusionEnabled(draft) && draft.backup.enabled
  const backup = !enabled ? { enabled: false } : draft.backup.mode === 'USERS'
    ? { enabled: true, mode: 'USERS', userRefs: [...draft.backup.userRefs] }
    : { enabled: true, mode: 'BU_ROLE', buSource: draft.backup.buSource, roleCodes: [...draft.backup.roleCodes],
      ...(draft.backup.buSource === 'FIXED_BU' ? { businessUnitCode: draft.backup.businessUnitCode } : {}) }
  return JSON.stringify({ schemaVersion: 1, excludeInitiator: draft.excludeInitiator, excludePreviousHandlers: draft.excludePreviousHandlers,
    excludeSelectedNodeHandlers: draft.excludeSelectedNodeHandlers,
    selectedNodeKeys: draft.excludeSelectedNodeHandlers ? [...draft.selectedNodeKeys] : [], backup })
}
