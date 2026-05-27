/**
 * @vitest-environment jsdom
 */
import { describe, expect, it } from 'vitest'
import formCreateModule from '@form-create/element-ui'
import { registerFormCreateReadonlyParser } from '../registerFormCreateReadonlyParser'

describe('registerFormCreateReadonlyParser integration', () => {
  it('registers without throwing on form-create factory', () => {
    const formCreate = (formCreateModule as { default?: typeof formCreateModule }).default ?? formCreateModule
    expect(typeof formCreate.parser).toBe('function')
    expect(() =>
      registerFormCreateReadonlyParser(formCreate as { parser: (name: string, config: unknown) => void }),
    ).not.toThrow()
  })
})
