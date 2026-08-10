import { describe, expect, it } from 'vitest'
import { pickSubFormOptionsFromDesign } from '../pickSubFormOptionsFromDesign'

describe('pickSubFormOptionsFromDesign', () => {
  it('returns designer Form event options for New Request subform bindings', () => {
    const onChange = '$FNX:\napi.hidden(true, "x")'
    expect(
      pickSubFormOptionsFromDesign({
        rule: [{ field: 'a', type: 'select' }],
        options: { onChange, onCreated: '$FNX:\nreturn' },
      }),
    ).toEqual({ onChange, onCreated: '$FNX:\nreturn' })
  })

  it('returns undefined when options missing or non-object (no silent {})', () => {
    expect(pickSubFormOptionsFromDesign(undefined)).toBeUndefined()
    expect(pickSubFormOptionsFromDesign({})).toBeUndefined()
    expect(pickSubFormOptionsFromDesign({ options: null })).toBeUndefined()
    expect(pickSubFormOptionsFromDesign({ options: [] })).toBeUndefined()
  })
})
