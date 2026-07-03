export type LookupFilterMatchType = 'eq' | 'contains' | 'startsWith' | 'endsWith'

export interface LookupFilterCondition {
  fieldName: string
  value: string
  matchType?: LookupFilterMatchType
}
