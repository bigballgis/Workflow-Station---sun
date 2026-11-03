import type { BatchEventSession, BatchRule } from './rowEvents'
export interface BatchEventContext {
  version(): number
  session(): BatchEventSession | undefined
  beforeFetch(config: unknown, data: unknown): unknown
  clearAssignment(): boolean
  change(field: string, value: unknown): boolean
  event(rule: BatchRule, name: string, args: unknown[], kind?: 'on' | 'hook'): void
}
