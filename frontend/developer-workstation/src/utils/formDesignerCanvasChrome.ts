import { isFormCreateRuleHidden } from '@/utils/formCreateRuleUtils'

export type HiddenDesignerTarget =
  | { kind: 'field'; field: string; fcId?: string }
  | { kind: 'subTable'; bindingId: number; fcId?: string }
  | { kind: 'linkForm'; componentId: number; fcId?: string }

function targetKey(target: HiddenDesignerTarget): string {
  if (target.kind === 'field') return `field:${target.field}`
  if (target.kind === 'subTable') return `subTable:${target.bindingId}`
  return `linkForm:${target.componentId}`
}

function normalizeBindingId(raw: unknown): number | null {
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
}

/** Collect hidden rules — includes subTable/linkForm (no `field` on rule). */
export function collectHiddenDesignerTargets(rules: unknown[]): HiddenDesignerTarget[] {
  const targets: HiddenDesignerTarget[] = []
  const seen = new Set<string>()

  const walk = (items: unknown[]) => {
    if (!Array.isArray(items)) return
    for (const raw of items) {
      if (!raw || typeof raw !== 'object') continue
      const item = raw as Record<string, unknown>
      if (!isFormCreateRuleHidden(item)) {
        walk(item.children as unknown[])
        continue
      }

      const fcId = typeof item._fc_id === 'string' ? item._fc_id : undefined
      const props = (item.props as Record<string, unknown> | undefined) || {}
      const type = String(item.type ?? '')

      if (type === 'subTable') {
        const bindingId = normalizeBindingId(item._bindingId ?? props._bindingId)
        if (bindingId != null) {
          const key = targetKey({ kind: 'subTable', bindingId, fcId })
          if (!seen.has(key)) {
            seen.add(key)
            targets.push({ kind: 'subTable', bindingId, fcId })
          }
        }
      } else if (type === 'linkForm') {
        const componentId = normalizeBindingId(item._componentId ?? props._componentId)
        if (componentId != null) {
          const key = targetKey({ kind: 'linkForm', componentId, fcId })
          if (!seen.has(key)) {
            seen.add(key)
            targets.push({ kind: 'linkForm', componentId, fcId })
          }
        }
      } else if (item.field) {
        const field = String(item.field)
        const key = targetKey({ kind: 'field', field, fcId })
        if (!seen.has(key)) {
          seen.add(key)
          targets.push({ kind: 'field', field, fcId })
        }
      } else if (fcId) {
        const key = `fc:${fcId}`
        if (!seen.has(key)) {
          seen.add(key)
          targets.push({ kind: 'field', field: fcId, fcId })
        }
      }

      walk(item.children as unknown[])
    }
  }

  walk(rules)
  return targets
}

function escapeAttr(value: string): string {
  if (typeof CSS !== 'undefined' && CSS.escape) {
    return CSS.escape(value)
  }
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

const DRAG_TOOL_SELECTOR = '._fd-drag-tool, ._fd-drag-box, ._fd-drag-item'

function dragArea(el: HTMLElement): number {
  const w = el.offsetWidth || 0
  const h = el.offsetHeight || 0
  return w > 0 && h > 0 ? w * h : Number.MAX_SAFE_INTEGER
}

/** Prefer the innermost / smallest drag chrome so card parents are not concealed. */
export function pickInnermostDragTool(candidates: HTMLElement[]): HTMLElement | null {
  if (!candidates.length) return null
  return candidates.reduce((best, el) => {
    const bestArea = dragArea(best)
    const elArea = dragArea(el)
    if (elArea < bestArea) return el
    if (elArea === bestArea && best.contains(el)) return el
    return best
  })
}

function collectDragToolsContaining(
  wrapper: HTMLElement,
  predicate: (el: HTMLElement) => boolean,
): HTMLElement[] {
  const candidates: HTMLElement[] = []
  wrapper.querySelectorAll(DRAG_TOOL_SELECTOR).forEach((node) => {
    const el = node as HTMLElement
    if (predicate(el)) candidates.push(el)
  })
  return candidates
}

function isDragChromeElement(el: HTMLElement): boolean {
  return (
    el.classList.contains('_fd-drag-tool')
    || el.classList.contains('_fd-drag-box')
    || el.classList.contains('_fd-drag-item')
  )
}

/** All drag chrome ancestors from hit → wrapper; smallest wins (field inside card). */
function findInnermostDragFromHit(wrapper: HTMLElement, hit: Element): HTMLElement | null {
  const chain: HTMLElement[] = []
  let node: Element | null = hit
  while (node && node !== wrapper) {
    if (node instanceof HTMLElement && isDragChromeElement(node)) {
      chain.push(node)
    }
    node = node.parentElement
  }
  if (chain.length) return pickInnermostDragTool(chain)

  const formItem = hit.closest('.el-form-item') as HTMLElement | null
  if (formItem) {
    const drag = formItem.closest('._fd-drag-tool, ._fd-drag-box, ._fd-drag-item') as HTMLElement | null
    if (drag && wrapper.contains(drag)) return drag
    return formItem
  }
  return null
}

function pushDragFromHit(
  wrapper: HTMLElement,
  hit: Element | null,
  candidates: HTMLElement[],
): void {
  if (!hit) return
  const drag = findInnermostDragFromHit(wrapper, hit)
  if (drag && !candidates.includes(drag)) candidates.push(drag)
}

/** Resolve the draggable chrome element for a form-create designer field. */
export function findDesignerDragElement(
  wrapper: HTMLElement,
  target: HiddenDesignerTarget,
): HTMLElement | null {
  const candidates: HTMLElement[] = []

  if (target.fcId) {
    const id = escapeAttr(target.fcId)
    wrapper
      .querySelectorAll(`#${id}, [id="${id}"], [data-id="${id}"], [data-fc-id="${id}"]`)
      .forEach((node) => pushDragFromHit(wrapper, node, candidates))
  }

  if (target.kind === 'subTable') {
    pushDragFromHit(
      wrapper,
      wrapper.querySelector(`[data-fc-designer-binding-id="${target.bindingId}"]`),
      candidates,
    )
  }

  if (target.kind === 'linkForm') {
    pushDragFromHit(
      wrapper,
      wrapper.querySelector(`[data-fc-designer-link-form-id="${target.componentId}"]`),
      candidates,
    )
  }

  if (target.kind === 'field') {
    const field = escapeAttr(target.field)
    wrapper
      .querySelectorAll(
        `[field="${field}"], [name="${field}"], input[name="${field}"], textarea[name="${field}"]`,
      )
      .forEach((node) => pushDragFromHit(wrapper, node, candidates))

    candidates.push(
      ...collectDragToolsContaining(wrapper, (el) => {
        if (el.getAttribute('field') === target.field) return true
        return Boolean(
          el.querySelector(`[field="${field}"]`)
          || el.querySelector(`[name="${field}"]`),
        )
      }),
    )
  }

  return pickInnermostDragTool(candidates)
}

/** Canvas root inside wrapper (fc-designer lives under zoom stage). */
export function getDesignerCanvasRoot(wrapper: HTMLElement): HTMLElement {
  return (
    wrapper.querySelector('.fc-designer-zoom-stage .fc-designer')
    || wrapper.querySelector('._fc-designer')
    || wrapper.querySelector('.fc-designer')
    || wrapper
  ) as HTMLElement
}

/**
 * form-create renders `._fd-drag-hidden` as a direct child overlay when rule._hidden / display is off.
 * Use this when getRule() lags behind the property panel (Basis "Hidden" uses activeRule._hidden).
 */
function hasDirectHiddenOverlay(dragTool: HTMLElement): boolean {
  return Array.from(dragTool.children).some(
    (child) => child instanceof HTMLElement && child.classList.contains('_fd-drag-hidden'),
  )
}

export function collectDragToolsWithHiddenOverlay(canvas: HTMLElement): HTMLElement[] {
  const tools: HTMLElement[] = []
  canvas.querySelectorAll('._fd-drag-tool').forEach((node) => {
    const el = node as HTMLElement
    if (hasDirectHiddenOverlay(el)) tools.push(el)
  })
  return tools
}

/**
 * Apply Power Apps–style hidden markers on the designer canvas without mutating saved rules.
 * - showHidden=false: concealed (not shown on canvas)
 * - showHidden=true: shown with dashed border + badge
 */
export function syncDesignerHiddenFieldMarkers(
  wrapper: HTMLElement | null | undefined,
  rules: unknown[],
  showHidden: boolean,
  hiddenBadgeLabel = 'Hidden',
): void {
  if (!wrapper) return

  const canvas = getDesignerCanvasRoot(wrapper)

  wrapper.classList.toggle('fc-designer-show-hidden', showHidden)
  wrapper.style.setProperty('--fc-designer-hidden-badge', `"${hiddenBadgeLabel.replace(/"/g, '\\"')}"`)

  canvas
    .querySelectorAll('[data-fc-designer-hidden-field]')
    .forEach((el) => {
      el.removeAttribute('data-fc-designer-hidden-field')
      el.classList.remove('fc-designer-hidden-field', 'fc-designer-hidden-field--concealed')
    })

  const marked = new Set<HTMLElement>()

  const markDragTool = (dragEl: HTMLElement) => {
    if (marked.has(dragEl)) return
    marked.add(dragEl)
    dragEl.setAttribute('data-fc-designer-hidden-field', 'true')
    dragEl.classList.add('fc-designer-hidden-field')
    if (!showHidden) {
      dragEl.classList.add('fc-designer-hidden-field--concealed')
    }
  }

  for (const target of collectHiddenDesignerTargets(rules)) {
    const dragEl = findDesignerDragElement(canvas, target)
    if (dragEl) markDragTool(dragEl)
  }

  for (const dragEl of collectDragToolsWithHiddenOverlay(canvas)) {
    markDragTool(dragEl)
  }
}
