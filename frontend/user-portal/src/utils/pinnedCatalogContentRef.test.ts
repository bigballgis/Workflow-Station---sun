import { describe, expect, it } from 'vitest'
import { pinnedCatalogContentRef } from './pinnedCatalogContentRef'

describe('pinnedCatalogContentRef', () => {
  it('loads the catalog the instance was started on', () => {
    const instance = {
      functionUnitCatalogId: '116e5204-de07-41d9-9344-e74c8d697cda',
      processDefinitionKey: 'p0-dual-binding-test',
    }
    expect(pinnedCatalogContentRef(instance)).toBe('116e5204-de07-41d9-9344-e74c8d697cda')
  })

  it('never falls back to the process key, which resolves to the latest release', () => {
    const unpinned = { functionUnitCatalogId: null, processDefinitionKey: 'p0-dual-binding-test' }
    expect(pinnedCatalogContentRef(unpinned)).toBeNull()
  })

  it('returns null when the pin is blank', () => {
    expect(pinnedCatalogContentRef({ functionUnitCatalogId: '  ' })).toBeNull()
  })
})
