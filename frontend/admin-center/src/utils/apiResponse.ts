/**
 * Unwrap platform-common ApiResponse `{ success, data }` to the inner payload.
 * List endpoints may return bare DTOs; this helper is a no-op in that case.
 */
export function unwrapApiData<T>(body: unknown): T {
  if (
    body != null &&
    typeof body === 'object' &&
    (body as { success?: boolean }).success === true &&
    'data' in body
  ) {
    return (body as { data: T }).data
  }
  return body as T
}
