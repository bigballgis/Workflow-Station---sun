import { USER_ID_KEY } from '@/api/auth'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
function storageKey(task: string): string {
  return `ws_up_completion:${encodeURIComponent(localStorage.getItem(USER_ID_KEY) ?? '')}:${encodeURIComponent(task)}`
}
export function savedCompletionOperationId(task: string): string | null {
  const stored = sessionStorage.getItem(storageKey(task))
  if (stored !== null && !UUID.test(stored)) throw new Error('Invalid saved completion operation identity')
  return stored
}
/** Contains no form values. Retain the operation identity across retries, edits and page reloads. */
export function completionOperationId(task: string, supplied?: string): string {
  if (supplied) return supplied
  const stored = savedCompletionOperationId(task)
  if (stored !== null) return stored
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hex = [...bytes].map(value => value.toString(16).padStart(2, '0')).join('')
  const operation = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  sessionStorage.setItem(storageKey(task), operation)
  return operation
}
export function forgetLegacyCompletion(task: string): void { sessionStorage.removeItem(storageKey(task)) }
export class CompletionDeliveryUnknown extends Error {
  constructor(readonly operationId: string) { super('Completion delivery is not confirmed') }
}
export class CompletionRejected extends Error {
  constructor(readonly response: { status: number; data?: unknown }) { super('Task completion was rejected') }
}
