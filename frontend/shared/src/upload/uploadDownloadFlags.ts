/** True only when the designer switch is explicitly on; missing/legacy = download allowed. */
export function isCannotDownload(value: unknown): boolean {
  return value === true || value === 'true' || value === 1
}

/** Designer switch `cannotDownload` and form-create native `canNotDownload`. */
export function uploadPropsBlockDownload(props: Record<string, unknown> | null | undefined): boolean {
  if (!props) return false
  return isCannotDownload(props.cannotDownload) || isCannotDownload(props.canNotDownload)
}
