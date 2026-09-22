import { describe, expect, it } from 'vitest'
import { applyServerRowVersionsToLiveRows, copyWsRowVersions } from '../subTableRowVersionSync'

describe('copyWsRowVersions', () => {
  it('replaces the stale version and keeps the edit on the page', () => {
    const page = [{ id: 'F1', title: 'edited on page', _wsRowVersion: 1 }]
    const server = [{ id: 'F1', title: 'saved earlier', _wsRowVersion: 4 }]
    copyWsRowVersions(page, server, ['id'])
    expect(page).toEqual([{ id: 'F1', title: 'edited on page', _wsRowVersion: 4 }])
  })

  it('leaves a new row alone when the server has no version for it', () => {
    const page = [{ id: 'NEW', title: 'just added' }]
    copyWsRowVersions(page, [{ id: 'F1', _wsRowVersion: 4 }], ['id'])
    expect(page[0]._wsRowVersion).toBeUndefined()
  })

  it('does not copy a missing server version onto a page row', () => {
    const page = [{ id: 'P1', _wsRowVersion: 2 }]
    copyWsRowVersions(page, [{ id: 'P1' }], ['id'])
    expect(page[0]._wsRowVersion).toBe(2)
  })
})

describe('applyServerRowVersionsToLiveRows', () => {
  it('patches the grid, the form store, and the delete baseline together', () => {
    const binding = {
      bindingId: 50705,
      tableName: 'p0_dual_file',
      designerTableName: 'p0_dual_file',
      primaryKeyFields: ['id'],
      data: [{ id: 'F1', title: 'edited', _wsRowVersion: 1 }],
    }
    const formSubTables = {
      'dw:p0_dual_file': [{ id: 'F1', title: 'edited', _wsRowVersion: 1 }],
    }
    const baseline = {
      'dw:p0_dual_file': [{ id: 'F1', title: 'loaded', _wsRowVersion: 1 }],
    }
    applyServerRowVersionsToLiveRows({
      bindings: [binding],
      formSubTables,
      baseline,
      serverSubTables: {
        'dw:p0_dual_file': [{ id: 'F1', title: 'server', _wsRowVersion: 4 }],
      },
    })
    expect(binding.data[0]._wsRowVersion).toBe(4)
    expect(binding.data[0].title).toBe('edited')
    expect(formSubTables['dw:p0_dual_file'][0]._wsRowVersion).toBe(4)
    expect(baseline['dw:p0_dual_file'][0]._wsRowVersion).toBe(4)
    expect(baseline['dw:p0_dual_file'][0].title).toBe('loaded')
  })
})
