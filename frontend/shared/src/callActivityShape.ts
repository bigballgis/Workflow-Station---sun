/**
 * How a Function Unit call step is drawn — one definition for every diagram.
 *
 * Stock bpmn-js draws a `callActivity` as a thick black box with a ⊞ marker, nearly
 * indistinguishable from a collapsed sub-process although the two do very different
 * things: a sub-process runs inside this instance, a call step hands control to a
 * *different* Function Unit. This draws it as an ordinary task box (same frame and
 * fill as any other step) marked with an accent-coloured arrow-out-of-box glyph, a
 * small line saying what kind of step it is, and the step
 * name on one line, never wrapped or truncated (the node is widened to fit it instead). Which Function Unit it calls, and which version,
 * are in the properties panel and the Call Relations dialog, not on the node.
 *
 * The Developer Workstation and the User Portal each wrap this in a bpmn-js renderer;
 * keeping the drawing here is what makes the node look the same in both.
 *
 * Built with the DOM API so it needs no SVG helper library from either app.
 */

/** Only the call glyph uses the accent; the frame and per-row bars match other tasks. */
export const CALL_ACTIVITY_ACCENT = '#7C3AED'
/** bpmn-js' own frame for a task: black 2px stroke on white. */
const FRAME_STROKE = '#000000'
const FRAME_FILL = '#FFFFFF'
const TEXT_MUTED = '#6B7280'
/** Step name: same colour as the name on any other node, so call steps read as ordinary steps. */
const TEXT_TITLE = '#000000'

export const CALL_ACTIVITY_KICKER = 'Call Function Unit'

/**
 * Marks the decorative parts (glyph, multi-instance bars) so status colouring in the
 * portal repaints only the frame and leaves these in the accent colour.
 */
export const CALL_ACTIVITY_DECOR_CLASS = 'fu-call-decor'

/** Vertical distance between line centres. */
const LINE_STEP = 16
/** bpmn-js' own size for names inside a task. */
const TITLE_FONT_PX = 12

export interface CallActivityContent {
  /** The emphasised line: the step name. */
  title: string
  /** One child process per collection row. */
  multiInstance: boolean
}

export interface CallActivityLine {
  text: string
  /** Offset of the line's centre from the shape's vertical centre. */
  offset: number
  fill: string
  fontSize: string
  fontWeight: string
}

/**
 * The text lines of a call step — kicker, then the wrapped title — centred as a block.
 *
 * The title stays on one line; the shape is then widened to fit by `fitNodesToText`,
 * which measures exactly these lines.
 */
export function layoutCallActivityLines(content: CallActivityContent): CallActivityLine[] {
  const lines: Omit<CallActivityLine, 'offset'>[] = [
    { text: CALL_ACTIVITY_KICKER, fill: TEXT_MUTED, fontSize: '10px', fontWeight: '400' },
  ]
  if (content.title) {
    // One line: a call step is read by its name, and a wrapped name is harder to scan along a
    // flow. A name wider than the node makes fitNodesToText widen the node instead.
    lines.push({ text: content.title, fill: TEXT_TITLE, fontSize: `${TITLE_FONT_PX}px`, fontWeight: '400' })
  }
  const firstOffset = -((lines.length - 1) * LINE_STEP) / 2
  return lines.map((line, index) => ({ ...line, offset: firstOffset + index * LINE_STEP }))
}

const SVG_NS = 'http://www.w3.org/2000/svg'

function svg(tag: string, attrs: Record<string, string | number>): SVGElement {
  const el = document.createElementNS(SVG_NS, tag)
  for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, String(value))
  return el
}

/** Draws the call step into `parentNode`; returns the frame, which bpmn-js treats as the shape. */
export function drawCallActivityShape(
  parentNode: Element,
  size: { width: number; height: number },
  content: CallActivityContent,
): SVGElement {
  const { width, height } = size

  const frame = svg('rect', {
    x: 0, y: 0, width, height, rx: 10, ry: 10,
    stroke: FRAME_STROKE, 'stroke-width': 2, fill: FRAME_FILL,
  })
  parentNode.appendChild(frame)

  // An arrow leaving a box: "control goes out to another Function Unit".
  const glyph = svg('g', { transform: 'translate(8, 8)', class: CALL_ACTIVITY_DECOR_CLASS })
  glyph.appendChild(svg('rect', {
    x: 0, y: 3, width: 11, height: 11, rx: 2, ry: 2,
    stroke: CALL_ACTIVITY_ACCENT, 'stroke-width': 1.6, fill: 'none',
  }))
  glyph.appendChild(svg('path', {
    d: 'M7,8 L16,8 M12.5,4.5 L16,8 L12.5,11.5',
    stroke: CALL_ACTIVITY_ACCENT, 'stroke-width': 1.8, fill: 'none',
    'stroke-linecap': 'round', 'stroke-linejoin': 'round',
  }))
  parentNode.appendChild(glyph)

  for (const line of layoutCallActivityLines(content)) {
    const text = svg('text', {
      x: width / 2,
      y: height / 2 + line.offset + 4,
      'text-anchor': 'middle',
      'font-size': line.fontSize,
      'font-weight': line.fontWeight,
      'font-family': 'Arial, sans-serif',
      fill: line.fill,
    })
    text.textContent = line.text
    parentNode.appendChild(text)
  }

  // Multi-instance call: the standard three bars, inside the lower edge, black like the
  // multi-instance marker on any other step.
  if (content.multiInstance) {
    const bars = svg('g', {
      transform: `translate(${width / 2 - 7}, ${height - 14})`,
      class: CALL_ACTIVITY_DECOR_CLASS,
    })
    for (let i = 0; i < 3; i++) {
      bars.appendChild(svg('rect', { x: i * 5, y: 0, width: 2, height: 10, fill: FRAME_STROKE }))
    }
    parentNode.appendChild(bars)
  }

  return frame
}
