/**
 * Stable identity for one sub-table control on a form canvas. It is deliberately
 * independent of bindingId: multiple controls may render the same binding with
 * different batch capabilities.
 */
export function createBatchControlId(): string {
  const randomUuid = globalThis.crypto?.randomUUID
  if (typeof randomUuid === 'function') {
    return `stbc_${randomUuid.call(globalThis.crypto)}`
  }

  const bytes = new Uint8Array(16)
  globalThis.crypto?.getRandomValues?.(bytes)
  const suffix = [...bytes].map((value) => value.toString(16).padStart(2, '0')).join('')
  if (!suffix || /^0+$/.test(suffix)) {
    throw new Error('Secure randomness is required to create a batch control id')
  }
  return `stbc_${suffix}`
}
