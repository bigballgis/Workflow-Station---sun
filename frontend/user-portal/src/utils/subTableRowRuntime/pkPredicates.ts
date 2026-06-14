/**
 * Sub-table row runtime — auto primary-key strategy predicates (shared, FK/PK hot path).
 * Used by both column presentation and PK allocation; kept standalone to avoid import cycles.
 */
import { normalizeFieldDefinitionForRuntime, type RuntimeFieldDefinition } from '../formFieldMeta'
import { type PkGenerationConfig } from '../tableFkRuntime'
import type { BindingFieldDefinition } from './types'

export function pkNeedsAllocation(field: BindingFieldDefinition): boolean {
  const normalized = normalizeFieldDefinitionForRuntime(field as RuntimeFieldDefinition)
  if (!normalized.isPrimaryKey) return false
  const pkConfig = normalized.pkGeneration ?? normalized.pkGenerationJson
  const strategy = (pkConfig as PkGenerationConfig | undefined)?.strategy ?? 'uuid'
  return strategy !== 'manual'
}

/** prefixedSequence / uuid allocate string values — inputNumber cannot bind them. */
export function pkAllocationYieldsString(field: BindingFieldDefinition): boolean {
  const normalized = normalizeFieldDefinitionForRuntime(field as RuntimeFieldDefinition)
  if (!normalized.isPrimaryKey) return false
  const pkConfig = normalized.pkGeneration ?? normalized.pkGenerationJson
  const strategy = (pkConfig as PkGenerationConfig | undefined)?.strategy ?? 'uuid'
  return strategy === 'uuid' || strategy === 'prefixedSequence'
}
