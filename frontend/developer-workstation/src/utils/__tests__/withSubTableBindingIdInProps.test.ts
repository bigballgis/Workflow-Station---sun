import { describe, it, expect } from 'vitest'
import { withSubTableBindingIdInProps } from '../formDesigner'

/**
 * Persisted rules keep `_bindingId` only at top level (parseRule strips props._bindingId on
 * save), while SubTablePlaceholderWidget reads props._bindingId — preview surfaces must
 * restore the props copy or nested sub-table placeholders render "unconfigured".
 */
describe('withSubTableBindingIdInProps', () => {
  it('copies top-level _bindingId into props for subTable rules', () => {
    const rules = [
      { type: 'input', field: 'name' },
      { type: 'subTable', _bindingId: 50114, props: { portalViews: { assigneeTodo: 'tableOnly' } } },
    ]
    const out = withSubTableBindingIdInProps(rules)
    expect(out[1].props._bindingId).toBe(50114)
    expect(out[1].props.portalViews.assigneeTodo).toBe('tableOnly')
  })

  it('does not mutate the source rules (persisted configJson stays clean)', () => {
    const st = { type: 'subTable', _bindingId: 7, props: {} }
    const out = withSubTableBindingIdInProps([st])
    expect(st.props).toEqual({})
    expect(out[0]).not.toBe(st)
    expect(out[0].props._bindingId).toBe(7)
  })

  it('keeps an existing props._bindingId (live designer rules already carry it)', () => {
    const st = { type: 'subTable', _bindingId: 7, props: { _bindingId: 9 } }
    const out = withSubTableBindingIdInProps([st])
    expect(out[0]).toBe(st)
    expect(out[0].props._bindingId).toBe(9)
  })

  it('recurses into layout children without mutating them', () => {
    const card = {
      type: 'elCard',
      children: [{ type: 'subTable', _bindingId: 66, props: {} }],
    }
    const out = withSubTableBindingIdInProps([card])
    expect(out[0].children[0].props._bindingId).toBe(66)
    expect(card.children[0].props).toEqual({})
  })

  it('passes through primitives and rules without _bindingId', () => {
    const rules = [null, 'text', { type: 'subTable', props: {} }]
    const out = withSubTableBindingIdInProps(rules as any[])
    expect(out[0]).toBeNull()
    expect(out[1]).toBe('text')
    expect(out[2].props._bindingId).toBeUndefined()
  })
})
