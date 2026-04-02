import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ElMessage, ElMessageBox } from 'element-plus'
import SubTaskForm from '../SubTaskForm.vue'
import * as taskApi from '@/api/task'

// Mock API
vi.mock('@/api/task', () => ({
  getSubTaskFormData: vi.fn(),
  completeTask: vi.fn()
}))

// Mock router
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mockPush
  })
}))

// Mock i18n
vi.mock('vue-i18n', async () => {
  const actual = await vi.importActual('vue-i18n')
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string, params?: any) => {
        const translations: Record<string, string> = {
          'task.loadingFormData': 'Loading form data...',
          'task.mainTaskInfo': 'Main Task Information',
          'task.yourTaskInfo': 'Your Task Information',
          'task.noFormData': 'No form data',
          'task.loadFormDataFailed': 'Failed to load form data',
          'task.confirmSubmit': 'Are you sure you want to submit this task?',
          'task.submitSuccess': 'Task submitted successfully',
          'task.submitFailed': 'Failed to submit task',
          'task.confirmCancel': 'Are you sure you want to cancel?',
          'task.dataModifiedPleaseRefresh': 'Data has been modified, please refresh and try again',
          'common.submit': 'Submit',
          'common.cancel': 'Cancel',
          'common.confirm': 'Confirm',
          'common.yes': 'Yes',
          'common.no': 'No',
          'validation.required': `${params?.field} is required`
        }
        return translations[key] || key
      }
    })
  }
})

describe('SubTaskForm', () => {
  const mockFormData = {
    taskId: 'task-001',
    mainFormData: {
      meetingTitle: '2026 Q2 Product Planning Meeting',
      meetingTime: '2026-04-15T14:00:00',
      meetingLocation: 'Conference Room 3F',
      organizer: 'Manager Zhang'
    },
    mainFormFields: [
      { name: 'meetingTitle', label: 'Meeting Title', type: 'text' },
      { name: 'meetingTime', label: 'Meeting Time', type: 'datetime' },
      { name: 'meetingLocation', label: 'Meeting Location', type: 'text' },
      { name: 'organizer', label: 'Organizer', type: 'text' }
    ],
    subTableRowData: {
      id: 101,
      name: 'Zhang San',
      department: 'Technology',
      email: 'zhang@example.com',
      willAttend: null,
      dietaryPreference: null,
      remarks: null
    },
    subFormFields: [
      { name: 'name', label: 'Name', type: 'text', readonly: true },
      { name: 'department', label: 'Department', type: 'text', readonly: true },
      { name: 'email', label: 'Email', type: 'email', readonly: true },
      { name: 'willAttend', label: 'Will Attend', type: 'select', required: true, options: [
        { label: 'Yes', value: 'yes' },
        { label: 'No', value: 'no' }
      ]},
      { name: 'dietaryPreference', label: 'Dietary Preference', type: 'select', options: [
        { label: 'None', value: 'none' },
        { label: 'Vegetarian', value: 'vegetarian' }
      ]},
      { name: 'remarks', label: 'Remarks', type: 'textarea' }
    ],
    rowVersion: 1
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should load and display form data on mount', async () => {
    vi.mocked(taskApi.getSubTaskFormData).mockResolvedValue({
      data: mockFormData
    } as any)

    const wrapper = mount(SubTaskForm, {
      props: {
        taskId: 'task-001'
      },
      global: {
        stubs: {
          ElCard: true,
          ElDescriptions: true,
          ElDescriptionsItem: true,
          ElForm: true,
          ElFormItem: true,
          ElRow: true,
          ElCol: true,
          ElButton: true,
          ElIcon: true,
          FieldRenderer: true
        }
      }
    })

    await flushPromises()

    expect(taskApi.getSubTaskFormData).toHaveBeenCalledWith('task-001')
    expect(wrapper.vm.formData).toEqual(mockFormData)
    expect(wrapper.vm.subFormData).toEqual(mockFormData.subTableRowData)
  })

  it('should show error message when loading fails', async () => {
    const errorMessage = 'Network error'
    vi.mocked(taskApi.getSubTaskFormData).mockRejectedValue(new Error(errorMessage))
    const errorSpy = vi.spyOn(ElMessage, 'error')

    mount(SubTaskForm, {
      props: {
        taskId: 'task-001'
      },
      global: {
        stubs: {
          ElCard: true,
          ElEmpty: true
        }
      }
    })

    await flushPromises()

    expect(errorSpy).toHaveBeenCalledWith(errorMessage)
  })

  it('should format field values correctly for display', async () => {
    vi.mocked(taskApi.getSubTaskFormData).mockResolvedValue({
      data: mockFormData
    } as any)

    const wrapper = mount(SubTaskForm, {
      props: {
        taskId: 'task-001'
      },
      global: {
        stubs: {
          ElCard: true,
          ElDescriptions: true,
          ElDescriptionsItem: true,
          ElForm: true,
          ElFormItem: true,
          ElRow: true,
          ElCol: true,
          ElButton: true,
          ElIcon: true,
          FieldRenderer: true
        }
      }
    })

    await flushPromises()

    // Test null/undefined formatting
    expect(wrapper.vm.formatFieldValue(null, { name: 'test', label: 'Test', type: 'text' })).toBe('-')
    expect(wrapper.vm.formatFieldValue(undefined, { name: 'test', label: 'Test', type: 'text' })).toBe('-')
    expect(wrapper.vm.formatFieldValue('', { name: 'test', label: 'Test', type: 'text' })).toBe('-')

    // Test boolean formatting
    expect(wrapper.vm.formatFieldValue(true, { name: 'test', label: 'Test', type: 'boolean' })).toBe('Yes')
    expect(wrapper.vm.formatFieldValue(false, { name: 'test', label: 'Test', type: 'boolean' })).toBe('No')

    // Test select formatting with options
    const selectField = {
      name: 'status',
      label: 'Status',
      type: 'select',
      options: [
        { label: 'Active', value: 'active' },
        { label: 'Inactive', value: 'inactive' }
      ]
    }
    expect(wrapper.vm.formatFieldValue('active', selectField)).toBe('Active')
    expect(wrapper.vm.formatFieldValue('unknown', selectField)).toBe('unknown')
  })

  it('should handle field changes', async () => {
    vi.mocked(taskApi.getSubTaskFormData).mockResolvedValue({
      data: mockFormData
    } as any)

    const wrapper = mount(SubTaskForm, {
      props: {
        taskId: 'task-001'
      },
      global: {
        stubs: {
          ElCard: true,
          ElDescriptions: true,
          ElDescriptionsItem: true,
          ElForm: true,
          ElFormItem: true,
          ElRow: true,
          ElCol: true,
          ElButton: true,
          ElIcon: true,
          FieldRenderer: true
        }
      }
    })

    await flushPromises()

    wrapper.vm.handleFieldChange('willAttend', 'yes')
    expect(wrapper.vm.subFormData.willAttend).toBe('yes')

    wrapper.vm.handleFieldChange('remarks', 'Need projector')
    expect(wrapper.vm.subFormData.remarks).toBe('Need projector')
  })

  it('should submit form successfully', async () => {
    vi.mocked(taskApi.getSubTaskFormData).mockResolvedValue({
      data: mockFormData
    } as any)
    vi.mocked(taskApi.completeTask).mockResolvedValue({ data: {} } as any)
    
    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    const successSpy = vi.spyOn(ElMessage, 'success')

    const wrapper = mount(SubTaskForm, {
      props: {
        taskId: 'task-001'
      },
      global: {
        stubs: {
          ElCard: true,
          ElDescriptions: true,
          ElDescriptionsItem: true,
          ElForm: true,
          ElFormItem: true,
          ElRow: true,
          ElCol: true,
          ElButton: true,
          ElIcon: true,
          FieldRenderer: true
        }
      }
    })

    await flushPromises()

    // Mock form validation
    wrapper.vm.formRef = {
      validate: vi.fn().mockResolvedValue(true)
    } as any

    // Update form data
    wrapper.vm.subFormData.willAttend = 'yes'
    wrapper.vm.subFormData.dietaryPreference = 'none'

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(confirmSpy).toHaveBeenCalled()
    expect(taskApi.completeTask).toHaveBeenCalledWith('task-001', {
      taskId: 'task-001',
      action: 'complete',
      formData: expect.objectContaining({
        willAttend: 'yes',
        dietaryPreference: 'none'
      }),
      variables: {
        rowVersion: 1
      }
    })
    expect(successSpy).toHaveBeenCalledWith('Task submitted successfully')
  })

  it('should handle optimistic lock exception', async () => {
    vi.mocked(taskApi.getSubTaskFormData).mockResolvedValue({
      data: mockFormData
    } as any)
    
    const lockError = new Error('Optimistic lock exception')
    ;(lockError as any).code = 'OPTIMISTIC_LOCK_EXCEPTION'
    vi.mocked(taskApi.completeTask).mockRejectedValue(lockError)
    
    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    const errorSpy = vi.spyOn(ElMessage, 'error')

    const wrapper = mount(SubTaskForm, {
      props: {
        taskId: 'task-001'
      },
      global: {
        stubs: {
          ElCard: true,
          ElDescriptions: true,
          ElDescriptionsItem: true,
          ElForm: true,
          ElFormItem: true,
          ElRow: true,
          ElCol: true,
          ElButton: true,
          ElIcon: true,
          FieldRenderer: true
        }
      }
    })

    await flushPromises()

    // Mock form validation
    wrapper.vm.formRef = {
      validate: vi.fn().mockResolvedValue(true)
    } as any

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(errorSpy).toHaveBeenCalledWith('Data has been modified, please refresh and try again')
    // Should reload form data
    expect(taskApi.getSubTaskFormData).toHaveBeenCalledTimes(2)
  })

  it('should emit submit-success event on successful submission', async () => {
    vi.mocked(taskApi.getSubTaskFormData).mockResolvedValue({
      data: mockFormData
    } as any)
    vi.mocked(taskApi.completeTask).mockResolvedValue({ data: {} } as any)
    
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    vi.spyOn(ElMessage, 'success')

    const wrapper = mount(SubTaskForm, {
      props: {
        taskId: 'task-001'
      },
      global: {
        stubs: {
          ElCard: true,
          ElDescriptions: true,
          ElDescriptionsItem: true,
          ElForm: true,
          ElFormItem: true,
          ElRow: true,
          ElCol: true,
          ElButton: true,
          ElIcon: true,
          FieldRenderer: true
        }
      }
    })

    await flushPromises()

    // Mock form validation
    wrapper.vm.formRef = {
      validate: vi.fn().mockResolvedValue(true)
    } as any

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(wrapper.emitted('submit-success')).toBeTruthy()
  })

  it('should handle cancel action', async () => {
    vi.mocked(taskApi.getSubTaskFormData).mockResolvedValue({
      data: mockFormData
    } as any)
    
    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)

    const wrapper = mount(SubTaskForm, {
      props: {
        taskId: 'task-001'
      },
      global: {
        stubs: {
          ElCard: true,
          ElDescriptions: true,
          ElDescriptionsItem: true,
          ElForm: true,
          ElFormItem: true,
          ElRow: true,
          ElCol: true,
          ElButton: true,
          ElIcon: true,
          FieldRenderer: true
        }
      }
    })

    await flushPromises()

    await wrapper.vm.handleCancel()
    await flushPromises()

    expect(confirmSpy).toHaveBeenCalled()
    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(mockPush).toHaveBeenCalledWith('/tasks')
  })

  it('should generate form rules correctly', async () => {
    vi.mocked(taskApi.getSubTaskFormData).mockResolvedValue({
      data: mockFormData
    } as any)

    const wrapper = mount(SubTaskForm, {
      props: {
        taskId: 'task-001'
      },
      global: {
        stubs: {
          ElCard: true,
          ElDescriptions: true,
          ElDescriptionsItem: true,
          ElForm: true,
          ElFormItem: true,
          ElRow: true,
          ElCol: true,
          ElButton: true,
          ElIcon: true,
          FieldRenderer: true
        }
      }
    })

    await flushPromises()

    const rules = wrapper.vm.formRules
    
    // willAttend is required
    expect(rules.willAttend).toBeDefined()
    expect(rules.willAttend[0].required).toBe(true)
    
    // dietaryPreference is not required
    expect(rules.dietaryPreference).toBeUndefined()
    
    // remarks is not required
    expect(rules.remarks).toBeUndefined()
  })
})
