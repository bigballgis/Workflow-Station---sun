import { describe, expect, it } from 'vitest'
import { pinnedCatalogContentRef } from './pinnedCatalogContentRef'

describe('pinnedCatalogContentRef', () => {
  it('prefers the catalog pin over the process key', () => {
    expect(pinnedCatalogContentRef({
      functionUnitCatalogId: '116e5204-de07-41d9-9344-e74c8d697cda',
      processDefinitionKey: 'p0-dual-binding-test',
    })).toBe('116e5204-de07-41d9-9344-e74c8d697cda')
  })

  it('falls back to the process key when the instance is unpinned', () => {
    expect(pinnedCatalogContentRef({
      processDefinitionKey: 'p0-dual-binding-test',
    })).toBe('p0-dual-binding-test')
  })

  it('returns null when neither pin nor key is present', () => {
    expect(pinnedCatalogContentRef({})).toBeNull()
  })
})
