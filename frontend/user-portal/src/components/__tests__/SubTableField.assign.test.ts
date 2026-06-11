import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import SubTableField from '../SubTableField.vue'
import { assignSubTableRow } from '@/api/task'
import { userApi } from '@/api/user'

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

describe('SubTableField - Assign Button', () => {
  const mockColumns = [
    { field: 'name', label: 'Name', type: 'text' },
    { field: 'email', label: 'Email', type: 'text' }
  ]

  const mockRows = [
    { id: 101, name: 'Zhang San', email: 'zhang@example.com', assignee: null },
    { id: 102, name: 'Li Si', email: 'li@example.com', assignee: 'user-001' },
    { id: 103, name: 'Wang Wu', email: 'wang@example.com', assignee: null }
  ]

  const mockUsers = [
    { id: 'user-001', name: 'User One', username: 'user1' },
    { id: 'user-002', name: 'User Two', username: 'user2' },
    { id: 'user-003', name: 'User Three', username: 'user3' }
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
    vi.mocked(userApi.searchUsers).mockResolvedValue(mockUsers)
  })

  describe('Button Permission Control', () => {
    it('should show assign button when canAssign is true', async () => {
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
      
      // Check that assignee field is in props
      expect(wrapper.props('assigneeField')).toBe('assignee')
      expect(wrapper.props('canAssign')).toBe(true)
      expect(wrapper.props('showAssignButton')).toBe(true)
    })

    it('should not show assign button when canAssign is false', async () => {
      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: mockRows,
          showAssignButton: true,
          assigneeField: 'assignee',
          canAssign: false,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      await wrapper.vm.$nextTick()
      expect(wrapper.props('canAssign')).toBe(false)
    })

    it('should not show assign column when showAssignButton is false', async () => {
      const wrapper = mount(SubTableField, {
        props: {
          title: 'Test Table',
          columns: mockColumns,
          modelValue: mockRows,
          showAssignButton: false,
          assigneeField: 'assignee',
          canAssign: true,
          taskId: 'task-123'
        },
        global: {
          stubs: globalStubs
        }
      })

      await wrapper.vm.$nextTick()
      expect(wrapper.props('showAssignButton')).toBe(false)
    })
  })

  describe('User Picker Dialog', () => {
    it('should open user picker dialog when assign button is clicked', async () => {
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

      // Open dialog programmatically
      await wrapper.vm.openAssignDialog(mockRows[0], 0)
      await wrapper.vm.$nextTick()

      // Dialog should be visible
      expect(wrapper.vm.assignDialogVisible).toBe(true)
      expect(wrapper.vm.currentAssignRow).toEqual(mockRows[0])
      expect(wrapper.vm.currentAssignRowIndex).toBe(0)
    })

    it('should load users when dialog is opened', async () => {
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

      // Open dialog
      await wrapper.vm.openAssignDialog(mockRows[0], 0)
      await wrapper.vm.onAssignDialogOpened()

      // Should call searchUsers
      expect(userApi.searchUsers).toHaveBeenCalledWith('')
    })

    it('should close dialog when cancel is clicked', async () => {
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

      // Open dialog
      await wrapper.vm.openAssignDialog(mockRows[0], 0)
      expect(wrapper.vm.assignDialogVisible).toBe(true)

      // Close dialog
      wrapper.vm.assignDialogVisible = false
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.assignDialogVisible).toBe(false)
    })
  })

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

      // Open dialog and select user
      await wrapper.vm.openAssignDialog(mockRows[0], 0)
      wrapper.vm.selectedAssigneeId = 'user-002'

      // Confirm assignment
      await wrapper.vm.confirmAssignment()

      // Should call API
      expect(assignSubTableRow).toHaveBeenCalledWith('task-123', 101, 'user-002')

      // Should update row data
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.rows[0].assignee).toBe('user-002')

      // Should emit events
      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      expect(wrapper.emitted('assignmentChanged')).toBeTruthy()

      // Dialog should be closed
      expect(wrapper.vm.assignDialogVisible).toBe(false)
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

      await wrapper.vm.openAssignDialog(mockRows[0], 0)
      wrapper.vm.selectedAssigneeId = 'user-002'
      await wrapper.vm.confirmAssignment()

      // User name should be cached
      expect(wrapper.vm.userNameCache['user-002']).toBe('User Two')
      expect(wrapper.vm.getUserDisplayName('user-002')).toBe('User Two')
    })
  })

  describe('Assignment Validation', () => {
    it('should show warning when no user is selected', async () => {
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

      await wrapper.vm.openAssignDialog(mockRows[0], 0)
      wrapper.vm.selectedAssigneeId = ''

      await wrapper.vm.confirmAssignment()

      expect(ElMessage.warning).toHaveBeenCalledWith('subTable.pleaseSelectUser')
      expect(assignSubTableRow).not.toHaveBeenCalled()
    })

    it('should show error when taskId is missing', async () => {
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

      await wrapper.vm.openAssignDialog(mockRows[0], 0)
      wrapper.vm.selectedAssigneeId = 'user-002'

      await wrapper.vm.confirmAssignment()

      expect(ElMessage.error).toHaveBeenCalledWith('subTable.assignmentFailed')
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

      await wrapper.vm.openAssignDialog(mockRows[0], 0)
      wrapper.vm.selectedAssigneeId = 'user-002'

      await wrapper.vm.confirmAssignment()

      expect(ElMessage.error).toHaveBeenCalled()
      expect(wrapper.vm.assignDialogVisible).toBe(true) // Dialog stays open on error
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
  })
})
