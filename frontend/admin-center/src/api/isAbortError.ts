/**
 * Axios abort / cancel. Keep in sync with the response interceptor skip-notify path.
 */
export function isAbortError(error: unknown): boolean {
  return typeof error === 'object' && error !== null
    && ((error as { code?: string }).code === 'ERR_CANCELED'
      || (error as { name?: string }).name === 'CanceledError')
}
