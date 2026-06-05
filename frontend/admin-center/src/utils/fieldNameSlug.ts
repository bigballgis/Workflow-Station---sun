const NAME_REGEX = /^[a-z][a-z0-9_]*$/

/**
 * Convert a human-readable display name into a valid Relation Table field name.
 * PRD: docs/table-design-fk-pk-requirements.md §4.2 (AC uses lowercase snake_case).
 */
export function slugFieldName(displayName: string): string {
  let slug = String(displayName ?? '')
    .trim()
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toLowerCase()

  if (!slug) return 'field'
  if (!/^[a-z]/.test(slug)) slug = `f_${slug}`
  return slug
}

export function uniquifyFieldName(base: string, existingNames: string[]): string {
  const taken = new Set(existingNames.map(n => n.toLowerCase()).filter(Boolean))
  let candidate = base
  let suffix = 2
  while (taken.has(candidate.toLowerCase())) {
    candidate = `${base}_${suffix}`
    suffix += 1
  }
  return candidate
}

export function suggestFieldName(displayName: string, existingNames: string[]): string {
  return uniquifyFieldName(slugFieldName(displayName), existingNames)
}

/** Relation table technical name from display name (create flow; uniqueness enforced by backend). */
export function suggestTableName(displayName: string): string {
  const slug = slugFieldName(displayName)
  return slug === 'field' ? 'table' : slug
}

export function isValidFieldName(name: string): boolean {
  return NAME_REGEX.test(name)
}
