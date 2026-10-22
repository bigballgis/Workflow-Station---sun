/** Server-owned fields returned with process variables are never user form input. */
const runtimeVersionContextKeys = new Set([
  'functionUnitId', 'functionUnitCatalogId', 'functionUnitCode', 'functionUnitVersionLabel',
  'functionUnitVersionId', 'processDefinitionId', 'expectedProcessDefinitionId',
])

export function withoutRuntimeVersionContext(values: Record<string, unknown>): Record<string, unknown> {
  // Only the root variable map carries the context; nested business rows retain their own fields.
  return Object.fromEntries(Object.entries(values).filter(([key]) => !runtimeVersionContextKeys.has(key)))
}
