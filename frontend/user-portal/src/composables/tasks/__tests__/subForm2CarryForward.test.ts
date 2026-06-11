import { describe, expect, it } from 'vitest'
import {
  collapseMiLinkChildRowsToOnePerParticipant,
  repairMisassignedLinkChildStructuralFk,
} from '../shared'

describe('sub form1 → sub form2 People carry-forward (task 61a40a76 snapshot)', () => {
  const myRowId = 'Test-000059'

  it('flatten + repair + collapse yields sub form1 age/sex/name with UUID id', () => {
    const stubRow = {
      id: 'bc601a4c-6a89-4165-bc8a-132c184893d6',
      age: 'rrr',
      sex: true,
      sub_task_id: 'Test-000059',
    }
    const subForm1Row = repairMisassignedLinkChildStructuralFk(
      {
        id: 'Test-000059',
        age: 'ii',
        sex: true,
        name: '33',
        sub_task_id: 'Test-000057',
        task_current_node: 'sub form1',
      },
      myRowId,
    )
    const collapsed = collapseMiLinkChildRowsToOnePerParticipant([stubRow, subForm1Row])
    expect(collapsed).toHaveLength(1)
    expect(collapsed[0].age).toBe('ii')
    expect(collapsed[0].name).toBe('33')
    expect(collapsed[0].id).toBe('bc601a4c-6a89-4165-bc8a-132c184893d6')
  })
})
