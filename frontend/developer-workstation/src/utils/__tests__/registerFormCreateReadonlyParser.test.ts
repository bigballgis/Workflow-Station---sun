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
})
