import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parse } from '@vue/compiler-sfc'
import {
  blockReadOnlyDesignerInteraction,
  isReadOnlyAllowedTarget,
} from '../readOnlyDesignerInteraction'

describe('readOnlyDesignerInteraction', () => {
  function designerTemplate(name: string): HTMLElement {
    const source = readFileSync(resolve('src/components/designer', `${name}.vue`), 'utf8')
    const { descriptor } = parse(source)
    const root = document.createElement('div')
    root.innerHTML = descriptor.template!.content
    while (root.querySelector('template')) {
      const template = root.querySelector('template')!
      template.replaceWith(template.content)
    }
    root.querySelectorAll('el-button').forEach(button => button.classList.add('el-button'))
    return root
  }

  function expectInspectionClickAllowed(target: Element): void {
    const event = new MouseEvent('click', { bubbles: true, cancelable: true })
    Object.defineProperty(event, 'target', { value: target })
    blockReadOnlyDesignerInteraction(event)
    expect(event.defaultPrevented).toBe(false)
  }

  it.each([
    ['FormDesigner', 'requestBackToList'],
    ['ActionDesigner', 'handleBackToList'],
    ['DecisionList', 'closeDesigner'],
  ])('allows the actual %s return-to-list control', (name, handler) => {
    const root = designerTemplate(name)
    const back = Array.from(root.querySelectorAll('el-button'))
      .find(button => button.getAttribute('@click') === handler)
    expect(back).toBeDefined()
    expectInspectionClickAllowed(back!)
  })

  it('allows all form table-navigation and sub-table inspection tabs', () => {
    const root = designerTemplate('FormDesigner')
    const tabs = root.querySelectorAll('.designer-nav-btn, .sub-inner-tab-btn')
    expect(tabs.length).toBe(6)
    tabs.forEach(expectInspectionClickAllowed)
    const save = Array.from(root.querySelectorAll('el-button'))
      .find(button => button.getAttribute('@click') === 'saveChanges')!
    expect(isReadOnlyAllowedTarget(save)).toBe(false)
  })

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

  it('lets a row or canvas click through so details stay visible', () => {
    const row = document.createElement('div')
    row.className = 'views-form-card'
    const event = new MouseEvent('click', { bubbles: true, cancelable: true })
    Object.defineProperty(event, 'target', { value: row })
    blockReadOnlyDesignerInteraction(event)
    expect(event.defaultPrevented).toBe(false)
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
