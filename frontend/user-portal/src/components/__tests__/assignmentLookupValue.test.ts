import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useSubTableAssignment } from '@/composables/subTableField/useSubTableAssignment'

vi.mock('@/composables/tasks/subTableAssignmentSync', () => ({ useSubTableAssignmentSync: () => vi.fn(), AssignmentReceiptConflict: class extends Error {} }))

describe('assignment display hydration', () => {
  it('adds display metadata without replacing the designed Lookup object', () => {
    const api = useSubTableAssignment({ assigneeField: 'renamed_handler' } as Parameters<typeof useSubTableAssignment>[0],
      ref([]), vi.fn(), key => key, { resolveSubTableRowPk: () => null })
    const person = { id: 'person-1', display_name: 'Test User', email: 'test@example.invalid' }
    const row = api.applyAssigneeDisplayNameToRow({ renamed_handler: person }, 'old-person')
    expect(row.renamed_handler).toBe(person)
    expect(row.assignee_display_name).toBe('Test User')
    expect(api.applyAssigneeDisplayNameToRow({ renamed_handler: 'person-1' }).renamed_handler).toBe('person-1')
  })
})
