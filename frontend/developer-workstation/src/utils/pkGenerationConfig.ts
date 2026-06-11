export type PkGenerationStrategy = 'manual' | 'uuid' | 'autoIncrement' | 'prefixedSequence'

export interface PkGenerationConfig {
  strategy?: PkGenerationStrategy
  startValue?: number
  padWidth?: number
  prefix?: string
}

export const PK_GENERATION_STRATEGIES: PkGenerationStrategy[] = [
  'uuid',
  'manual',
  'autoIncrement',
  'prefixedSequence',
]

export function parsePkGeneration(
  raw?: Record<string, unknown> | PkGenerationConfig | null,
): PkGenerationConfig {
  if (!raw || typeof raw !== 'object') {
    return { strategy: 'uuid' }
  }
  const strategy = raw.strategy as PkGenerationStrategy | undefined
  return {
    strategy: PK_GENERATION_STRATEGIES.includes(strategy as PkGenerationStrategy)
      ? strategy
      : 'uuid',
    startValue: typeof raw.startValue === 'number' ? raw.startValue : 1,
    padWidth: typeof raw.padWidth === 'number' ? raw.padWidth : 6,
    prefix: typeof raw.prefix === 'string' ? raw.prefix : '',
  }
}

export function serializePkGeneration(
  config?: PkGenerationConfig | Record<string, unknown> | null,
  isPrimaryKey?: boolean,
): Record<string, unknown> | undefined {
  if (!isPrimaryKey) return undefined
  const parsed = parsePkGeneration(config)
  if (parsed.strategy === 'manual') {
    return { strategy: 'manual' }
  }
  if (parsed.strategy === 'uuid') {
    return { strategy: 'uuid' }
  }
  const out: Record<string, unknown> = {
    strategy: parsed.strategy,
    scope: 'perTable',
    startValue: parsed.startValue ?? 1,
  }
  if (parsed.strategy === 'prefixedSequence') {
    out.padWidth = parsed.padWidth ?? 6
    out.prefix = parsed.prefix ?? ''
  }
  return out
}

export function pkGenerationNeedsExtraConfig(strategy?: PkGenerationStrategy): boolean {
  return strategy === 'autoIncrement' || strategy === 'prefixedSequence'
}
