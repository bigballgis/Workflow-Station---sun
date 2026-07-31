import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SubTableField from '../SubTableField.vue'

vi.mock('@/api/task', () => ({
  assignSubTableRow: vi.fn(),
  assignSubTableRowByIdentity: vi.fn(),
  getSubTableData: vi.fn(),
  getTaskDetail: vi.fn(),
}))
vi.mock('@/api/user', () => ({ userApi: { searchUsers: vi.fn().mockResolvedValue([]) } }))
vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (k: string) => k }) }
})

const CONFIG = {
  allowUser: true,
  allowRole: true,
  assigneeField: 'assignee',
  roleField: 'role_code',
  buField: 'bu_code',
}

/**
 * A row assigned to a BU + role has no named assignee — the Assignee cell must fall back
 * to the shared role pool. This used to require an `miAssignment` marker in the form
 * design, so sub-tables whose form predates the component resolved no config and showed
 * an empty cell instead of the role.
 */
describe('SubTableField — role pool in the Assignee cell', () => {
  const mountField = (formFields?: unknown[]) =>
    mount(SubTableField, {
      props: {
        title: 'Participants',
        columns: [{ field: 'name', label: 'Name', type: 'text' }],
        modelValue: [
          { id: 1, name: 'by person', assignee: 'user-001' },
          { id: 2, name: 'by role', assignee: null, role_code: 'HMDC_Index_Role', bu_code: 'hase-hmdc' },
        ],
        assignmentConfig: CONFIG,
        ...(formFields ? { formFields } : {}),
      } as never,
      global: { stubs: { ElTable: true, ElTableColumn: true, ElButton: true, ElDialog: true, ElIcon: true, ElEmpty: true, Teleport: true } },
    })

  it('resolves the role code without requiring an miAssignment marker', () => {
    const vm = mountField().vm as unknown as {
      rowRoleCode: (r: Record<string, unknown>) => string
    }
    expect(vm.rowRoleCode({ role_code: 'HMDC_Index_Role' })).toBe('HMDC_Index_Role')
  })

  it('still resolves when the form design does carry the marker', () => {
    const vm = mountField([{ key: 'm', label: '', type: 'miAssignment' }]).vm as unknown as {
      rowRoleCode: (r: Record<string, unknown>) => string
    }
    expect(vm.rowRoleCode({ role_code: 'MANAGER' })).toBe('MANAGER')
  })

  it('returns empty for a person-assigned row so the name renders instead', () => {
    const vm = mountField().vm as unknown as {
      rowRoleCode: (r: Record<string, unknown>) => string
    }
    expect(vm.rowRoleCode({ assignee: 'user-001' })).toBe('')
    expect(vm.rowRoleCode({ role_code: '   ' })).toBe('')
  })
})
