/**
 * Error toast for a failed action, unless `api/request.ts` has already dealt with the failure.
 *
 * The request layer toasts every HTTP, network and configuration failure itself and then rejects with an
 * `ApiError`; a cancelled request, and a 401 that sends the user to the login page, are rejected as the raw axios
 * error on purpose, without a toast. A caller that toasts any of these shows the failure twice, or over the login
 * redirect. Only an error thrown by the caller's own code is still unreported, and gets `fallback`.
 */
import { ApiError } from '@/types/errors'
import { isAbortError } from '@/api/isAbortError'
import { notifyError } from '@/utils/notify'

export function notifyUnreportedError(error: unknown, fallback: string): void {
  if (handledByRequestLayer(error)) return
  notifyError(fallback)
}

function handledByRequestLayer(error: unknown): boolean {
  return error instanceof ApiError
    || isAbortError(error)
    || (error as { response?: unknown } | null)?.response !== undefined
}
