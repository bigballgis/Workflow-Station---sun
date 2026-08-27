import { describe, expect, it } from 'vitest'
import { isMainTableView } from '../mainTableViewNav'

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
