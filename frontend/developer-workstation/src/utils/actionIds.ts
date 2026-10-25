import type { ActionDefinition } from '@/api/functionUnit'

/** Decode current JSON arrays and the legacy bracketed / comma-separated ID format. */
export function parseActionIds(value: unknown): Array<ActionDefinition['id']> {
  if (value == null || value === '') return []
  let ids: unknown = value
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return []
    try {
      ids = JSON.parse(text)
    } catch {
      // FALLBACK(migration): historical BPMN stores unquoted string IDs, with optional brackets.
      const legacy = text.startsWith('[') && text.endsWith(']') ? text.slice(1, -1) : text
      if (/[[\]{}"']/.test(legacy)) throw new Error('Invalid action ID list')
      ids = legacy.split(',').map(id => id.trim())
    }
  }
  if (!Array.isArray(ids) || ids.some(id =>
    !((typeof id === 'string' && id.trim() !== '') || (typeof id === 'number' && Number.isFinite(id))))) {
    throw new Error('Invalid action ID list')
  }
  return ids.map(id => typeof id === 'string' ? id.trim() : id)
}
