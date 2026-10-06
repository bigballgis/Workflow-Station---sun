import type { SlaConfig } from '@/api/functionUnit'

/** [row identity, fieldName] for every field row of the table being edited. */
export type FieldNamePair = readonly [unknown, string | undefined]

/**
 * Returns the SLA mapping with its field names following renames made in the field grid, so renaming
 * the start or due date field does not leave the mapping pointing at a name that no longer exists
 * (the save would then be rejected). Rows are matched by their stable client identity; a field that
 * was removed is not guessed at — the mapping keeps the old name and the save names it.
 */
export function followRenamedSlaFields(
  config: SlaConfig | null | undefined,
  prev: readonly FieldNamePair[],
  next: readonly FieldNamePair[],
): SlaConfig | null | undefined {
  if (!config) return config
  const renames = new Map<string, string>()
  const nextNameById = new Map(next.map(([id, name]) => [id, name]))
  for (const [id, oldName] of prev) {
    const newName = nextNameById.get(id)
    if (id != null && oldName && newName && newName !== oldName) {
      renames.set(oldName, newName)
    }
  }
  if (!renames.size) return config
  const follow = (name?: string | null) => (name && renames.get(name)) || name
  const dueDateField = follow(config.dueDateField) as string
  const startDateField = follow(config.startDateField)
  if (dueDateField === config.dueDateField && startDateField === config.startDateField) return config
  return { ...config, dueDateField, startDateField }
}
