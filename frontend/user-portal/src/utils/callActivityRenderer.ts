/**
 * bpmn-js renderer for Function Unit call activities, matching the Developer Workstation.
 *
 * Stock bpmn-js draws a `callActivity` as a thick black box, which looks nothing like
 * the node the designer drew. The drawing is shared with the DW renderer
 * (`@platform-shared/callActivityShape`) — accent frame, call glyph, step name — so a
 * call step looks the same in both.
 */
// @ts-ignore — diagram-js ships without type declarations
import BaseRenderer from 'diagram-js/lib/draw/BaseRenderer'
import { drawCallActivityShape } from '@platform-shared/callActivityShape'

export {
  CALL_ACTIVITY_ACCENT,
  CALL_ACTIVITY_DECOR_CLASS,
} from '@platform-shared/callActivityShape'

/** Above the default BpmnRenderer (1000) so these elements take precedence. */
const CALL_ACTIVITY_RENDER_PRIORITY = 1500

export function isCallActivity(element: any): boolean {
  return element?.businessObject?.$type === 'bpmn:CallActivity'
}

/** The process key a `calledElement` refers to, whether it holds a key or a pinned definition id. */
export function calledKeyOf(calledElement: string | undefined): string {
  if (!calledElement) return ''
  return calledElement.split(':')[0] || ''
}

export default class CallActivityRenderer extends BaseRenderer {
  static $inject = ['eventBus']

  constructor(eventBus: any) {
    super(eventBus, CALL_ACTIVITY_RENDER_PRIORITY)
  }

  canRender(element: any): boolean {
    return isCallActivity(element) && !element.labelTarget
  }

  drawShape(parentNode: SVGElement, element: any): SVGElement {
    const bo = element.businessObject || {}
    return drawCallActivityShape(
      parentNode,
      { width: element.width || 140, height: element.height || 80 },
      {
        // A step not yet named falls back to the called process key (never the raw
        // pinned definition id, which is unreadable).
        title: bo.name || calledKeyOf(bo.calledElement),
        multiInstance: Boolean(bo.loopCharacteristics),
      },
    )
  }

  getShapePath(shape: any): string {
    const { x, y, width, height } = shape
    return `M${x},${y}h${width}v${height}h-${width}z`
  }
}

/** diagram-js module registering the renderer above the default one. */
export const callActivityRendererModule = {
  __init__: ['callActivityRenderer'],
  callActivityRenderer: ['type', CallActivityRenderer],
}
