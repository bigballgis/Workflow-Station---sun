const NAME_REGEX = /^[a-zA-Z][a-zA-Z0-9_]*$/

/**
 * Convert a human-readable display name into a valid field identifier.
 * PRD: docs/table-design-fk-pk-requirements.md §4.2
 */
export function slugFieldName(displayName: string): string {
  let slug = String(displayName ?? '')
    .trim()
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toLowerCase()

  if (!slug) return 'field'
  if (!/^[a-zA-Z]/.test(slug)) slug = `f_${slug}`
  return slug
}

/** Ensure slug is unique within existing field names (case-insensitive). */
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

/** Table technical name from display name (uniqueness within the function unit). */
export function suggestTableName(displayName: string, existingNames: string[] = []): string {
  const slug = slugFieldName(displayName)
  const base = slug === 'field' ? 'table' : slug
  return uniquifyFieldName(base, existingNames)
}

export function isValidFieldName(name: string): boolean {
  return NAME_REGEX.test(name)
}
