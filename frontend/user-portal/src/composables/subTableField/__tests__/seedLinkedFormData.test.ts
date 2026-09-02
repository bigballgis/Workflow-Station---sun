import { describe, it, expect } from 'vitest'
import { seedLinkedFormDataFromFields } from '../subTableLinkFormFields'
import type { FormField } from '@/components/formRendererHelpers'

/**
 * The Link Form ("Details") dialog seeds its model from the designed form. It only ever
 * descended into `card`, so the children of every other layout container were skipped and
 * the container's own key was seeded instead — the Assignment Mode block opened with its
 * assignee / BU / role pickers blank even though the row had values.
 */
const leaf = (key: string, type = 'text'): FormField =>
  ({ key, label: key, type } as FormField)

describe('seedLinkedFormDataFromFields', () => {
  const ROW = {
    assignee: 'user-dev',
    bu_code: 'E2E_FINANCE',
    role_code: 'MANAGER',
    name: 'Row 1',
  }

  it('seeds the fields an Assignment Mode block owns as its children', () => {
    const fields: FormField[] = [
      { key: '__mi', label: '', type: 'miAssignment', children: [
        leaf('assignee'), leaf('bu_code'), leaf('role_code'),
      ] } as FormField,
      leaf('name'),
    ]

    const seeded = seedLinkedFormDataFromFields(fields, ROW)

    expect(seeded.assignee).toBe('user-dev')
    expect(seeded.bu_code).toBe('E2E_FINANCE')
    expect(seeded.role_code).toBe('MANAGER')
    expect(seeded.name).toBe('Row 1')
    // The marker itself is not a data field — it must not occupy a model key.
    expect(seeded).not.toHaveProperty('__mi')
  })

  it('still descends into cards (the case that already worked)', () => {
    const fields = [{ key: 'c', label: 'Card', type: 'card', children: [leaf('name')] } as FormField]
    expect(seedLinkedFormDataFromFields(fields, ROW).name).toBe('Row 1')
  })

  it('descends into rows, tabs and collapse panels too', () => {
    const fields: FormField[] = [
      { key: 'r', label: '', type: 'row', children: [
        { key: 'col', label: '', type: 'col', children: [leaf('assignee')] } as FormField,
      ] } as FormField,
      { key: 't', label: '', type: 'tabs',
        tabs: [{ name: 't1', label: 'T1', fields: [leaf('bu_code')] }] } as unknown as FormField,
      { key: 'p', label: '', type: 'collapse',
        collapsePanels: [{ name: 'p1', label: 'P1', fields: [leaf('role_code')] }] } as unknown as FormField,
    ]

    const seeded = seedLinkedFormDataFromFields(fields, ROW)

    expect(seeded.assignee).toBe('user-dev')
    expect(seeded.bu_code).toBe('E2E_FINANCE')
    expect(seeded.role_code).toBe('MANAGER')
  })

  it('falls back to the field default when the row has no value', () => {
    const fields = [{ key: 'missing', label: 'M', type: 'text', defaultValue: 'fallback' } as FormField]
    expect(seedLinkedFormDataFromFields(fields, ROW).missing).toBe('fallback')
  })
})
