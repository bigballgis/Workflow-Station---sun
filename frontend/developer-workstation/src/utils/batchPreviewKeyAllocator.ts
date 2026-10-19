import { formatPkGenerationPreview, parsePkGeneration, PK_GENERATION_STRATEGIES } from './pkGenerationConfig'
import type { AllocatePrimaryKeysFn, BindingFieldDefinition } from './subTableRowRuntime'

/** Isolated sample counters, never server reservations; formats use the existing designer preview formatter. */
export function createBatchPreviewKeyAllocator(
  fieldsByTable: Record<number, BindingFieldDefinition[]>, rowsByTable: Record<number, Array<Record<string, unknown>>>,
): AllocatePrimaryKeysFn {
  const nextByField = new Map<string, number>()
  const usedByField = new Map<string, Set<string>>()
  return async ({ tableId, fieldName, count = 1 }) => {
    const field = fieldsByTable[tableId]?.find(item => item.fieldName === fieldName)
    if (!field?.isPrimaryKey || !Number.isInteger(count) || count < 1 || count > 200) throw new Error('form.batchFormInvalid')
    const raw = field.pkGeneration ?? field.pkGenerationJson
    if (raw?.strategy != null && !PK_GENERATION_STRATEGIES.includes(raw.strategy) && raw.strategy !== 'datePrefixedSequence') {
      throw new Error('form.batchFormInvalid')
    }
    const config = parsePkGeneration(raw)
    if (config.strategy === 'manual') throw new Error('form.batchFormInvalid')
    const key = `${tableId}:${fieldName}`
    const used = usedByField.get(key) ?? new Set((rowsByTable[tableId] ?? []).map(row => String(row[fieldName])))
    const values: string[] = []
    let next = nextByField.get(key) ?? config.startValue ?? 1
    while (values.length < count) {
      const value = config.strategy === 'uuid' ? crypto.randomUUID() : config.strategy === 'autoIncrement' ? String(next)
        : formatPkGenerationPreview({ ...config, startValue: next })
      if (!value || !Number.isSafeInteger(next)) throw new Error('form.batchFormInvalid')
      next++
      if (used.has(value)) continue
      used.add(value); values.push(value)
    }
    nextByField.set(key, next); usedByField.set(key, used)
    return values
  }
}
