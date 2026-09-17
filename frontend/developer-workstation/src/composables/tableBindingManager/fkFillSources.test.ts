import { describe, expect, it } from 'vitest'
import { setFieldFillKind, kindOfField } from './fkFillSources'

describe('fkFillSources form drafts', () => {
  it('omits AUTO kinds from the persisted list', () => {
    const afterParent = setFieldFillKind(undefined, { id: 31 }, 'PARENT')
    expect(kindOfField(afterParent, 31)).toBe('PARENT')
    const afterAuto = setFieldFillKind(afterParent, { id: 31 }, 'AUTO')
    expect(afterAuto).toBeUndefined()
  })

  it('keeps ancestorBindingId only for ANCESTOR', () => {
    const next = setFieldFillKind(undefined, { id: 31 }, 'ANCESTOR', 12)
    expect(next).toEqual([{ fieldId: 31, kind: 'ANCESTOR', ancestorBindingId: 12 }])
    const parent = setFieldFillKind(next, { id: 31 }, 'PARENT', 12)
    expect(parent).toEqual([{ fieldId: 31, kind: 'PARENT', ancestorBindingId: undefined }])
  })
})
