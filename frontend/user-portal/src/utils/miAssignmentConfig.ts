/**
 * Portal mirror of:
 * frontend/developer-workstation/src/utils/miAssignmentConfig.ts
 *
 * Keep the contract and pure helpers aligned across both applications.
 */
export interface AssignmentConfig {
  allowUser: boolean
  allowRole: boolean
  assigneeField?: string
  roleField?: string
  buField?: string
}

export type AssignmentMode = 'person' | 'role'
export type MiAssignmentsMap = Record<string, AssignmentConfig>

function configuredField(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  const trimmed = value.trim()
  return trimmed || undefined
}

export function normalizeAssignmentConfig(value: unknown): AssignmentConfig | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
  const raw = value as Record<string, unknown>
  return {
    allowUser: raw.allowUser === true,
    allowRole: raw.allowRole === true,
    assigneeField: configuredField(raw.assigneeField),
    roleField: configuredField(raw.roleField),
    buField: configuredField(raw.buField),
  }
}

export function isAssignmentConfigured(config?: AssignmentConfig | null): boolean {
  if (!config) return false
  if (!config.allowUser && !config.allowRole) return false
  if (config.allowUser && !configuredField(config.assigneeField)) return false
  if (config.allowRole && !configuredField(config.roleField)) return false
  return true
}

export function shouldShowAssignModeRadio(config?: AssignmentConfig | null): boolean {
  return isAssignmentConfigured(config) && config?.allowUser === true && config.allowRole === true
}

/**
 * Field order inside the Assignment Mode container — fixed, not designer-authored.
 * BU precedes Role because picking a BU narrows the role list; assignee is the
 * single field of the person branch.
 */
export function assignmentChildFieldOrder(config: AssignmentConfig): string[] {
  return [config.assigneeField, config.buField, config.roleField]
    .filter((field): field is string => !!configuredField(field))
}

export function hasMiAssignmentMarker(
  fields?: Array<{
    type?: string
    children?: unknown[]
    tabs?: Array<{ fields?: unknown[] }>
    collapsePanels?: Array<{ fields?: unknown[] }>
  }>,
): boolean {
  for (const field of fields || []) {
    if (field.type === 'miAssignment') return true
    if (hasMiAssignmentMarker(field.children as Parameters<typeof hasMiAssignmentMarker>[0])) return true
    if (field.tabs?.some(tab =>
      hasMiAssignmentMarker(tab.fields as Parameters<typeof hasMiAssignmentMarker>[0]))) return true
    if (field.collapsePanels?.some(panel =>
      hasMiAssignmentMarker(panel.fields as Parameters<typeof hasMiAssignmentMarker>[0]))) return true
  }
  return false
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
  return fields.filter((field): field is string => !!configuredField(field))
}

export function resolveAssignModeFromRow(
  row: Record<string, unknown>,
  config: AssignmentConfig,
): AssignmentMode {
  if (config.allowRole) {
    const roleValue = config.roleField ? row[config.roleField] : undefined
    const buValue = config.buField ? row[config.buField] : undefined
    if (hasValue(roleValue) || hasValue(buValue) || !config.allowUser) return 'role'
  }
  return 'person'
}

export function fieldsHiddenByMode(
  mode: AssignmentMode,
  config: AssignmentConfig,
): Set<string> {
  const hidden = new Set<string>()
  if (mode === 'person') {
    if (config.roleField) hidden.add(config.roleField)
    if (config.buField) hidden.add(config.buField)
  } else if (config.assigneeField) {
    hidden.add(config.assigneeField)
  }
  return hidden
}

export function resolveAssignmentConfigForBinding(
  map: unknown,
  binding: { tableName?: string; physicalTableName?: string },
): AssignmentConfig | undefined {
  if (!map || typeof map !== 'object' || Array.isArray(map)) return undefined
  const assignments = map as Record<string, unknown>
  const names = [binding.physicalTableName, binding.tableName]
    .map(name => configuredField(name))
    .filter((name): name is string => !!name)
  for (const name of names) {
    const direct = normalizeAssignmentConfig(assignments[name])
    if (direct) return direct
    const matchedKey = Object.keys(assignments).find(key => key.toLowerCase() === name.toLowerCase())
    const matched = matchedKey ? normalizeAssignmentConfig(assignments[matchedKey]) : undefined
    if (matched) return matched
  }
  return undefined
}

export function attachAssignmentConfigsToBindings<T extends {
  tableName?: string
  physicalTableName?: string
  assignmentConfig?: AssignmentConfig
}>(bindings: T[], map: unknown): T[] {
  for (const binding of bindings) {
    const config = resolveAssignmentConfigForBinding(map, binding)
    if (config) binding.assignmentConfig = config
    else delete binding.assignmentConfig
  }
  return bindings
}

export function stampAssignmentConfigsOnForms(
  forms: unknown,
  map: unknown,
): void {
  if (!Array.isArray(forms)) return
  for (const form of forms) {
    if (!form || typeof form !== 'object') continue
    const bindings = (form as { tableBindings?: unknown }).tableBindings
    if (!Array.isArray(bindings)) continue
    const typedBindings = bindings as Array<{
      tableName?: string
      tableDisplayName?: string
      physicalTableName?: string
      assignmentConfig?: AssignmentConfig
    }>
    for (const binding of typedBindings) {
      if (!binding.physicalTableName) binding.physicalTableName = binding.tableName
      if (!binding.tableName && binding.tableDisplayName) binding.tableName = binding.tableDisplayName
    }
    attachAssignmentConfigsToBindings(typedBindings, map)
  }
}

function hasValue(value: unknown): boolean {
  if (value == null) return false
  if (typeof value === 'object') return Object.keys(value as object).length > 0
  return String(value).trim() !== ''
}
