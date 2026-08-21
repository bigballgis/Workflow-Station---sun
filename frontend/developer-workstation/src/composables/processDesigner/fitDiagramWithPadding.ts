/** Palette is absolutely positioned over the canvas at ~10px left, ~48px wide. */
const PALETTE_CLEARANCE = 68

/**
 * Fits the BPMN diagram to the canvas with breathing room around it, instead
 * of bpmn-js's built-in `zoom('fit-viewport')` which scales the diagram
 * flush against all four container edges. That flush fit also left the
 * start event sitting directly under the palette (absolutely positioned
 * over the canvas, not part of layout), since nothing kept content clear of it.
 *
 * The canvas is tall relative to most diagrams (`calc(100vh - 280px)`), so
 * centering the fitted diagram vertically in that leftover space would strand
 * it far below the toolbar with a huge empty gap above. Instead this anchors
 * the diagram near the top with a fixed margin and only centers horizontally
 * (biased right to clear the palette), which reads as "diagram with margin,"
 * not "diagram lost in a big empty box."
 */
export function fitDiagramWithPadding(bpmnModeler: any, padding = 40): void {
  const canvas = bpmnModeler.get('canvas')
  const { inner, outer } = canvas.viewbox()

  if (inner.width <= 0 || inner.height <= 0) {
    canvas.zoom('fit-viewport')
    return
  }

  const availableWidth = Math.max(outer.width - padding * 2 - PALETTE_CLEARANCE, 1)
  const scale = Math.min(1, availableWidth / inner.width)

  const viewboxWidth = outer.width / scale
  const viewboxHeight = outer.height / scale
  const extraWidth = viewboxWidth - inner.width

  canvas.viewbox({
    x: inner.x - extraWidth / 2 - PALETTE_CLEARANCE / scale / 2,
    y: inner.y - padding / scale,
    width: viewboxWidth,
    height: viewboxHeight,
  })
}
