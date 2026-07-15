import { describe, expect, it } from 'vitest'
import {
  walkFormCreateRules,
  withSubTableBindingIdInProps,
  snapshotRulesForPreview,
} from '../formDesigner'
import { mapFormCreateRulesReadonlyDeep } from '../formCreateRuleUtils'
import { ensureFormCreateRulesValidationDeep } from '../formCreateValidateRules'

/** Build a cyclic rule graph like fc-designer layout nodes can produce. */
function cyclicLayoutRule(): Record<string, unknown> {
  const row: Record<string, unknown> = {
    type: 'el-row',
    props: { children: [] as unknown[] },
  }
  const col: Record<string, unknown> = {
    type: 'el-col',
    props: { children: [row] },
  }
  ;(row.props as Record<string, unknown>).children = [col]
  return row
}

describe('form-create rule tree walk (cycle-safe)', () => {
  it('walkFormCreateRules terminates on cyclic layout graph', () => {
    let visits = 0
    walkFormCreateRules([cyclicLayoutRule()], () => {
      visits += 1
    })
    expect(visits).toBe(2)
  })

  it('withSubTableBindingIdInProps terminates on cyclic graph', () => {
    const out = withSubTableBindingIdInProps([cyclicLayoutRule()])
    expect(out).toHaveLength(1)
  })

  it('mapFormCreateRulesReadonlyDeep terminates on cyclic graph', () => {
    const out = mapFormCreateRulesReadonlyDeep([cyclicLayoutRule()])
    expect(out).toHaveLength(1)
  })

  it('ensureFormCreateRulesValidationDeep terminates on cyclic graph', () => {
    expect(() => ensureFormCreateRulesValidationDeep([cyclicLayoutRule()])).not.toThrow()
  })

  it('snapshotRulesForPreview deep-clones without sharing references', () => {
    const src = [{ type: 'input', field: 'a', value: '1' }]
    const snap = snapshotRulesForPreview(src)
    expect(snap).not.toBe(src)
    expect(snap[0]).not.toBe(src[0])
    snap[0].value = '2'
    expect(src[0].value).toBe('1')
  })

  it('maps readonly through props.children nesting', () => {
    const rules = mapFormCreateRulesReadonlyDeep([
      {
        type: 'el-row',
        props: {
          children: [{ type: 'input', field: 'a', props: { readonly: true } }],
        },
      },
    ]) as Array<Record<string, unknown>>
    const child = ((rules[0].props as Record<string, unknown>).children as Array<Record<string, unknown>>)[0]
    expect(child.disabled).toBe(true)
  })
})
