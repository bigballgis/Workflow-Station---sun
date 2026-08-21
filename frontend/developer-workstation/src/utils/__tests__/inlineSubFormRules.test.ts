import { describe, it, expect } from 'vitest'
import { collectSubTableRules, withSubTableBindingIdInProps } from '../formDesigner'

/**
 * The `inlineSubForm` rule binds a SUB table via `_bindingId`, exactly like `subTable`, so it
 * must clear the same two save-time guards in useFormSave.ts (both of which call
 * collectSubTableRules): "every placeholder has a binding" and "that binding is of type SUB".
 *
 * withSubTableBindingIdInProps matters because the drag rule's parseRule DELETES
 * props._bindingId on save — persisted rules carry it only at top level, so preview surfaces
 * that feed saved rules into form-create must copy it back or the widget renders "unconfigured".
 */

describe('collectSubTableRules — inlineSubForm', () => {
  it('collects an inlineSubForm rule so save-time binding validation applies', () => {
    const rules = [
      { type: 'input', field: 'a' },
      { type: 'inlineSubForm', _bindingId: 66 },
    ]
    const out = collectSubTableRules(rules)
    expect(out).toHaveLength(1)
    expect(out[0].type).toBe('inlineSubForm')
  })

  it('collects an UNBOUND inlineSubForm so the "binding required" guard can reject it', () => {
    const out = collectSubTableRules([{ type: 'inlineSubForm' }])
    expect(out).toHaveLength(1)
    // useFormSave filters on !r._bindingId — the rule must be visible for that to fire.
    expect(out[0]._bindingId).toBeUndefined()
  })

  it('collects both rule types together', () => {
    const out = collectSubTableRules([
      { type: 'subTable', _bindingId: 77 },
      { type: 'inlineSubForm', _bindingId: 66 },
    ])
    expect(out.map(r => r.type).sort()).toEqual(['inlineSubForm', 'subTable'])
  })

  it('finds an inlineSubForm nested inside a layout container', () => {
    const out = collectSubTableRules([
      { type: 'elCard', children: [{ type: 'inlineSubForm', _bindingId: 66 }] },
    ])
    expect(out).toHaveLength(1)
    expect(out[0]._bindingId).toBe(66)
  })
})

describe('withSubTableBindingIdInProps — inlineSubForm', () => {
  it('copies a top-level _bindingId into props for the canvas widget to read', () => {
    const out = withSubTableBindingIdInProps([{ type: 'inlineSubForm', _bindingId: 66 }])
    expect(out[0].props._bindingId).toBe(66)
  })

  it('does not clobber an existing props._bindingId', () => {
    const out = withSubTableBindingIdInProps([
      { type: 'inlineSubForm', _bindingId: 66, props: { _bindingId: 99 } },
    ])
    expect(out[0].props._bindingId).toBe(99)
  })

  it('leaves the input rule untouched (non-mutating)', () => {
    const input: any = { type: 'inlineSubForm', _bindingId: 66 }
    withSubTableBindingIdInProps([input])
    expect(input.props).toBeUndefined()
  })

  it('handles an inlineSubForm nested in a container', () => {
    const out = withSubTableBindingIdInProps([
      { type: 'elCard', children: [{ type: 'inlineSubForm', _bindingId: 66 }] },
    ])
    expect(out[0].children[0].props._bindingId).toBe(66)
  })
})
