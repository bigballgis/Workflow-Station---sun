import { describe, expect, it } from 'vitest'
import {
  filterTableGroups,
  groupViewsByTable,
  pickDefaultView,
  sortViewsByName,
  tableGroupKey,
} from './mainTableViewNav'

const attachment = { id: 1, viewName: 'Zebra', isDefault: true, tableId: 10, tableLabel: 'ATM Attachment' }
const attachmentB = { id: 2, viewName: 'Alpha', isDefault: false, tableId: 10, tableLabel: 'ATM Attachment' }
const caseView = { id: 3, viewName: 'ATM Case', isDefault: true, tableId: 11, tableLabel: 'ATM Case' }

describe('mainTableViewNav', () => {
  it('groupViewsByTable keeps insertion order and ignores empty input', () => {
    expect(groupViewsByTable([])).toEqual([])
    const groups = groupViewsByTable([attachment, caseView])
    expect(groups.map(g => g.label)).toEqual(['ATM Attachment', 'ATM Case'])
    expect(groups.every(g => g.views.length > 0)).toBe(true)
  })

  it('sortViewsByName is alphabetical and pickDefaultView prefers isDefault', () => {
    expect(sortViewsByName([attachment, attachmentB]).map(v => v.viewName)).toEqual(['Alpha', 'Zebra'])
    expect(pickDefaultView([attachmentB, attachment])?.id).toBe(1)
    expect(pickDefaultView([attachmentB])?.id).toBe(2)
    expect(pickDefaultView(
      [{ id: 8, viewName: 'Zebra', tableId: 10, tableLabel: 'T' }, { id: 9, viewName: 'Alpha', tableId: 10, tableLabel: 'T' }],
      'en',
    )?.id).toBe(9)
  })

  it('filterTableGroups hides tables with no matching name or view', () => {
    const groups = groupViewsByTable([attachment, attachmentB, caseView])
    expect(filterTableGroups(groups, 'case').map(g => g.label)).toEqual(['ATM Case'])
    expect(filterTableGroups(groups, 'alpha').map(g => g.label)).toEqual(['ATM Attachment'])
    expect(filterTableGroups(groups, 'missing')).toEqual([])
  })

  it('tableGroupKey prefers tableId', () => {
    expect(tableGroupKey({ tableId: 10, label: 'ATM Attachment' })).toBe('10')
    expect(tableGroupKey({ tableId: null, label: 'Orphan' })).toBe('Orphan')
  })

  // The "Request" tag is rendered per group, so the group has to carry the table type.
  it('groupViewsByTable carries tableType onto the group', () => {
    const mainView = { id: 4, viewName: 'All cases', tableId: 11, tableLabel: 'ATM Case', tableType: 'MAIN' }
    const subView = { id: 5, viewName: 'Files', tableId: 10, tableLabel: 'ATM Attachment', tableType: 'SUB' }
    const groups = groupViewsByTable([mainView, subView])
    expect(groups.map(g => g.tableType)).toEqual(['MAIN', 'SUB'])
  })

  it('groupViewsByTable leaves tableType null when views do not carry one', () => {
    expect(groupViewsByTable([attachment])[0].tableType).toBeNull()
  })
})
