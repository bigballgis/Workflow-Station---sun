import { describe, expect, it, vi } from 'vitest'
import {
  fitNodesToText,
  holdsEmbeddedText,
  requiredHeight,
  GROWN_NODE_TEXT_VERTICAL_PADDING,
  NODE_TEXT_HORIZONTAL_PADDING,
} from '../bpmnNodeTextFit'
import { layoutCallActivityLines } from '../callActivityShape'

const SVG_NS = 'http://www.w3.org/2000/svg'

function businessObject(type: string) {
  return { $type: type, $instanceOf: (t: string) => t === type || (t === 'bpmn:Task' && type.endsWith('Task')) }
}

/** A shape whose rendered text reports the given height (jsdom has no layout). */
function shape(id: string, type: string, textHeight: number, bounds = { x: 100, y: 100, width: 100, height: 80 }, textWidth = 80) {
  const gfx = document.createElementNS(SVG_NS, 'g')
  const visual = document.createElementNS(SVG_NS, 'g')
  visual.setAttribute('class', 'djs-visual')
  const text = document.createElementNS(SVG_NS, 'text') as any
  text.textContent = 'label'
  text.getBBox = () => ({ x: 0, y: 10, width: textWidth, height: textHeight })
  visual.appendChild(text)
  gfx.appendChild(visual)
  return { element: { id, ...bounds, businessObject: businessObject(type), incoming: [], outgoing: [] } as any, gfx }
}

function diagram(entries: Array<{ element: any; gfx: Element }>) {
  const fire = vi.fn()
  const services: Record<string, any> = {
    elementRegistry: {
      getAll: () => entries.map(e => e.element),
      getGraphics: (el: any) => entries.find(e => e.element === el)?.gfx,
    },
    eventBus: { fire },
  }
  return { get: (name: string) => services[name], fire }
}

describe('layoutCallActivityLines', () => {
  it('shows the kicker and the step name — no called unit, no version', () => {
    const lines = layoutCallActivityLines({ title: 'Approve', multiInstance: false })
    expect(lines.map(l => [l.text, l.offset])).toEqual([['Call Function Unit', -8], ['Approve', 8]])
  })

  it('keeps a long step name on one line (the node is widened instead)', () => {
    const lines = layoutCallActivityLines(
      { title: 'Check Primary Vendor Qualification', multiInstance: false })
    expect(lines.map(l => l.text)).toEqual(['Call Function Unit', 'Check Primary Vendor Qualification'])
  })
})

describe('fitNodesToText', () => {
  it('grows a shape whose text needs more room, evenly about its centre', () => {
    const task = shape('Task_1', 'bpmn:UserTask', 70)
    const d = diagram([task])

    const changed = fitNodesToText(d)

    const expected = 70 + 2 * GROWN_NODE_TEXT_VERTICAL_PADDING
    expect(changed).toEqual([task.element])
    expect(task.element.height).toBe(expected)
    expect(task.element.y + task.element.height / 2).toBe(140) // centre unchanged
    expect(d.fire).toHaveBeenCalledWith('elements.changed', expect.anything())
  })

  it('leaves shapes whose text already fits, so a repeat run is a no-op', () => {
    const task = shape('Task_1', 'bpmn:UserTask', 30)
    const d = diagram([task])

    expect(fitNodesToText(d)).toEqual([])
    expect(task.element.height).toBe(80)
    expect(d.fire).not.toHaveBeenCalled()
  })

  it('moves a flow end attached to the top edge onto the new top edge', () => {
    const task = shape('Task_1', 'bpmn:UserTask', 70)
    const flow = { waypoints: [{ x: 150, y: 20 }, { x: 150, y: 100 }], target: task.element }
    const side = { waypoints: [{ x: 0, y: 140 }, { x: 100, y: 140 }], target: task.element }
    task.element.incoming = [flow, side]

    fitNodesToText(diagram([task]))

    expect(flow.waypoints[1].y).toBe(task.element.y)
    expect(side.waypoints[1].y).toBe(140) // centre-line attachment still valid
  })

  /** A one-line name wider than the node widens it, evenly; arrow ends on its sides move with it. */
  it('widens a shape whose single line of text is wider than it', () => {
    const call = shape('Call_1', 'bpmn:CallActivity', 30, { x: 100, y: 100, width: 140, height: 80 }, 180)
    const incoming = { waypoints: [{ x: 0, y: 140 }, { x: 100, y: 140 }], target: call.element }
    const outgoing = { waypoints: [{ x: 240, y: 140 }, { x: 300, y: 140 }], source: call.element }
    call.element.incoming = [incoming]
    call.element.outgoing = [outgoing]

    fitNodesToText(diagram([call]))

    expect(call.element.width).toBe(180 + 2 * NODE_TEXT_HORIZONTAL_PADDING)
    expect(call.element.x + call.element.width / 2).toBe(170) // centre unchanged
    expect(call.element.height).toBe(80) // one line fits the height already
    expect(incoming.waypoints[1].x).toBe(call.element.x)
    expect(outgoing.waypoints[0].x).toBe(call.element.x + call.element.width)
  })

  it('skips events, gateways and expanded sub-processes', () => {
    expect(holdsEmbeddedText({ businessObject: businessObject('bpmn:StartEvent') })).toBe(false)
    expect(holdsEmbeddedText({ businessObject: businessObject('bpmn:ExclusiveGateway') })).toBe(false)
    expect(holdsEmbeddedText({ businessObject: businessObject('bpmn:SubProcess'), collapsed: false })).toBe(false)
    expect(holdsEmbeddedText({ businessObject: businessObject('bpmn:SubProcess'), collapsed: true })).toBe(true)
    expect(holdsEmbeddedText({ businessObject: businessObject('bpmn:CallActivity') })).toBe(true)
  })

  it('never shrinks a shape', () => {
    expect(requiredHeight(120, 10)).toBe(120)
  })

  it('keeps a shape whose text fits within the stock margins, e.g. a three-line name', () => {
    expect(requiredHeight(80, 44)).toBe(80)
  })
})
