import { describe, it, expect } from 'vitest'
import { getSavedSubTableRowsFromVariables } from '../subTableRowHelpers'

/**
 * Regression: My Request "Sub task"'s Participants Details dialog showed a stale `name` because a
 * DIFFERENT stale copy of the row survived under the table's DISPLAY-NAME string key (e.g.
 * "Participants"), shared by several MI form bindings on one logical table — whichever binding
 * saved LAST wins that shared string key, independent of which binding is being resolved. Rather
 * than trying to arbitrate which chunk under a shared name key is "fresher", the table-name-key
 * fallback has been removed entirely: resolution is exact-bindingId only, so a stale copy under
 * a shared name key can never leak into an unrelated binding's read.
 */
describe('getSavedSubTableRowsFromVariables — exact bindingId lookup only, no table-name fallback', () => {
  it('resolves rows from its own numeric bindingId key', () => {
    const saved = {
      '50544': [{ id_idw: 'Test-000004', name: 'eeev', sub_task_id: 'Test-000004' }],
      // Shared display-name key — last written by a DIFFERENT binding's save (Assign Task / Main),
      // still holding the pre-edit value. Must never be consulted.
      Participants: [{ id_idw: 'Test-000004', name: 'eee' }],
    }
    const rows = getSavedSubTableRowsFromVariables(saved, { bindingId: 50544 }, ['id_idw'])
    expect(rows).toHaveLength(1)
    expect(rows![0].name).toBe('eeev')
  })

  it('returns undefined when the binding has no own numeric key, even if a table-name-keyed chunk exists', () => {
    const saved = {
      Participants: [{ id_idw: 'Test-000004', name: 'eee' }],
    }
    const rows = getSavedSubTableRowsFromVariables(saved, { bindingId: 50617 }, ['id_idw'])
    expect(rows).toBeUndefined()
  })

  it('never resolves via a table-name key even when that chunk is self-owned', () => {
    const saved = {
      // binding 50617 has no own numeric key. The table-name key happens to be self-owned, but it
      // must still not be consulted — only exact bindingId keys are ever read.
      Participants: [{ id_idw: 'Test-000004', name: 'eeev', sub_task_id: 'Test-000004' }],
    }
    const rows = getSavedSubTableRowsFromVariables(saved, { bindingId: 50617 }, ['id_idw'])
    expect(rows).toBeUndefined()
  })
})
