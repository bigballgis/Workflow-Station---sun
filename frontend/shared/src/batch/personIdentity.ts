/** Lookup objects remain available to events; assignment intent contains only identity. */
export function batchPersonIdentity(value: unknown): string {
  if (value == null || value === '') return ''
  const identity = typeof value === 'object' && !Array.isArray(value) && 'id' in value ? value.id : value
  if (typeof identity === 'string' && identity.trim()) return identity.trim()
  throw new Error('subTable.batchAssignmentRequired')
}
