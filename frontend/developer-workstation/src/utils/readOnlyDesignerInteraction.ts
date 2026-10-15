const ALLOWED_SELECTOR = [
  '[data-read-only-allowed]',
  '.el-tabs__header',
  '.el-tabs__nav-wrap',
  '.el-pagination',
  '.properties-panel-toggle',
  '.back-btn',
].join(', ')

/** Controls that change the design. Inspection clicks (rows, canvas, cards) stay open. */
const BLOCK_SELECTOR = [
  'button',
  '.el-button',
  'input',
  'textarea',
  'select',
  '.el-input',
  '.el-textarea',
  '.el-select',
  '.el-checkbox',
  '.el-switch',
  '.el-radio',
  '.el-upload',
  '.djs-palette',
  '.djs-context-pad',
  '[contenteditable="true"]',
].join(', ')

export function isReadOnlyAllowedTarget(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return false
  return Boolean(target.closest(ALLOWED_SELECTOR))
}

/**
 * Read-only designers keep the same layout as edit mode. Clicks that only reveal
 * detail (table rows, form cards, BPMN shapes, tabs) go through. Save and other
 * mutating controls stay blocked; Save itself is also disabled in the template.
 */
export function blockReadOnlyDesignerInteraction(event: Event): void {
  if (isReadOnlyAllowedTarget(event.target)) return
  if (event.type === 'dragstart' || event.type === 'drop') {
    event.preventDefault()
    event.stopImmediatePropagation()
    return
  }
  const el = event.target instanceof Element ? event.target : null
  if (!el?.closest(BLOCK_SELECTOR)) return
  event.preventDefault()
  event.stopImmediatePropagation()
}
