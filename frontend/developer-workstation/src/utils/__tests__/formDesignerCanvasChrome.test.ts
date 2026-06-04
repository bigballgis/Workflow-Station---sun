import { describe, expect, it } from 'vitest'
import {
  collectDragToolsWithHiddenOverlay,
  collectHiddenDesignerTargets,
  findDesignerDragElement,
  pickInnermostDragTool,
  syncDesignerHiddenFieldMarkers,
} from '../formDesignerCanvasChrome'

describe('formDesignerCanvasChrome', () => {
  it('collects hidden field keys from nested rules', () => {
    const targets = collectHiddenDesignerTargets([
      { type: 'input', field: 'visible', hidden: false },
      {
        type: 'elCard',
        children: [
          { type: 'lookup', field: 'secret', hidden: true, _fc_id: 'id_abc' },
        ],
      },
    ])
    expect(targets).toEqual([{ kind: 'field', field: 'secret', fcId: 'id_abc' }])
  })

  it('collects hidden subTable by bindingId', () => {
    const targets = collectHiddenDesignerTargets([
      { type: 'subTable', hidden: true, _bindingId: 42, _fc_id: 'id_st' },
    ])
    expect(targets).toEqual([{ kind: 'subTable', bindingId: 42, fcId: 'id_st' }])
  })

  it('collects live designer _hidden flag before getRule parse', () => {
    const targets = collectHiddenDesignerTargets([
      { type: 'input', field: 'Card_Number', _hidden: true, _fc_id: 'id_card' },
    ])
    expect(targets).toEqual([{ kind: 'field', field: 'Card_Number', fcId: 'id_card' }])
  })

  it('pickInnermostDragTool prefers nested field over card container', () => {
    const card = document.createElement('div')
    card.className = '_fd-drag-tool'
    Object.defineProperty(card, 'offsetWidth', { value: 400 })
    Object.defineProperty(card, 'offsetHeight', { value: 200 })

    const field = document.createElement('div')
    field.className = '_fd-drag-tool'
    Object.defineProperty(field, 'offsetWidth', { value: 120 })
    Object.defineProperty(field, 'offsetHeight', { value: 40 })
    card.appendChild(field)

    expect(pickInnermostDragTool([card, field])).toBe(field)
  })

  it('findDesignerDragElement resolves hidden field inside card, not the card', () => {
    const wrapper = document.createElement('div')
    const card = document.createElement('div')
    card.className = '_fd-drag-tool _fd-drag-box'
    Object.defineProperty(card, 'offsetWidth', { value: 400 })
    Object.defineProperty(card, 'offsetHeight', { value: 200 })

    const fieldWrap = document.createElement('div')
    fieldWrap.className = '_fd-drag-tool'
    Object.defineProperty(fieldWrap, 'offsetWidth', { value: 120 })
    Object.defineProperty(fieldWrap, 'offsetHeight', { value: 40 })

    const input = document.createElement('input')
    input.setAttribute('name', 'legal_hold')
    fieldWrap.appendChild(input)
    card.appendChild(fieldWrap)
    wrapper.appendChild(card)

    const drag = findDesignerDragElement(wrapper, { kind: 'field', field: 'legal_hold' })
    expect(drag).toBe(fieldWrap)
    expect(drag).not.toBe(card)
  })

  it('collectDragToolsWithHiddenOverlay finds only direct hidden overlay, not card parent', () => {
    const canvas = document.createElement('div')
    const card = document.createElement('div')
    card.className = '_fd-drag-tool'
    const field = document.createElement('div')
    field.className = '_fd-drag-tool'
    const overlay = document.createElement('div')
    overlay.className = '_fd-drag-hidden'
    field.appendChild(overlay)
    card.appendChild(field)
    canvas.appendChild(card)

    expect(collectDragToolsWithHiddenOverlay(canvas)).toEqual([field])
  })

  it('syncDesignerHiddenFieldMarkers toggles wrapper class and concealed state', () => {
    const wrapper = document.createElement('div')
    wrapper.className = 'fc-designer-wrapper'
    const stage = document.createElement('div')
    stage.className = 'fc-designer-zoom-stage'
    const canvas = document.createElement('div')
    canvas.className = '_fc-designer'
    const drag = document.createElement('div')
    drag.className = '_fd-drag-tool'
    Object.defineProperty(drag, 'offsetWidth', { value: 120 })
    Object.defineProperty(drag, 'offsetHeight', { value: 40 })
    const overlay = document.createElement('div')
    overlay.className = '_fd-drag-hidden'
    const input = document.createElement('input')
    input.setAttribute('name', 'fld')
    drag.appendChild(overlay)
    drag.appendChild(input)
    canvas.appendChild(drag)
    stage.appendChild(canvas)
    wrapper.appendChild(stage)

    syncDesignerHiddenFieldMarkers(
      wrapper,
      [{ type: 'input', field: 'fld', hidden: true }],
      false,
    )
    expect(wrapper.classList.contains('fc-designer-show-hidden')).toBe(false)
    expect(drag.classList.contains('fc-designer-hidden-field--concealed')).toBe(true)

    syncDesignerHiddenFieldMarkers(
      wrapper,
      [{ type: 'input', field: 'fld', hidden: true }],
      true,
    )
    expect(wrapper.classList.contains('fc-designer-show-hidden')).toBe(true)
    expect(drag.classList.contains('fc-designer-hidden-field--concealed')).toBe(false)
  })
})
