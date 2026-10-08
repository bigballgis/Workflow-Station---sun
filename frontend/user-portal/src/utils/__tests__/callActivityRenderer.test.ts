import { describe, expect, it } from 'vitest'
import CallActivityRenderer, {
  calledKeyOf,
  CALL_ACTIVITY_ACCENT,
  CALL_ACTIVITY_DECOR_CLASS,
} from '../callActivityRenderer'

/** Business objects as the portal's Viewer produces them; Call_1 is pinned to 1.0.1. */
function callActivities(): Record<string, any> {
  return {
    Call_1: {
      $type: 'bpmn:CallActivity',
      id: 'Call_1',
      name: 'Check Primary Vendor',
      calledElement: 'fu-callee:2:b8ea',
      loopCharacteristics: { $type: 'bpmn:MultiInstanceLoopCharacteristics' },
      extensionElements: {
        $type: 'bpmn:ExtensionElements',
        values: [{
          $type: 'custom:properties',
          $children: [
            { $type: 'custom:property', name: 'formId', value: '12' },
            { $type: 'custom:property', name: 'calledVersion', value: '1.0.1' },
          ],
        }],
      },
    },
    Call_2: { $type: 'bpmn:CallActivity', id: 'Call_2', calledElement: 'fu-callee' },
  }
}

function render(businessObject: any) {
  const renderer = new CallActivityRenderer({ on: () => {} })
  const parent = document.createElementNS('http://www.w3.org/2000/svg', 'g')
  const frame = renderer.drawShape(parent, { width: 140, height: 80, businessObject })
  const texts = Array.from(parent.querySelectorAll('text')).map(t => t.textContent)
  return { parent, frame, texts }
}

describe('callActivityRenderer', () => {
  it('takes the process key from both a key and a pinned definition id', () => {
    expect(calledKeyOf('fu-callee:2:b8ea')).toBe('fu-callee')
    expect(calledKeyOf('fu-callee')).toBe('fu-callee')
    expect(calledKeyOf(undefined)).toBe('')
  })

  it('draws a task frame with the accent call glyph, and the whole step name — not the called unit or its version', () => {
    const { parent, frame, texts } = render(callActivities().Call_1)

    // Ordinary task frame; only the call glyph carries the accent.
    expect(frame.getAttribute('stroke')).toBe('#000000')
    expect(parent.querySelector(`.${CALL_ACTIVITY_DECOR_CLASS} rect`)?.getAttribute('stroke')).toBe(CALL_ACTIVITY_ACCENT)
    expect(texts[0]).toBe('Call Function Unit')
    expect(texts).not.toContain('1.0.1')
    const title = texts.slice(1)
    expect(title.join(' ')).toBe('Check Primary Vendor')
    expect(title.join('')).not.toContain('…')
    // Glyph + multi-instance bars are exempt from status repainting.
    expect(parent.querySelectorAll(`.${CALL_ACTIVITY_DECOR_CLASS}`)).toHaveLength(2)
  })

  it('falls back to the called process key when the step has no name', () => {
    expect(render(callActivities().Call_2).texts).toEqual(['Call Function Unit', 'fu-callee'])
  })
})
