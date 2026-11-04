import type { BatchEventSession, BatchRule } from './rowEvents'
export interface BatchEventContext {
  version(): number
  session(): BatchEventSession | undefined
  beforeFetch(config: unknown, data: unknown): unknown
  pending?(): boolean
  clearAssignment(): boolean | Promise<boolean>
  change(field: string, value: unknown): boolean | Promise<boolean>
  event(rule: BatchRule, name: string, args: unknown[], kind?: 'on' | 'hook'): unknown
}
