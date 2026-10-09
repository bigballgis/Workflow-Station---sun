export type VersionCompareModuleKey =
  | 'BASIC' | 'PROCESS' | 'TABLES' | 'FORMS' | 'VIEWS' | 'ACTIONS'
  | 'AUTOMATION' | 'CONNECTIONS' | 'EMAIL_TEMPLATES' | 'EMAIL_MONITORS'
  | 'DECISIONS' | 'DOCUMENTS'

export type VersionCompareStatus = 'COMPARED' | 'NOT_CAPTURED' | 'NOT_SNAPSHOTTED'
export type VersionSemanticStatus = VersionCompareStatus | 'UNPARSEABLE'
export type VersionChangeType = 'ADDED' | 'MODIFIED' | 'REMOVED'

export interface VersionCompareCounts {
  added: number
  modified: number
  removed: number
}

export interface VersionCompareChange {
  path: string
  type: VersionChangeType
  oldValue: string | null
  newValue: string | null
}

export interface VersionCompareModule {
  key: VersionCompareModuleKey
  status: VersionCompareStatus
  counts: VersionCompareCounts
  truncated: boolean
  changes: VersionCompareChange[]
  semantic?: VersionSemanticDiff
}

export interface VersionSemanticFieldChange {
  field: string
  oldValue: string | null
  newValue: string | null
  textContext?: { oldStart: number; newStart: number; oldLength: number; newLength: number; omittedChanges: boolean } | null
}

export interface VersionSemanticChange {
  objectType: string
  objectKey: string
  label: string
  type: VersionChangeType
  scope: 'DESIGN' | 'LAYOUT_ONLY' | 'REFERENCES_ONLY'
  fields: VersionSemanticFieldChange[]
}

export interface VersionSemanticDiff {
  status: VersionSemanticStatus
  scope: 'FULL' | 'PARTIAL' | 'REFERENCES_ONLY' | 'NONE'
  counts: VersionCompareCounts
  truncated: boolean
  items: VersionSemanticChange[]
}

export interface VersionCompareResponse {
  baseVersion: { id: number; versionNumber: string; publishedAt: string }
  targetVersion: { id: number; versionNumber: string; publishedAt: string }
  totals: VersionCompareCounts
  displayTotals?: VersionCompareCounts
  modules: VersionCompareModule[]
}
