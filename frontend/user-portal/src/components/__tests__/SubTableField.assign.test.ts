import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import SubTableField from '../SubTableField.vue'
import { assignSubTableRow } from '@/api/task'

// Mock API calls
vi.mock('@/api/task', () => ({
  assignSubTableRow: vi.fn(),
  assignSubTableRowByIdentity: vi.fn(),
  getSubTableData: vi.fn(),
  getTaskDetail: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  userApi: {
    searchUsers: vi.fn()
  }
}))

// Mock i18n
vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key,
    }),
  }
})

// Mock Element Plus Message
vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn()
    }
  }
})

describe('SubTableField - Row Assignment', () => {
  const mockColumns = [
    { field: 'name', label: 'Name', type: 'text' },
    { field: 'email', label: 'Email', type: 'text' }
  ]

  const mockRows = [
    { id: 101, name: 'Zhang San', email: 'zhang@example.com', assignee: null },
    { id: 102, name: 'Li Si', email: 'li@example.com', assignee: 'user-001' },
    { id: 103, name: 'Wang Wu', email: 'wang@example.com', assignee: null }
  ]

  const globalStubs = {
    ElTable: true,
    ElTableColumn: true,
    ElButton: true,
    ElDialog: true,
    ElForm: true,
    ElFormItem: true,
    ElSelect: true,
    ElOption: true,
    ElIcon: true,
    ElEmpty: true,
    ElSlider: true,
    ElRate: true
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  /**
   * The inline Assign/Reassign button and its user-picker dialog were removed: assignment is
   * reached only through the row Edit dialog. These lock that in so the button cannot come back.
   */
  describe('No inline assign button', () => {
    function mountWith(extra: Record<string, unknown> = {}) {
      return mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: mockRows,
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123',
          ...extra,
        },
        global: { stubs: globalStubs },
      })
    }

    it('renders no Assign/Reassign button even when assignment is permitted', async () => {
      const wrapper = mountWith()
      await wrapper.vm.$nextTick()

      expect(wrapper.find('.assign-btn').exists()).toBe(false)
      expect(wrapper.html()).not.toContain('subTable.assign')
      expect(wrapper.html()).not.toContain('subTable.reassign')
    })

    it('exposes no user-picker dialog API', () => {
      const wrapper = mountWith()
      const vm = wrapper.vm as unknown as Record<string, unknown>

      expect(vm.openAssignDialog).toBeUndefined()
      expect(vm.confirmAssignment).toBeUndefined()
      expect(vm.assignDialogVisible).toBeUndefined()
    })

    it('still keeps the Assignee column available for display', async () => {
      const wrapper = mountWith()
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.showAssigneeColumn).toBe(true)
    })

    it('hides the Assignee column when there is no assignee field', async () => {
      const wrapper = mountWith({ assigneeField: undefined, showAssignButton: false })
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.showAssigneeColumn).toBe(false)
    })
  })

  /**
   * There is no standalone user-picker dialog: assignment is driven only by the row Edit dialog,
   * whose save funnel calls performSubTableRowAssignment. These cover that real entry point.
   */
  describe('Assignment Success', () => {
    it('should call API and refresh data when assignment succeeds', async () => {
      const mockResponse = {
        data: {
          success: true,
          rowId: 101,
          assigneeId: 'user-002',
          assigneeName: 'User Two'
        }
      }
      vi.mocked(assignSubTableRow).mockResolvedValue(mockResponse)

      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: [...mockRows],
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      const ok = await wrapper.vm.performSubTableRowAssignment(0, 'user-002')
      expect(ok).toBe(true)

      // Should call API
      expect(assignSubTableRow).toHaveBeenCalledWith('task-123', 101, 'user-002')

      // Should update row data
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.rows[0].assignee).toBe('user-002')

      // Should emit events
      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      expect(wrapper.emitted('assignmentChanged')).toBeTruthy()
    })

    it('should cache user names after assignment', async () => {
      const mockResponse = {
        data: {
          success: true,
          rowId: 101,
          assigneeId: 'user-002',
          assigneeName: 'User Two'
        }
      }
      vi.mocked(assignSubTableRow).mockResolvedValue(mockResponse)

      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: [...mockRows],
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      await wrapper.vm.performSubTableRowAssignment(0, 'user-002')

      // User name should be cached
      expect(wrapper.vm.userNameCache['user-002']).toBe('User Two')
      expect(wrapper.vm.getUserDisplayName('user-002')).toBe('User Two')
    })
  })

  describe('Assignment Validation', () => {
    it('should not call the API when taskId is missing', async () => {
      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: [...mockRows],
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: undefined // Missing taskId
        },
        global: {
          stubs: globalStubs
        }
      })

      const ok = await wrapper.vm.performSubTableRowAssignment(0, 'user-002')

      expect(ok).toBe(false)
      expect(assignSubTableRow).not.toHaveBeenCalled()
    })

    it('should not call the API when the row index does not exist', async () => {
      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: [...mockRows],
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      const ok = await wrapper.vm.performSubTableRowAssignment(999, 'user-002')

      expect(ok).toBe(false)
      expect(assignSubTableRow).not.toHaveBeenCalled()
    })

    it('should handle API errors gracefully', async () => {
      vi.mocked(assignSubTableRow).mockRejectedValue(new Error('Network error'))

      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: [...mockRows],
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      const ok = await wrapper.vm.performSubTableRowAssignment(0, 'user-002')

      // Surfaces the failure to the user and reports it to the caller, without throwing.
      expect(ok).toBe(false)
      expect(ElMessage.error).toHaveBeenCalled()
    })
  })

  describe('Display Logic', () => {
    it('should show "Unassigned" for rows without assignee', async () => {
      // Use fresh mock data to avoid test pollution
      const freshMockRows = [
        { id: 101, name: 'Zhang San', email: 'zhang@example.com', assignee: null },
        { id: 102, name: 'Li Si', email: 'li@example.com', assignee: 'user-001' },
        { id: 103, name: 'Wang Wu', email: 'wang@example.com', assignee: null }
      ]
      
      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: freshMockRows,
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      await wrapper.vm.$nextTick()
      
      // First row has no assignee
      expect(wrapper.vm.rows[0].assignee).toBeNull()
    })

    it('should show assignee name for assigned rows', async () => {
      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: mockRows,
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      // Set up user name cache
      wrapper.vm.userNameCache['user-001'] = 'User One'

      // Second row has assignee
      expect(wrapper.vm.getUserDisplayName('user-001')).toBe('User One')
    })

    it('should show "Assign" button for unassigned rows', async () => {
      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: mockRows,
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      await wrapper.vm.$nextTick()
      
      // Check that we have unassigned rows
      const unassignedRows = wrapper.vm.rows.filter((row: any) => !row.assignee)
      expect(unassignedRows.length).toBeGreaterThan(0)
    })

    it('should show "Reassign" button for assigned rows', async () => {
      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: mockRows,
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      await wrapper.vm.$nextTick()
      
      // Check that we have assigned rows
      const assignedRows = wrapper.vm.rows.filter((row: any) => row.assignee)
      expect(assignedRows.length).toBeGreaterThan(0)
    })
  })

  describe('Edit dialog assignee sync', () => {
    it('updates assignee_display_name and calls assign API when assignee changes via edit', async () => {
      const rowsWithAssignee = [{
        id: 101,
        name: '112',
        assignee: 'user-001',
        assignee_display_name: 'User One',
      }]
      const columnsWithAssignee = [
        { field: 'name', label: 'Name', type: 'text' },
        { field: 'assignee', label: 'assignee', type: 'lookup' },
      ]
      vi.mocked(assignSubTableRow).mockResolvedValue({
        data: {
          success: true,
          rowId: 101,
          assigneeId: 'user-002',
          assigneeName: 'User Two',
        },
      })

      const wrapper = mount(SubTableField, {
        props: {
          title: 'Sub Task',
          columns: columnsWithAssignee,
          modelValue: [...rowsWithAssignee],
          primaryKeyFields: ['id'],
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123',
          editable: true,
        },
        global: {
          stubs: globalStubs,
        },
      })

      wrapper.vm.dialogMode = 'edit'
      wrapper.vm.editingRowIndex = 0
      wrapper.vm.dialogInitialData = { ...rowsWithAssignee[0] }

      await wrapper.vm.handleDialogSave({
        name: '112',
        assignee: {
          id: 'user-002',
          display_name: 'User Two',
          username: 'user2',
        },
      })

      await wrapper.vm.$nextTick()
      expect(wrapper.vm.rows[0].assignee_display_name).toBe('User Two')
      expect(assignSubTableRow).toHaveBeenCalledWith('task-123', 101, 'user-002')
      expect(wrapper.emitted('assignmentChanged')).toBeTruthy()
    })

    it('does not call assign API when assignee is unchanged in edit', async () => {
      const rowsWithAssignee = [{
        id: 101,
        name: '112',
        assignee: 'user-001',
        assignee_display_name: 'User One',
      }]
      const columnsWithAssignee = [
        { field: 'name', label: 'Name', type: 'text' },
        { field: 'assignee', label: 'assignee', type: 'lookup' },
      ]

      const wrapper = mount(SubTableField, {
        props: {
          title: 'Sub Task',
          columns: columnsWithAssignee,
          modelValue: [...rowsWithAssignee],
          primaryKeyFields: ['id'],
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123',
          editable: true,
        },
        global: {
          stubs: globalStubs,
        },
      })

      wrapper.vm.dialogMode = 'edit'
      wrapper.vm.editingRowIndex = 0
      wrapper.vm.dialogInitialData = { ...rowsWithAssignee[0] }

      await wrapper.vm.handleDialogSave({
        name: '113',
        assignee: 'user-001',
      })

      expect(assignSubTableRow).not.toHaveBeenCalled()
      expect(wrapper.vm.rows[0].assignee_display_name).toBe('User One')
    })

    it('edit save keeps intentionally cleared fields empty (no seed restore)', async () => {
      const existing = [{
        id: 101,
        test: 'was-filled',
        name: 'keep',
        assignee: 'user-001',
        assignee_display_name: 'User One',
      }]
      const columns = [
        { field: 'test', label: 'test', type: 'text' },
        { field: 'name', label: 'Name', type: 'text' },
        { field: 'assignee', label: 'assignee', type: 'lookup' },
      ]

      const wrapper = mount(SubTableField, {
        props: {
          title: 'Participants',
          columns,
          modelValue: [...existing],
          primaryKeyFields: ['id'],
          assigneeField: 'assignee',
          editable: true,
        },
        global: { stubs: globalStubs },
      })

      wrapper.vm.dialogMode = 'edit'
      wrapper.vm.editingRowIndex = 0
      wrapper.vm.dialogInitialData = { ...existing[0] }

      await wrapper.vm.handleDialogSave({
        id: 101,
        test: '',
        name: 'keep',
        assignee: '',
      })

      expect(wrapper.vm.rows[0].test).toBe('')
      expect(wrapper.vm.rows[0].assignee).toBe('')
      expect(wrapper.vm.rows[0].assignee_display_name).toBeUndefined()
      expect(wrapper.vm.rows[0].name).toBe('keep')
    })
  })
})
