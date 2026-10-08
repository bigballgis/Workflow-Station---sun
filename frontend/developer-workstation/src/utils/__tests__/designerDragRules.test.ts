import { describe, expect, it } from 'vitest'
// @ts-ignore — the designer ships its source without type declarations
import designerDragRules from '@form-create/designer/src/config'
import { fieldSwitchTypes, overrideDragRule } from '../designerDragRules'

const names = () => (designerDragRules as Array<{ name: string }>).map((rule) => rule.name)

describe('designerDragRules', () => {
  it('replaces a built-in component in place instead of adding a second entry', () => {
    const before = names()
    expect(before.filter((n) => n === 'slider')).toHaveLength(1)

    overrideDragRule({ name: 'slider', menu: 'main', input: true, marker: 'platform' })

    expect(names()).toEqual(before)
    const slider = (designerDragRules as Array<{ name: string; marker?: string }>).find((r) => r.name === 'slider')
    expect(slider?.marker).toBe('platform')
  })

  it('offers Extend field components in the type switcher, not containers', () => {
    overrideDragRule({ name: 'lookup', menu: 'extend', input: true })
    overrideDragRule({ name: 'owner', menu: 'extend', input: true })
    overrideDragRule({ name: 'subTable', menu: 'extend', input: false })

    const switchable = fieldSwitchTypes()

    expect(switchable).toEqual(expect.arrayContaining(['input', 'select', 'lookup', 'owner']))
    expect(switchable).not.toContain('subTable')
    expect(new Set(switchable).size).toBe(switchable.length)
  })
})
