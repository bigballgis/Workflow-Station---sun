import { describe, expect, it } from 'vitest'
import {
  blockReadOnlyDesignerInteraction,
  isReadOnlyAllowedTarget,
} from '../readOnlyDesignerInteraction'

describe('readOnlyDesignerInteraction', () => {
  it('allows targets marked data-read-only-allowed', () => {
    const allowed = document.createElement('button')
    allowed.setAttribute('data-read-only-allowed', '')
    const child = document.createElement('span')
    allowed.appendChild(child)
    expect(isReadOnlyAllowedTarget(child)).toBe(true)
  })

  it('allows tab header clicks', () => {
    const header = document.createElement('div')
    header.className = 'el-tabs__header'
    const tab = document.createElement('div')
    header.appendChild(tab)
    expect(isReadOnlyAllowedTarget(tab)).toBe(true)
  })

  it('blocks unmarked mutation targets', () => {
    const save = document.createElement('button')
    save.textContent = 'Save'
    const event = new MouseEvent('click', { bubbles: true, cancelable: true })
    Object.defineProperty(event, 'target', { value: save })
    blockReadOnlyDesignerInteraction(event)
    expect(event.defaultPrevented).toBe(true)
  })

  it('does not block allowed inspection controls', () => {
    const zoom = document.createElement('button')
    zoom.setAttribute('data-read-only-allowed', '')
    const event = new MouseEvent('click', { bubbles: true, cancelable: true })
    Object.defineProperty(event, 'target', { value: zoom })
    blockReadOnlyDesignerInteraction(event)
    expect(event.defaultPrevented).toBe(false)
  })
})
