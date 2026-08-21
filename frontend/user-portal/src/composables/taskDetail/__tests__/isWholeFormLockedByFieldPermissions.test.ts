import { describe, it, expect } from 'vitest'
import { isWholeFormLockedByFieldPermissions } from '../subTableRowUtils'

const RULE_AB = [{ field: 'a' }, { field: 'b' }]

describe('isWholeFormLockedByFieldPermissions', () => {
  it('locks when every main-table rule field has an explicit READONLY permission', () => {
    expect(isWholeFormLockedByFieldPermissions({ a: 'READONLY', b: 'READONLY' }, RULE_AB)).toBe(true)
  })

  it('does not lock when any main-table rule field is EDITABLE', () => {
    expect(isWholeFormLockedByFieldPermissions({ a: 'READONLY', b: 'EDITABLE' }, RULE_AB)).toBe(false)
  })

  it('does not lock when a main-table rule field has no permission entry at all (defaults editable)', () => {
    // Only "a" is configured READONLY; "b" is unconfigured — same as the original bug: a form
    // with one READONLY field and the rest untouched must NOT read as "100% READONLY".
    expect(isWholeFormLockedByFieldPermissions({ a: 'READONLY' }, RULE_AB)).toBe(false)
  })

  it('does not lock an empty or absent map (backward compatible default)', () => {
    expect(isWholeFormLockedByFieldPermissions({}, RULE_AB)).toBe(false)
    expect(isWholeFormLockedByFieldPermissions(null, RULE_AB)).toBe(false)
    expect(isWholeFormLockedByFieldPermissions(undefined, RULE_AB)).toBe(false)
  })

  it('does not lock when mainTableRule is empty/absent, even if fieldPermissions looks fully READONLY', () => {
    expect(isWholeFormLockedByFieldPermissions({ a: 'READONLY', b: 'READONLY' })).toBe(false)
    expect(isWholeFormLockedByFieldPermissions({ a: 'READONLY', b: 'READONLY' }, [])).toBe(false)
  })

  it('ignores composite bindingId:fieldName sub-table keys entirely', () => {
    // Only sub-table permissions configured, all READONLY — main table has zero configured
    // permissions, so the whole form must stay unlocked.
    expect(isWholeFormLockedByFieldPermissions({
      '50544:bu_code': 'READONLY',
      '50544:role_code': 'READONLY',
    }, RULE_AB)).toBe(false)
  })

  it('ignores a stale bare key belonging to a sub-table field, not the main table', () => {
    // "id_idw" is not in mainTableRule (a SUB-table's own field name colliding in the flat
    // map) — it must not count as "the main table's id_idw field is READONLY".
    expect(isWholeFormLockedByFieldPermissions({
      a: 'READONLY',
      b: 'READONLY',
      id_idw: 'READONLY',
    }, RULE_AB)).toBe(true) // still locks because a AND b (the real main-table fields) are both READONLY
  })

  it('locks on main-table keys even when sub-table composite keys are mixed in and EDITABLE', () => {
    expect(isWholeFormLockedByFieldPermissions({
      a: 'READONLY',
      b: 'READONLY',
      '50544:name': 'EDITABLE',
    }, RULE_AB)).toBe(true)
  })

  it('reproduces the FU 50005 regression: one main-table READONLY field plus stale sub-table bare keys must not lock the form', () => {
    // id = main table's own Meeting ID (legitimately READONLY); id_idw/main_id/sub_task_id are
    // stale sub-table bare-key entries from before the composite-key fix. __request_id/I/lookup/
    // fileupload are unconfigured (implicitly editable) main-table fields.
    const mainTableRule = [
      { field: '__request_id' }, { field: 'I' }, { field: 'id' }, { field: 'lookup' }, { field: 'fileupload' },
    ]
    expect(isWholeFormLockedByFieldPermissions({
      id: 'READONLY',
      id_idw: 'READONLY',
      main_id: 'READONLY',
      sub_task_id: 'READONLY',
      '50544:bu_code': 'READONLY',
      '50544:role_code': 'READONLY',
    }, mainTableRule)).toBe(false)
  })
})
