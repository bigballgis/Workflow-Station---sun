const ALLOWED_SELECTOR = '[data-read-only-allowed], .el-tabs__header, .el-tabs__nav-wrap'

export function isReadOnlyAllowedTarget(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return false
  return Boolean(target.closest(ALLOWED_SELECTOR))
}

/**
 * Capture-phase handler for the designer when the current Function Unit is
 * read-only. Inspection controls marked `data-read-only-allowed` (zoom, fit,
 * validate, export, debug, version compare/export) and tab headers stay usable.
 */
export function blockReadOnlyDesignerInteraction(event: Event): void {
  if (isReadOnlyAllowedTarget(event.target)) return
  event.preventDefault()
  event.stopImmediatePropagation()
}
