/**
 * Grows BPMN activity shapes so their text is never clipped or spilled.
 *
 * A node's size comes from the diagram's DI and is fixed when it is drawn, but the text
 * inside it — a step name, or the Function Unit a call step targets — can be longer
 * than that box allows. Rather than truncate (the reader then can't tell two steps
 * apart) or overflow (the text runs over the arrows below), the shape is made taller.
 *
 * Shared by the Developer Workstation designer/version viewer and the User Portal
 * diagram, so a node is the same size everywhere it is shown.
 *
 * Display-only on purpose: bounds are adjusted on the rendered elements, not through
 * modeling commands, so opening a diagram never marks it changed or triggers an
 * auto-save (which, for a read-only Function Unit, would fail). The stored diagram
 * keeps its size until the node is next edited.
 *
 * Grown evenly about the centre. Height covers wrapped names (bpmn-js wraps task names);
 * width covers text drawn on one line (call steps), which no height can fit. Connection ends
 * on the edges that moved are moved with them, so flows stay attached.
 */

/**
 * Minimum space above and below the text before a shape counts as too small. Matches
 * what stock bpmn-js leaves for a three-line name, so shapes that look fine today keep
 * their size.
 */
export const NODE_TEXT_VERTICAL_PADDING = 16

/**
 * Space left above and below the text once a shape is grown: enough to clear the
 * task-type icon in the top-left corner and the multi-instance marker at the bottom.
 */
export const GROWN_NODE_TEXT_VERTICAL_PADDING = 26

/** Diagram service locator — a bpmn-js Viewer/Modeler instance. */
export interface DiagramLike {
  get(name: string): any
}

/**
 * Whether a shape carries its text inside its own box.
 *
 * Events and gateways put their name in an external label that already sizes itself;
 * an expanded sub-process puts its name at the top of an arbitrarily large box.
 */
export function holdsEmbeddedText(element: any): boolean {
  if (!element || element.labelTarget || element.waypoints) return false
  const bo = element.businessObject
  if (!bo || typeof bo.$instanceOf !== 'function') return false
  if (bo.$instanceOf('bpmn:Task') || bo.$instanceOf('bpmn:CallActivity')) return true
  if (bo.$instanceOf('bpmn:SubProcess')) {
    return element.collapsed === true || element.di?.isExpanded === false
  }
  return false
}

/** Room kept on each side of a line of text before a shape counts as too narrow. */
export const NODE_TEXT_HORIZONTAL_PADDING = 8

/** Width and height of the text drawn in a shape's visual, or null when it has none / cannot be measured. */
export function measureText(gfx: Element | null | undefined): { width: number; height: number } | null {
  const visual = gfx?.querySelector?.('.djs-visual')
  if (!visual) return null
  let top = Infinity
  let bottom = -Infinity
  let width = 0
  for (const text of Array.from(visual.querySelectorAll('text')) as SVGGraphicsElement[]) {
    if (!text.textContent?.trim() || typeof text.getBBox !== 'function') continue
    let box: DOMRect
    try {
      box = text.getBBox()
    } catch {
      continue
    }
    if (box.height <= 0) continue
    top = Math.min(top, box.y)
    bottom = Math.max(bottom, box.y + box.height)
    width = Math.max(width, box.width)
  }
  return Number.isFinite(top) ? { width, height: bottom - top } : null
}

/** The width a shape needs for its widest line of text; never smaller than it already is. */
export function requiredWidth(currentWidth: number, textWidth: number): number {
  return Math.max(currentWidth, Math.ceil(textWidth + 2 * NODE_TEXT_HORIZONTAL_PADDING))
}


/** The height a shape needs for text of the given height; never smaller than it already is. */
export function requiredHeight(currentHeight: number, textHeight: number): number {
  if (textHeight + 2 * NODE_TEXT_VERTICAL_PADDING <= currentHeight) return currentHeight
  return Math.max(currentHeight, Math.ceil(textHeight + 2 * GROWN_NODE_TEXT_VERTICAL_PADDING))
}

/**
 * Moves a connection end that sat on the shape's old top or bottom edge onto the new
 * one. Ends on the left/right sides stay valid as the shape only grows taller.
 */
function reattach(point: { x: number; y: number } | undefined, oldTop: number, oldBottom: number, grow: number) {
  if (!point) return
  if (Math.abs(point.y - oldTop) <= 1) point.y -= grow
  else if (Math.abs(point.y - oldBottom) <= 1) point.y += grow
}

/** As {@link reattach}, for the left and right edges when a shape grows wider. */
function reattachSide(point: { x: number; y: number } | undefined, oldLeft: number, oldRight: number, grow: number) {
  if (!point) return
  if (Math.abs(point.x - oldLeft) <= 1) point.x -= grow
  else if (Math.abs(point.x - oldRight) <= 1) point.x += grow
}

/**
 * Grows the given shapes (default: all) whose text needs more height than they have.
 *
 * Measures the text already rendered, so it agrees with whatever renderer drew the
 * shape — the stock one for tasks, the custom one for call steps. Returns the shapes
 * it changed. Repeated calls are no-ops once everything fits, so it is safe to run
 * from an `elements.changed` listener.
 */
export function fitNodesToText(diagram: DiagramLike, elements?: any[]): any[] {
  const elementRegistry = diagram.get('elementRegistry')
  const candidates = (elements ?? elementRegistry.getAll()).filter(holdsEmbeddedText)
  const changed: any[] = []

  for (const element of candidates) {
    const text = measureText(elementRegistry.getGraphics(element))
    if (text == null) continue
    const growY = (requiredHeight(element.height, text.height) - element.height) / 2
    const growX = (requiredWidth(element.width, text.width) - element.width) / 2
    if (growY < 0.5 && growX < 0.5) continue

    const oldTop = element.y
    const oldBottom = element.y + element.height
    const oldLeft = element.x
    const oldRight = element.x + element.width
    if (growY >= 0.5) {
      element.y = oldTop - growY
      element.height += 2 * growY
    }
    if (growX >= 0.5) {
      element.x = oldLeft - growX
      element.width += 2 * growX
    }

    for (const connection of [...(element.incoming || []), ...(element.outgoing || [])]) {
      const points = connection.waypoints
      if (!points?.length) continue
      const end = connection.target === element ? points[points.length - 1] : points[0]
      if (growY >= 0.5) reattach(end, oldTop, oldBottom, growY)
      if (growX >= 0.5) reattachSide(end, oldLeft, oldRight, growX)
      changed.push(connection)
    }
    changed.push(element)
  }

  if (changed.length) {
    diagram.get('eventBus').fire('elements.changed', { elements: changed })
  }
  return changed.filter(el => !el.waypoints)
}

/**
 * Keeps shapes fitted: after every diagram import, and whenever a shape changes
 * (renamed, retargeted, created). Register once, right after creating the diagram.
 *
 * The change listener runs after diagram-js has redrawn the changed elements (lower
 * priority than its own listener), so it measures the new text.
 */
export function keepNodesFittedToText(diagram: DiagramLike): void {
  const eventBus = diagram.get('eventBus')
  eventBus.on('import.done', () => fitNodesToText(diagram))
  eventBus.on('elements.changed', 500, (event: { elements?: any[] }) => {
    const shapes = (event.elements || []).filter(holdsEmbeddedText)
    if (shapes.length) fitNodesToText(diagram, shapes)
  })
}

