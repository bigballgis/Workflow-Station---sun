import type { InjectionKey, Ref } from 'vue'

/**
 * Developer Workstation source mirrored by:
 * frontend/user-portal/src/utils/miAssignmentConfig.ts
 *
 * Keep the AssignmentConfig contract and pure orchestration helpers aligned.
 */
export type AssignmentMode = 'person' | 'role'

export interface AssignmentConfig {
  allowUser: boolean
  allowRole: boolean
  assigneeField?: string
  roleField?: string
  buField?: string
}

export interface MiAssignmentEntry {
  subTableName: string
  nodeId: string
  nodeName: string
  config: AssignmentConfig
}

export interface MiAssignmentDiagnostic {
  code: 'CONFLICTING_MI_ASSIGNMENT_CONFIG'
  subTableName: string
  nodeIds: string[]
}

export interface MiAssignmentParseResult {
  configs: Record<string, AssignmentConfig>
  entries: MiAssignmentEntry[]
  diagnostics: MiAssignmentDiagnostic[]
}

export interface MiAssignmentBinding {
  bindingId: number
  tableName: string
}

export interface MiAssignmentGuardIssue {
  code: 'MISSING_MI_ASSIGNMENT_COMPONENT' | 'CONFLICTING_MI_ASSIGNMENT_CONFIG'
  subTableName: string
  nodeIds: string[]
}

export interface MiAssignmentGuardResult {
  blocking: MiAssignmentGuardIssue[]
  warnings: Array<{ code: 'ORPHAN_MI_ASSIGNMENT_COMPONENT'; bindingId: number; subTableName: string }>
}

export const MI_ASSIGNMENT_CONFIG_KEY: InjectionKey<Ref<AssignmentConfig | undefined>> =
  Symbol('miAssignmentConfig')

/**
 * Active mode + its setter, shared with the Assignment Mode container widget.
 *
 * form-create does NOT forward `rule.props` to components registered with a drag rule
 * declaring `input: false` — the widget receives only form-create's own internals
 * (`onFc.updateValue`, `onFc.el`, class/id/style). Verified at runtime: every attempt to
 * pass the mode as `modelValue`, `assignMode`, or a callback prop arrived as undefined.
 * provide/inject is therefore the only channel, and it is the same one already carrying
 * MI_ASSIGNMENT_CONFIG_KEY into this widget.
 */
export interface MiAssignmentModeContext {
  mode: Ref<AssignmentMode>
  setMode: (mode: AssignmentMode) => void
}

export const MI_ASSIGNMENT_MODE_KEY: InjectionKey<MiAssignmentModeContext> =
  Symbol('miAssignmentMode')

function normalizedField(value: unknown): string | undefined {
  const field = typeof value === 'string' ? value.trim() : ''
  return field || undefined
}

export function resolveAssignModeFromRow(
  row: Record<string, unknown>,
  config: AssignmentConfig,
): AssignmentMode {
  const roleValues = [config.roleField, config.buField]
    .filter((field): field is string => !!field)
    .map((field) => row[field])
  if (config.allowRole && roleValues.some(hasValue)) return 'role'
  return config.allowUser ? 'person' : 'role'
}

/**
 * Fields the Assignment Mode block renders inside itself for the active mode.
 *
 * The block owns these controls directly, so it stays complete even when the
 * sub-form design carries no `miAssignment` marker to anchor them (older FUs
 * were saved before the marker existed). Order is the reading order in the
 * block: BU narrows the role list, so it comes first.
 */
export function fieldsOwnedByMode(
  mode: AssignmentMode,
  config: AssignmentConfig,
): string[] {
  const fields = mode === 'person'
    ? [config.assigneeField]
    : [config.buField, config.roleField]
  return fields.filter((field): field is string => !!normalizedField(field))
}

export function fieldsHiddenByMode(
  mode: AssignmentMode,
  config: AssignmentConfig,
): Set<string> {
  const fields = mode === 'person'
    ? [config.roleField, config.buField]
    : [config.assigneeField]
  return new Set(fields.filter((field): field is string => !!field))
}

export function shouldShowAssignModeRadio(config?: AssignmentConfig): boolean {
  return config?.allowUser === true && config.allowRole === true
}

/**
 * Field order inside the Assignment Mode container — fixed, not designer-authored.
 * BU precedes Role because picking a BU narrows the role list; assignee is the
 * single field of the person branch. Both branches are nested so switching modes
 * only changes which children render, never where the block sits.
 */
export function assignmentChildFieldOrder(config: AssignmentConfig): string[] {
  return [config.assigneeField, config.buField, config.roleField]
    .filter((field): field is string => !!normalizedField(field))
}

/**
 * Wrap the owned fields of one rule level in a new container, positioned where the
 * first of them already sat. Returns null when this level owns none of them, so the
 * caller can keep looking (or leave the tree alone).
 */
function createContainerAtFirstOwned<T extends {
  type?: string
  field?: string
  children?: unknown[]
}>(list: T[], owned: Set<string>, order: string[]): T[] | null {
  const anchorAt = list.findIndex(rule => rule?.field && owned.has(rule.field))
  if (anchorAt < 0) return null
  const children = order
    .map(field => list.find(rule => rule?.field === field))
    .filter((rule): rule is T => !!rule)
  if (children.length === 0) return null

  const keptBefore = list
    .slice(0, anchorAt)
    .filter(rule => !(rule?.field && owned.has(rule.field)))
  const keptAfter = list
    .slice(anchorAt)
    .filter(rule => !(rule?.field && owned.has(rule.field)))
  // _miAdopted: adoption is one-time. Without it the next load would vacuum the
  // fields back in and undo any drag-out the author performed.
  const container = {
    type: 'miAssignment',
    title: 'Assignment Mode',
    props: {},
    _miAdopted: true,
    children,
  } as unknown as T
  return [...keptBefore, container, ...keptAfter]
}

/**
 * ONE-TIME adoption of the assignee / BU / role rules into the Assignment Mode
 * container, so a form that predates the container opens with them already grouped.
 *
 * The container is a normal drop container: after adoption the author owns the
 * membership and the order. Fields dragged OUT must stay out, so adoption is gated on
 * `_miAdopted` — a marker written onto the container the first time it happens. Without
 * that gate every load would vacuum the fields back in and silently undo the author's
 * layout. Re-ordering inside the container is likewise never imposed after adoption.
 *
 * Pure: returns new arrays rather than mutating the designer's live rule tree, and
 * returns the input array itself when nothing needed adopting.
 */
export function nestAssignmentFieldsIntoContainer<T extends {
  type?: string
  field?: string
  children?: unknown[]
  _miAdopted?: boolean
}>(
  rules: T[],
  config?: AssignmentConfig,
  options: {
    /**
     * Create the container at the first owned field when the tree has none.
     *
     * The designer canvas needs this: forms authored before the container existed
     * have the fields but no container rule, and leaving them loose is exactly the
     * scattered layout the container is meant to fix. Off by default so read-only
     * consumers never mutate a form's shape behind the author's back.
     */
    createIfMissing?: boolean
  } = {},
): T[] {
  if (!config || !isAssignmentConfigured(config)) return rules
  const order = assignmentChildFieldOrder(config)
  if (order.length === 0) return rules
  const owned = new Set(order)

  const walk = (list: T[]): T[] => {
    const container = list.find(rule => rule?.type === 'miAssignment')
    let changed = false
    const descended = list.map(rule => {
      if (!Array.isArray(rule?.children) || rule.type === 'miAssignment') return rule
      const children = walk(rule.children as T[])
      if (children === rule.children) return rule
      changed = true
      return { ...rule, children }
    })

    if (!container) {
      if (options.createIfMissing && !changed) {
        const created = createContainerAtFirstOwned(descended, owned, order)
        if (created) return created
      }
      return changed ? descended : list
    }

    // Already adopted → the author is in charge of membership and order from here on.
    if (container._miAdopted) return changed ? descended : list

    const loose = order
      .map(field => descended.find(rule => rule?.field === field))
      .filter((rule): rule is T => !!rule)
    if (loose.length === 0) {
      // Nothing loose to pull in; just record that adoption has run.
      return descended.map(rule => rule?.type === 'miAssignment'
        ? { ...rule, _miAdopted: true }
        : rule)
    }

    const existing = ((container.children ?? []) as T[])
    return descended
      .filter(rule => !(rule?.field && owned.has(rule.field)))
      .map(rule => rule?.type === 'miAssignment'
        ? { ...rule, _miAdopted: true, children: [...existing, ...loose] }
        : rule)
  }
  return walk(rules)
}

/**
 * Form Preview only demonstrates layout/interaction — BU/Role use placeholder sample
 * data (no admin-center query); real BU→Role cascade runs at user-portal runtime.
 * Single source so the Add dialog's picker and the sub-table list's label lookup
 * agree on the same value→label mapping (a row saved via the demo picker stores
 * one of these values, not a real BU/role code).
 */
export const DEMO_BU_OPTIONS = [
  { label: 'Sample Business Unit 1', value: '__demo_bu_1' },
  { label: 'Sample Business Unit 2', value: '__demo_bu_2' },
]
export const DEMO_ROLE_OPTIONS = [
  { label: 'Sample Role A', value: '__demo_role_a' },
  { label: 'Sample Role B', value: '__demo_role_b' },
]

export function isAssignmentConfigured(config?: AssignmentConfig): boolean {
  if (!config || (!config.allowUser && !config.allowRole)) return false
  if (config.allowUser && !normalizedField(config.assigneeField)) return false
  if (config.allowRole && !normalizedField(config.roleField)) return false
  return true
}

export function parseMiAssignmentsFromBpmn(xml?: string | null): MiAssignmentParseResult {
  const result: MiAssignmentParseResult = { configs: {}, entries: [], diagnostics: [] }
  if (!xml?.trim()) return result
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  if (document.querySelector('parsererror')) return result

  for (const node of elementsByLocalName(document, 'userTask')) {
    if (!isInsideMultiInstanceSubProcess(node)) continue
    const properties = readCustomProperties(node)
    const subTableName = normalizedField(properties.subTableName)
    const mode = normalizedField(properties.assigneeMode)
    if (!subTableName || !mode || !['user', 'role', 'both'].includes(mode)) continue
    result.entries.push({
      subTableName,
      nodeId: node.getAttribute('id') || '',
      nodeName: node.getAttribute('name') || node.getAttribute('id') || '',
      config: {
        allowUser: mode === 'user' || mode === 'both',
        allowRole: mode === 'role' || mode === 'both',
        assigneeField: normalizedField(properties.assigneeField),
        roleField: normalizedField(properties.roleField),
        buField: normalizedField(properties.buField),
      },
    })
  }

  for (const [subTableName, entries] of groupEntries(result.entries)) {
    const first = entries[0]
    if (!first) continue
    if (entries.some((entry) => !sameConfig(entry.config, first.config))) {
      result.diagnostics.push({
        code: 'CONFLICTING_MI_ASSIGNMENT_CONFIG',
        subTableName,
        nodeIds: entries.map((entry) => entry.nodeId),
      })
      continue
    }
    result.configs[subTableName] = first.config
  }
  return result
}

export function validateMiAssignmentComponents(
  parsed: MiAssignmentParseResult,
  bindings: MiAssignmentBinding[],
  configJson: Record<string, unknown>,
): MiAssignmentGuardResult {
  const blocking: MiAssignmentGuardIssue[] = parsed.diagnostics.map((diagnostic) => ({ ...diagnostic }))
  const warnings: MiAssignmentGuardResult['warnings'] = []
  const subForms = asRecord(configJson.subForms)

  for (const entry of parsed.entries) {
    if (!isAssignmentConfigured(entry.config)) continue
    const binding = bindings.find((item) => item.tableName === entry.subTableName)
    if (!binding || ruleContainsMiAssignment(subFormRule(subForms, binding.bindingId))) continue
    if (!blocking.some((issue) =>
      issue.code === 'MISSING_MI_ASSIGNMENT_COMPONENT' && issue.subTableName === entry.subTableName)) {
      blocking.push({
        code: 'MISSING_MI_ASSIGNMENT_COMPONENT',
        subTableName: entry.subTableName,
        nodeIds: parsed.entries
          .filter((item) => item.subTableName === entry.subTableName)
          .map((item) => item.nodeId),
      })
    }
  }

  for (const binding of bindings) {
    if (!ruleContainsMiAssignment(subFormRule(subForms, binding.bindingId))) continue
    if (!parsed.entries.some((entry) => entry.subTableName === binding.tableName)) {
      warnings.push({
        code: 'ORPHAN_MI_ASSIGNMENT_COMPONENT',
        bindingId: binding.bindingId,
        subTableName: binding.tableName,
      })
    }
  }
  return { blocking, warnings }
}

export function ruleContainsMiAssignment(rules: unknown): boolean {
  if (!Array.isArray(rules)) return false
  return rules.some((rule) => {
    const item = asRecord(rule)
    if (item.type === 'miAssignment') return true
    return Object.values(item).some((value) => Array.isArray(value) && ruleContainsMiAssignment(value))
  })
}

function subFormRule(subForms: Record<string, unknown>, bindingId: number): unknown {
  return asRecord(subForms[String(bindingId)]).rule
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function hasValue(value: unknown): boolean {
  if (value == null) return false
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'object') return Object.keys(value).length > 0
  return String(value).trim() !== ''
}

function elementsByLocalName(root: Document | Element, localName: string): Element[] {
  return Array.from(root.getElementsByTagName('*')).filter((element) => element.localName === localName)
}

function isInsideMultiInstanceSubProcess(node: Element): boolean {
  let current = node.parentElement
  while (current) {
    if (current.localName === 'subProcess') {
      return Array.from(current.children).some((child) => child.localName === 'multiInstanceLoopCharacteristics')
    }
    current = current.parentElement
  }
  return false
}

function readCustomProperties(node: Element): Record<string, string> {
  const result: Record<string, string> = {}
  for (const property of elementsByLocalName(node, 'property')) {
    if (property.closest('*[id]') !== node) continue
    const name = property.getAttribute('name')
    if (name) result[name] = property.getAttribute('value') || ''
  }
  return result
}

function groupEntries(entries: MiAssignmentEntry[]): Map<string, MiAssignmentEntry[]> {
  const grouped = new Map<string, MiAssignmentEntry[]>()
  for (const entry of entries) {
    grouped.set(entry.subTableName, [...(grouped.get(entry.subTableName) || []), entry])
  }
  return grouped
}

function sameConfig(left: AssignmentConfig, right: AssignmentConfig): boolean {
  return left.allowUser === right.allowUser
    && left.allowRole === right.allowRole
    && normalizedField(left.assigneeField) === normalizedField(right.assigneeField)
    && normalizedField(left.roleField) === normalizedField(right.roleField)
    && normalizedField(left.buField) === normalizedField(right.buField)
}
