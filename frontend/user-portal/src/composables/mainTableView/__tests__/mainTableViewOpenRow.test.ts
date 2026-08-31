import { describe, expect, it } from 'vitest'
import { isMainTableView, resolveRowOpenTarget } from '../mainTableViewNav'

/**
 * `isMainTableView` is the branch that decides where a row click lands: MAIN views open the
 * request detail page, everything else falls through to the view's bound detail form. Guarding it
 * here keeps the two call sites — `openRow` and the "Request" tag in the views list — honest about
 * what counts as a MAIN view.
 */
describe('isMainTableView', () => {
  it('recognises a MAIN table view', () => {
    expect(isMainTableView({ tableType: 'MAIN' })).toBe(true)
  })

  // The value travels from a DB column through JSON, so casing is not guaranteed.
  it('is case-insensitive', () => {
    expect(isMainTableView({ tableType: 'main' })).toBe(true)
    expect(isMainTableView({ tableType: 'Main' })).toBe(true)
  })

  it('rejects SUB views, which keep opening their bound detail form', () => {
    expect(isMainTableView({ tableType: 'SUB' })).toBe(false)
  })

  /**
   * A portal talking to a backend that predates the tableType field sees it missing. Defaulting to
   * "not MAIN" keeps the old detailFormId behaviour rather than routing every row to a request page.
   */
  it('treats a missing or empty table type as not MAIN', () => {
    expect(isMainTableView({})).toBe(false)
    expect(isMainTableView({ tableType: null })).toBe(false)
    expect(isMainTableView({ tableType: '' })).toBe(false)
    expect(isMainTableView(undefined)).toBe(false)
    expect(isMainTableView(null)).toBe(false)
  })
})

describe('resolveRowOpenTarget', () => {
  const MAIN = { tableType: 'MAIN' as const, detailFormId: null }
  const SUB_BOUND = { tableType: 'SUB' as const, detailFormId: 77 }
  const SUB_UNBOUND = { tableType: 'SUB' as const, detailFormId: null }

  it('opens the request page for a MAIN row', () => {
    expect(resolveRowOpenTarget(MAIN, 9, { processInstanceId: 'pi-1' }, 'row-1'))
      .toEqual({ kind: 'request', processInstanceId: 'pi-1' })
  })

  it('refuses a MAIN row that carries no process instance', () => {
    expect(resolveRowOpenTarget(MAIN, 9, { processInstanceId: null }, 'row-1'))
      .toEqual({ kind: 'refuse', messageKey: 'noDetailPage' })
  })

  it('opens the bound detail form for a non-MAIN row', () => {
    expect(resolveRowOpenTarget(SUB_BOUND, 9, { processInstanceId: 'pi-1' }, 'row-1'))
      .toEqual({ kind: 'detail', viewId: 9, rowKey: 'row-1' })
  })

  /**
   * The regression this function exists for: a view with no detail form bound in Developer
   * Workstation used to fall through to `/applications/{processInstanceId}`, so clicking an
   * Attachment row landed on the whole request — a different record than the one clicked. The
   * missing binding must be reported, and reported even though the row *could* address a request.
   */
  it('refuses a non-MAIN row when no detail form is bound, instead of opening the request', () => {
    expect(resolveRowOpenTarget(SUB_UNBOUND, 9, { processInstanceId: 'pi-1' }, 'row-1'))
      .toEqual({ kind: 'refuse', messageKey: 'noDetailForm' })
    expect(resolveRowOpenTarget(SUB_UNBOUND, 9, { processInstanceId: null }, 'row-1'))
      .toEqual({ kind: 'refuse', messageKey: 'noDetailForm' })
  })

  // An unresolved active view id cannot build the detail route, and is the same missing binding.
  it('refuses when the view id is not resolved', () => {
    expect(resolveRowOpenTarget(SUB_BOUND, null, { processInstanceId: 'pi-1' }, 'row-1'))
      .toEqual({ kind: 'refuse', messageKey: 'noDetailForm' })
  })

  // Bound form, but nothing to address the row by — a distinct failure, so a distinct message.
  it('refuses a bound view when the row has no key', () => {
    expect(resolveRowOpenTarget(SUB_BOUND, 9, { processInstanceId: 'pi-1' }, null))
      .toEqual({ kind: 'refuse', messageKey: 'rowNotAddressable' })
  })
})
