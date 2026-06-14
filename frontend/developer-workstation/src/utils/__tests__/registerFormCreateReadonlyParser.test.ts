import { describe, expect, it, vi } from 'vitest'
import { registerFormCreateReadonlyParser } from '../registerFormCreateReadonlyParser'

describe('registerFormCreateReadonlyParser', () => {
  it('registers readonly parser for built-in and custom form-create types', () => {
    const parser = vi.fn()
    registerFormCreateReadonlyParser({ parser })

    expect(parser).toHaveBeenCalled()
    expect(parser.mock.calls.some(([type]) => type === 'input')).toBe(true)
    expect(parser.mock.calls.some(([type]) => type === 'lookup')).toBe(true)

    const config = parser.mock.calls.find(([type]) => type === 'select')?.[1]
    expect(config?.merge).toBe(true)

    const ctx = {
      rule: { field: 'x', props: { readonly: true } },
      prop: { props: { readonly: true } },
    }
    config?.mergeProp(ctx)
    expect(ctx.rule.disabled).toBe(true)
    expect(ctx.prop.props?.disabled).toBe(true)
    expect(ctx.prop.props?.readonly).toBeUndefined()
  })

  it('clears stale disabled when readonly is explicitly off (re-select field)', () => {
    const parser = vi.fn()
    registerFormCreateReadonlyParser({ parser })
    const config = parser.mock.calls.find(([type]) => type === 'input')?.[1]

    const ctx = {
      rule: {
        field: 'case_number',
        readonly: false,
        disabled: true,
        props: { disabled: true, readonly: false },
      },
      prop: { props: { disabled: true, readonly: false } },
    }
    config?.mergeProp(ctx)
    expect(ctx.rule.disabled).toBeUndefined()
    expect(ctx.rule.readonly).toBe(false)
    expect(ctx.prop.props?.disabled).toBeUndefined()
    expect(ctx.prop.props?.readonly).toBe(false)
  })

  it('clears stale disabled when props panel readonly is off but rule.readonly is still true', () => {
    const parser = vi.fn()
    registerFormCreateReadonlyParser({ parser })
    const config = parser.mock.calls.find(([type]) => type === 'input')?.[1]

    const ctx = {
      rule: {
        field: 'case_number',
        type: 'input',
        readonly: true,
        disabled: true,
        props: { disabled: true, readonly: true },
      },
      prop: { props: { readonly: false } },
    }
    config?.mergeProp(ctx)
    expect(ctx.rule.readonly).toBe(false)
    expect(ctx.rule.disabled).toBeUndefined()
    expect(ctx.prop.props?.readonly).toBe(false)
    expect(ctx.prop.props?.disabled).toBeUndefined()
  })
})
