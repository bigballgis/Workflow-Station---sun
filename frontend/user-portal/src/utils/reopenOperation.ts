import { USER_ID_KEY } from '@/api/auth'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
function key(source: string) { return `ws_up_reopen:${encodeURIComponent(localStorage.getItem(USER_ID_KEY) ?? '')}:${encodeURIComponent(source)}` }
export function savedReopenOperation(source: string): string | null {
  const value = sessionStorage.getItem(key(source))
  if (value !== null && !UUID.test(value)) throw new Error('Invalid saved reopen operation')
  return value
}
export function reopenOperation(source: string): string {
  const stored = savedReopenOperation(source)
  if (stored) return stored
  const operation = crypto.randomUUID()
  sessionStorage.setItem(key(source), operation)
  return operation
}
export function forgetReopenOperation(source: string) { sessionStorage.removeItem(key(source)) }
