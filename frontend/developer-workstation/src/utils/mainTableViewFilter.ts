export type FilterLogic = 'and' | 'or'

export type FilterOperator =
  | 'eq'
  | 'ne'
  | 'contains'
  | 'notContains'
  | 'startsWith'
  | 'notStartsWith'
  | 'endsWith'
  | 'notEndsWith'
  | 'isNull'
  | 'isNotNull'
  | 'gt'
  | 'lt'

export interface FilterCondition {
  fieldName: string
  operator: string
  value?: string | null
  systemField?: boolean
}

export interface FilterGroup {
  logic: FilterLogic
  conditions?: FilterCondition[]
  groups?: FilterGroup[]
}

/** Persisted filter config (API / deploy snapshot). */
export interface FilterConfig {
  logic?: FilterLogic
  conditions?: FilterCondition[]
  groups?: FilterGroup[]
}

/** Editor tree node with stable ids for Vue keys. */
export interface FilterConditionEditorRow extends FilterCondition {
  id: string
}

export interface FilterGroupEditorNode {
  id: string
  logic: FilterLogic
  conditions: FilterConditionEditorRow[]
  groups: FilterGroupEditorNode[]
}

export interface FilterFieldOption {
  fieldName: string
  label: string
  systemField?: boolean
}

let filterNodeSeq = 0

export function nextFilterNodeId(prefix: string): string {
  filterNodeSeq += 1
  return `${prefix}-${filterNodeSeq}`
}

export function createEmptyFilterGroup(logic: FilterLogic = 'and'): FilterGroupEditorNode {
  return {
    id: nextFilterNodeId('fg'),
    logic,
    conditions: [],
    groups: [],
  }
}

export function createDefaultFilterCondition(
  fieldOptions: FilterFieldOption[],
): FilterConditionEditorRow {
  const first = fieldOptions[0]
  return {
    id: nextFilterNodeId('fc'),
    fieldName: first?.fieldName || 'process_status',
    operator: 'eq',
    value: '',
    systemField: first?.systemField,
  }
}

function cloneEditorGroup(raw: FilterGroup): FilterGroupEditorNode {
  return {
    id: nextFilterNodeId('fg'),
    logic: raw.logic === 'or' ? 'or' : 'and',
    conditions: (raw.conditions || []).map(c => ({
      ...c,
      id: nextFilterNodeId('fc'),
    })),
    groups: (raw.groups || []).map(cloneEditorGroup),
  }
}

/** Normalize legacy flat `conditions` or nested `groups` into editor root group. */
export function parseFilterConfigToEditorRoot(config?: FilterConfig | null): FilterGroupEditorNode {
  if (!config) {
    return createEmptyFilterGroup('and')
  }
  if (config.groups?.length) {
    return {
      id: nextFilterNodeId('root'),
      logic: config.logic === 'or' ? 'or' : 'and',
      conditions: (config.conditions || []).map(c => ({
        ...c,
        id: nextFilterNodeId('fc'),
      })),
      groups: config.groups.map(cloneEditorGroup),
    }
  }
  return {
    id: nextFilterNodeId('root'),
    logic: config.logic === 'or' ? 'or' : 'and',
    conditions: (config.conditions || []).map(c => ({
      ...c,
      id: nextFilterNodeId('fc'),
    })),
    groups: [],
  }
}

function stripConditionRow(row: FilterConditionEditorRow): FilterCondition {
  const { id: _id, ...rest } = row
  return { ...rest }
}

function serializeEditorGroup(group: FilterGroupEditorNode): FilterGroup {
  return {
    logic: group.logic,
    conditions: group.conditions.map(stripConditionRow),
    groups: group.groups.map(serializeEditorGroup),
  }
}

export function serializeFilterEditorRoot(root: FilterGroupEditorNode): FilterConfig {
  return {
    logic: root.logic,
    conditions: root.conditions.map(stripConditionRow),
    groups: root.groups.map(serializeEditorGroup),
  }
}

export function flattenFilterConditions(root: FilterGroupEditorNode): FilterCondition[] {
  const out: FilterCondition[] = []
  function walk(group: FilterGroupEditorNode) {
    for (const c of group.conditions) {
      out.push(stripConditionRow(c))
    }
    for (const g of group.groups) {
      walk(g)
    }
  }
  walk(root)
  return out
}

export function removeFieldFromFilterTree(
  root: FilterGroupEditorNode,
  fieldName: string,
): void {
  root.conditions = root.conditions.filter(c => c.fieldName !== fieldName)
  for (const g of root.groups) {
    removeFieldFromFilterTree(g, fieldName)
  }
}

export function operatorNeedsValue(operator: string): boolean {
  return operator !== 'isNull' && operator !== 'isNotNull'
}

export function removeFlattenedConditionAt(
  root: FilterGroupEditorNode,
  flatIndex: number,
): boolean {
  let cursor = 0
  function walk(group: FilterGroupEditorNode): boolean {
    for (let i = 0; i < group.conditions.length; i++) {
      if (cursor === flatIndex) {
        group.conditions.splice(i, 1)
        return true
      }
      cursor += 1
    }
    for (const child of group.groups) {
      if (walk(child)) return true
    }
    return false
  }
  return walk(root)
}

export const FILTER_OPERATOR_KEYS: FilterOperator[] = [
  'eq',
  'ne',
  'contains',
  'notContains',
  'startsWith',
  'notStartsWith',
  'endsWith',
  'notEndsWith',
  'isNotNull',
  'isNull',
]
