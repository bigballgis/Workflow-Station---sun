/**
 * bpmn-js renderer for Function Unit call activities.
 *
 * The drawing itself lives in `@platform-shared/callActivityShape`, shared with the
 * User Portal so a call step looks the same in the designer and at runtime: accent
 * frame, call glyph, and the step name. The called Function Unit and pinned version
 * are shown in the properties panel and the Call Relations dialog, not on the node.
 */
import BaseRenderer from 'diagram-js/lib/draw/BaseRenderer'
import { drawCallActivityShape } from '@platform-shared/callActivityShape'

/** Above the default BpmnRenderer (1000) so these elements take precedence. */
const CALL_ACTIVITY_RENDER_PRIORITY = 1500

function isCallActivity(element: any): boolean {
  return element?.businessObject?.$type === 'bpmn:CallActivity'
}

export default class CallActivityRenderer extends BaseRenderer {
  static $inject = ['eventBus']

  constructor(eventBus: any) {
    super(eventBus, CALL_ACTIVITY_RENDER_PRIORITY)
  }

  canRender(element: any): boolean {
    return isCallActivity(element) && !element.labelTarget
  }

  drawShape(parentNode: any, element: any): SVGElement {
    const bo = element.businessObject || {}
    // The step name wraps rather than truncates; the node is then grown to fit by
    // fitNodesToText. A step not yet named falls back to the called code.
    return drawCallActivityShape(
      parentNode,
      { width: element.width || 140, height: element.height || 80 },
      {
        title: bo.name || bo.calledElement || '',
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
