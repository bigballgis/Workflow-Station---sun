export type PkGenerationStrategy = 'manual' | 'uuid' | 'autoIncrement' | 'prefixedSequence'

export interface PkGenerationConfig {
  strategy?: PkGenerationStrategy
  startValue?: number
  padWidth?: number
  prefix?: string
}

const PK_GENERATION_STRATEGIES: PkGenerationStrategy[] = [
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
