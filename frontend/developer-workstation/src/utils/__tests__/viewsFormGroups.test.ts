import { describe, expect, it } from 'vitest'
import {
  buildViewsFormGroups,
  countGroupedViewsForms,
  isMainTableDefinition,
} from '../formDesigner'

const mainTable = { id: 1, tableName: 'Meeting', tableType: 'MAIN' }
const subTable = { id: 2, tableName: 'Participants', tableType: 'SUB' }

function detailForm(id: number, formName: string, tableId?: number) {
  return {
    id,
    formName,
    formType: 'DETAIL',
    ...(tableId == null ? {} : { boundTableId: tableId }),
  }
}

describe('isMainTableDefinition', () => {
  it('matches MAIN regardless of casing and treats anything else as not MAIN', () => {
    expect(isMainTableDefinition({ tableType: 'MAIN' })).toBe(true)
    expect(isMainTableDefinition({ tableType: 'main' })).toBe(true)
    expect(isMainTableDefinition({ tableType: 'SUB' })).toBe(false)
    expect(isMainTableDefinition({})).toBe(false)
    expect(isMainTableDefinition(null)).toBe(false)
  })
})

describe('buildViewsFormGroups', () => {
  it('gives the MAIN table no group of its own', () => {
    const groups = buildViewsFormGroups(
      [detailForm(10, 'Participants Detail', 2)],
      [mainTable, subTable],
      [{ id: 100, viewName: 'All meetings', mainTableId: 1 }],
    )
    expect(groups.map(g => g.label)).toEqual(['Participants'])
  })

  /**
   * A MAIN row opens the request detail page, never a designed form, so a DETAIL form bound to the
   * MAIN table is unreachable at runtime and is not listed at all.
   */
  it('drops a MAIN-bound detail form rather than listing it under a bucket', () => {
    const groups = buildViewsFormGroups(
      [detailForm(11, 'Legacy Meeting Detail', 1)],
      [mainTable, subTable],
      [],
    )
    expect(groups).toEqual([])
  })

  it('drops forms with no resolvable table', () => {
    expect(buildViewsFormGroups([detailForm(12, 'Floating Detail')], [subTable], [])).toEqual([])
  })

  it('drops a form bound to a table that is not in the catalog', () => {
    expect(buildViewsFormGroups([detailForm(15, 'Stale', 999)], [subTable], [])).toEqual([])
  })

  it('still groups SUB tables with their forms and views', () => {
    const groups = buildViewsFormGroups(
      [detailForm(13, 'Participants Detail', 2)],
      [subTable],
      [{ id: 200, viewName: 'Roster', mainTableId: 2 }],
    )
    expect(groups).toHaveLength(1)
    expect(groups[0].forms).toHaveLength(1)
    expect(groups[0].views.map((v: any) => v.viewName)).toEqual(['Roster'])
  })

  /** A SUB table whose views have no detail form yet is exactly where a choice is made. */
  it('keeps a SUB group that has views but no forms', () => {
    const groups = buildViewsFormGroups(
      [],
      [subTable],
      [{ id: 201, viewName: 'Roster', mainTableId: 2 }],
    )
    expect(groups.map(g => g.label)).toEqual(['Participants'])
  })

  it('drops tables that hold neither a form nor a view', () => {
    expect(buildViewsFormGroups([], [subTable], [])).toEqual([])
  })

  /** Non-DETAIL forms belong to the Task/Request tabs and must not leak in here. */
  it('ignores forms that are not DETAIL', () => {
    const groups = buildViewsFormGroups(
      [{ id: 14, formName: 'Task form', formType: 'TASK', boundTableId: 2 }],
      [subTable],
      [],
    )
    expect(groups).toEqual([])
  })
})

describe('countGroupedViewsForms', () => {
  /**
   * The badge must agree with the rendered rows: unreachable forms are not listed, so counting
   * every DETAIL form would label the tab with rows that are never drawn.
   */
  it('counts only the forms the groups actually render', () => {
    const forms = [
      detailForm(20, 'Main bound', 1),
      detailForm(21, 'Sub bound', 2),
      detailForm(22, 'Unbound'),
      detailForm(23, 'Bound to a table not in the catalog', 999),
    ]
    const tables = [mainTable, subTable]
    const rendered = buildViewsFormGroups(forms, tables, []).flatMap(g => g.forms)
    expect(countGroupedViewsForms(forms, tables, [])).toBe(rendered.length)
    expect(countGroupedViewsForms(forms, tables, [])).toBe(1)
  })

  it('is zero when every detail form is unreachable', () => {
    const forms = [detailForm(24, 'Main bound', 1), detailForm(25, 'Unbound')]
    expect(countGroupedViewsForms(forms, [mainTable, subTable], [])).toBe(0)
  })
})
