import { describe, expect, it } from 'vitest'
import {
  applyServerRowVersionsToLiveRows,
  copyWsRowVersions,
  restoreAuthoritativeRowVersions,
  stampAuthoritativeRowVersions,
} from '../subTableRowVersionSync'

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

describe('stampAuthoritativeRowVersions', () => {
  it('replaces a stale nested version and leaves a brand-new row without one', () => {
    const subTables = {
      'dw:file': [
        { id: 'F1', file_name: 'ciri file', _wsRowVersion: 1 },
        { id: 'NEW', file_name: 'bob file3' },
      ],
      'dw:party': [{
        id: 'P',
        __subTables__: {
          'dw:file': [{ id: 'F1', file_name: 'ciri file', _wsRowVersion: 1 }],
        },
      }],
    }
    stampAuthoritativeRowVersions(subTables, {
      'dw:file': [{ id: 'F1', _wsRowVersion: 2 }],
    }, [{ bindingId: 1, tableName: 'file', primaryKeyFields: ['id'] }])
    expect(subTables['dw:file'][0]._wsRowVersion).toBe(2)
    expect(subTables['dw:file'][1]._wsRowVersion).toBeUndefined()
    const nested = subTables['dw:party'][0].__subTables__['dw:file'][0]
    expect(nested._wsRowVersion).toBe(2)
  })

  it('does not copy a version onto another table that reuses the same primary key', () => {
    const subTables = {
      'dw:file': [{ id: '1', _wsRowVersion: 1 }],
      'dw:party': [{
        id: '1',
        _wsRowVersion: 3,
        __subTables__: {
          'dw:file': [{ id: '1', _wsRowVersion: 1 }],
          'dw:party': [{ id: '1', _wsRowVersion: 3 }],
        },
      }],
    }
    stampAuthoritativeRowVersions(subTables, {
      'dw:file': [{ id: '1', _wsRowVersion: 2 }],
      'dw:party': [{ id: '1', _wsRowVersion: 4 }],
    }, [
      { bindingId: 1, tableName: 'file', primaryKeyFields: ['id'] },
      { bindingId: 2, tableName: 'party', primaryKeyFields: ['id'] },
    ])
    const nested = subTables['dw:party'][0].__subTables__
    expect(subTables['dw:file'][0]._wsRowVersion).toBe(2)
    expect(subTables['dw:party'][0]._wsRowVersion).toBe(4)
    expect(nested['dw:file'][0]._wsRowVersion).toBe(2)
    expect(nested['dw:party'][0]._wsRowVersion).toBe(4)
  })
})

describe('restoreAuthoritativeRowVersions', () => {
  it('puts the top-level snapshot version back after a nested copy overwrote it', () => {
    const binding = {
      bindingId: 1,
      tableName: 'file',
      primaryKeyFields: ['id'],
      data: [{ id: 'F1', file_name: 'ciri file', _wsRowVersion: 1 }],
    }
    const party = {
      bindingId: 2,
      tableName: 'party',
      primaryKeyFields: ['id'],
      data: [{
        id: 'P',
        __subTables__: { 'dw:file': [{ id: 'F1', file_name: 'ciri file', _wsRowVersion: 1 }] },
      }],
    }
    restoreAuthoritativeRowVersions({
      'dw:file': [{ id: 'F1', _wsRowVersion: 2 }],
      'dw:party': [{ id: 'P' }],
    }, [binding, party])
    expect(binding.data[0]._wsRowVersion).toBe(2)
    expect(party.data[0].__subTables__['dw:file'][0]._wsRowVersion).toBe(2)
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
