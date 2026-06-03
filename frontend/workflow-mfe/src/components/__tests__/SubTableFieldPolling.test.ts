import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SubTableField from '../SubTableField.vue'
import * as taskApi from '@/api/task'
import { useSubTableWebSocket } from '@/composables/useSubTableWebSocket'

// Mock the API modules
vi.mock('@/api/task', () => ({
  assignSubTableRow: vi.fn(),
  getSubTableData: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  userApi: {
    searchUsers: vi.fn(),
  },
}))

// Mock i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Mock DOMPurify
vi.mock('dompurify', () => ({
  default: {
    sanitize: (html: string) => html,
  },
}))

// Mock WebSocket composable
vi.mock('@/composables/useSubTableWebSocket', () => ({
  useSubTableWebSocket: vi.fn(() => ({
    connected: { value: false },
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
    disconnect: vi.fn(),
  })),
}))

describe('SubTableField - Real-time Polling (Task 16.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  const createWrapper = (props: any = {}) => {
    return mount(SubTableField, {
      props: {
        title: 'Test Sub-Table',
        columns: [
          { field: 'name', label: 'Name', type: 'text' },
          { field: 'status', label: 'Status', type: 'text' },
        ],
        modelValue: [
          { id: 1, name: 'Row 1', status: 'pending' },
          { id: 2, name: 'Row 2', status: 'pending' },
        ],
        taskId: 'task-123',
        assigneeField: 'assignee',
        ...props,
      },
      global: {
        stubs: {
          ElTable: true,
          ElTableColumn: true,
          ElButton: true,
          ElDialog: true,
          ElSelect: true,
          ElOption: true,
          ElForm: true,
          ElFormItem: true,
          ElIcon: true,
          ElEmpty: true,
          SubTableAddDialog: true,
        },
      },
    })
  }

  it('should start polling when enablePolling is true', async () => {
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: 'task-123',
        subTableName: 'test_table',
        rows: [
          { id: 1, name: 'Row 1', status: 'completed' },
          { id: 2, name: 'Row 2', status: 'in_progress' },
        ],
      },
    } as any)

    const wrapper = createWrapper({
      enablePolling: true,
      pollingInterval: 5000,
    })

    await wrapper.vm.$nextTick()

    // Advance timer by 5 seconds
    await vi.advanceTimersByTimeAsync(5000)

    // Verify API was called
    expect(mockGetSubTableData).toHaveBeenCalledWith('task-123')
    expect(mockGetSubTableData).toHaveBeenCalledTimes(1)

    // Advance timer by another 5 seconds
    await vi.advanceTimersByTimeAsync(5000)

    // Verify API was called again
    expect(mockGetSubTableData).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('should stop polling when component is unmounted', async () => {
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: 'task-123',
        subTableName: 'test_table',
        rows: [],
      },
    } as any)

    const wrapper = createWrapper({
      enablePolling: true,
      pollingInterval: 5000,
    })

    await wrapper.vm.$nextTick()

    // Advance timer
    await vi.advanceTimersByTimeAsync(5000)

    expect(mockGetSubTableData).toHaveBeenCalledTimes(1)

    // Unmount component
    wrapper.unmount()

    // Advance timer again
    await vi.advanceTimersByTimeAsync(5000)

    // API should not be called after unmount
    expect(mockGetSubTableData).toHaveBeenCalledTimes(1)
  })

  it('should update sub-table data when polling receives new data', async () => {
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: 'task-123',
        subTableName: 'test_table',
        rows: [
          { id: 1, name: 'Row 1', status: 'completed', assignee: 'user-001' },
          { id: 2, name: 'Row 2', status: 'in_progress', assignee: 'user-002' },
        ],
      },
    } as any)

    const wrapper = createWrapper({
      enablePolling: true,
      pollingInterval: 5000,
    })

    await wrapper.vm.$nextTick()

    // Advance timer
    await vi.advanceTimersByTimeAsync(5000)

    // Verify emitted event
    const updateEvents = wrapper.emitted('update:modelValue')
    expect(updateEvents).toBeDefined()
    expect(updateEvents!.length).toBeGreaterThan(0)

    const lastUpdate = updateEvents![updateEvents!.length - 1][0] as any[]
    expect(lastUpdate[0].status).toBe('completed')
    expect(lastUpdate[0].assignee).toBe('user-001')
    expect(lastUpdate[1].status).toBe('in_progress')
    expect(lastUpdate[1].assignee).toBe('user-002')

    wrapper.unmount()
  })

  it('should not start polling when enablePolling is false', async () => {
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: 'task-123',
        subTableName: 'test_table',
        rows: [],
      },
    } as any)

    const wrapper = createWrapper({
      enablePolling: false,
      pollingInterval: 5000,
    })

    await wrapper.vm.$nextTick()

    // Advance timer
    await vi.advanceTimersByTimeAsync(10000)

    // API should not be called
    expect(mockGetSubTableData).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('should not start polling when taskId is missing', async () => {
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: '',
        subTableName: 'test_table',
        rows: [],
      },
    } as any)

    const wrapper = createWrapper({
      taskId: undefined,
      enablePolling: true,
      pollingInterval: 5000,
    })

    await wrapper.vm.$nextTick()

    // Advance timer
    await vi.advanceTimersByTimeAsync(10000)

    // API should not be called
    expect(mockGetSubTableData).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('should handle API errors gracefully without showing error messages', async () => {
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockRejectedValue(new Error('Network error'))

    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    const wrapper = createWrapper({
      enablePolling: true,
      pollingInterval: 5000,
    })

    await wrapper.vm.$nextTick()

    // Advance timer
    await vi.advanceTimersByTimeAsync(5000)

    // Verify error was logged
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      'Failed to refresh sub-table data:',
      expect.any(Error)
    )

    // Component should still be functional
    expect(wrapper.exists()).toBe(true)

    consoleErrorSpy.mockRestore()
    wrapper.unmount()
  })

  it('should restart polling when taskId changes', async () => {
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: 'task-123',
        subTableName: 'test_table',
        rows: [],
      },
    } as any)

    const wrapper = createWrapper({
      enablePolling: true,
      pollingInterval: 5000,
    })

    await wrapper.vm.$nextTick()

    // Advance timer
    await vi.advanceTimersByTimeAsync(5000)

    expect(mockGetSubTableData).toHaveBeenCalledWith('task-123')
    expect(mockGetSubTableData).toHaveBeenCalledTimes(1)

    // Change taskId
    await wrapper.setProps({ taskId: 'task-456' })
    await wrapper.vm.$nextTick()

    // Advance timer
    await vi.advanceTimersByTimeAsync(5000)

    // Should be called with new taskId
    expect(mockGetSubTableData).toHaveBeenCalledWith('task-456')
    expect(mockGetSubTableData).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('should emit dataRefreshed event when data is updated', async () => {
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    const updatedRows = [
      { id: 1, name: 'Row 1', status: 'completed' },
      { id: 2, name: 'Row 2', status: 'completed' },
    ]

    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: 'task-123',
        subTableName: 'test_table',
        rows: updatedRows,
      },
    } as any)

    const wrapper = createWrapper({
      enablePolling: true,
      pollingInterval: 5000,
    })

    await wrapper.vm.$nextTick()

    // Advance timer
    await vi.advanceTimersByTimeAsync(5000)

    // Verify dataRefreshed event was emitted
    const dataRefreshedEvents = wrapper.emitted('dataRefreshed')
    expect(dataRefreshedEvents).toBeDefined()
    expect(dataRefreshedEvents!.length).toBeGreaterThan(0)

    const emittedData = dataRefreshedEvents![0][0] as any[]
    expect(emittedData[0].status).toBe('completed')
    expect(emittedData[1].status).toBe('completed')

    wrapper.unmount()
  })

  it('should preserve existing row data when merging refreshed data', async () => {
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: 'task-123',
        subTableName: 'test_table',
        rows: [
          { id: 1, status: 'completed', assignee: 'user-001' },
          { id: 2, status: 'in_progress', assignee: 'user-002' },
        ],
      },
    } as any)

    const wrapper = createWrapper({
      modelValue: [
        { id: 1, name: 'Row 1', status: 'pending', customField: 'value1' },
        { id: 2, name: 'Row 2', status: 'pending', customField: 'value2' },
      ],
      enablePolling: true,
      pollingInterval: 5000,
    })

    await wrapper.vm.$nextTick()

    // Advance timer
    await vi.advanceTimersByTimeAsync(5000)

    // Verify merged data preserves existing fields
    const updateEvents = wrapper.emitted('update:modelValue')
    expect(updateEvents).toBeDefined()

    const lastUpdate = updateEvents![updateEvents!.length - 1][0] as any[]
    expect(lastUpdate[0].name).toBe('Row 1') // Preserved
    expect(lastUpdate[0].customField).toBe('value1') // Preserved
    expect(lastUpdate[0].status).toBe('completed') // Updated
    expect(lastUpdate[0].assignee).toBe('user-001') // Updated

    wrapper.unmount()
  })
})

describe('SubTableField - WebSocket Real-time Sync (Task 16.2)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const createWrapper = (props: any = {}) => {
    return mount(SubTableField, {
      props: {
        title: 'Test Sub-Table',
        columns: [
          { field: 'name', label: 'Name', type: 'text' },
          { field: 'status', label: 'Status', type: 'text' },
        ],
        modelValue: [
          { id: 1, name: 'Row 1', status: 'pending' },
          { id: 2, name: 'Row 2', status: 'pending' },
        ],
        taskId: 'task-123',
        assigneeField: 'assignee',
        ...props,
      },
      global: {
        stubs: {
          ElTable: true,
          ElTableColumn: true,
          ElButton: true,
          ElDialog: true,
          ElSelect: true,
          ElOption: true,
          ElForm: true,
          ElFormItem: true,
          ElIcon: true,
          ElEmpty: true,
          SubTableAddDialog: true,
        },
      },
    })
  }

  it('should subscribe to WebSocket when enableWebSocket is true', async () => {
    const mockSubscribe = vi.fn()
    const mockUnsubscribe = vi.fn()
    
    vi.mocked(useSubTableWebSocket).mockReturnValue({
      connected: { value: true },
      subscribe: mockSubscribe,
      unsubscribe: mockUnsubscribe,
      disconnect: vi.fn(),
    } as any)

    const wrapper = createWrapper({
      enableWebSocket: true,
    })

    await wrapper.vm.$nextTick()

    // Verify subscription was called with correct taskId
    expect(mockSubscribe).toHaveBeenCalledWith('task-123', expect.any(Function))

    wrapper.unmount()
  })

  it('should unsubscribe from WebSocket when component is unmounted', async () => {
    const mockSubscribe = vi.fn()
    const mockUnsubscribe = vi.fn()
    
    vi.mocked(useSubTableWebSocket).mockReturnValue({
      connected: { value: true },
      subscribe: mockSubscribe,
      unsubscribe: mockUnsubscribe,
      disconnect: vi.fn(),
    } as any)

    const wrapper = createWrapper({
      enableWebSocket: true,
    })

    await wrapper.vm.$nextTick()

    expect(mockSubscribe).toHaveBeenCalled()

    // Unmount component
    wrapper.unmount()

    // Verify unsubscribe was called
    expect(mockUnsubscribe).toHaveBeenCalled()
  })

  it('should refresh data when receiving WebSocket message', async () => {
    let messageCallback: any = null
    const mockSubscribe = vi.fn((taskId, callback) => {
      messageCallback = callback
    })
    
    vi.mocked(useSubTableWebSocket).mockReturnValue({
      connected: { value: true },
      subscribe: mockSubscribe,
      unsubscribe: vi.fn(),
      disconnect: vi.fn(),
    } as any)

    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: 'task-123',
        subTableName: 'test_table',
        rows: [
          { id: 1, name: 'Row 1', status: 'completed', assignee: 'user-001' },
          { id: 2, name: 'Row 2', status: 'in_progress', assignee: 'user-002' },
        ],
      },
    } as any)

    const wrapper = createWrapper({
      enableWebSocket: true,
    })

    await wrapper.vm.$nextTick()

    // Simulate WebSocket message
    expect(messageCallback).toBeDefined()
    messageCallback({
      taskId: 'task-123',
      rowId: 1,
      assigneeId: 'user-001',
      status: 'completed',
      timestamp: new Date().toISOString(),
    })

    // Wait for async refresh
    await new Promise(resolve => setTimeout(resolve, 100))

    // Verify API was called to refresh data
    expect(mockGetSubTableData).toHaveBeenCalledWith('task-123')

    // Verify emitted event
    const updateEvents = wrapper.emitted('update:modelValue')
    expect(updateEvents).toBeDefined()

    wrapper.unmount()
  })

  it('should not subscribe when enableWebSocket is false', async () => {
    const mockSubscribe = vi.fn()
    
    vi.mocked(useSubTableWebSocket).mockReturnValue({
      connected: { value: false },
      subscribe: mockSubscribe,
      unsubscribe: vi.fn(),
      disconnect: vi.fn(),
    } as any)

    const wrapper = createWrapper({
      enableWebSocket: false,
    })

    await wrapper.vm.$nextTick()

    // Verify subscription was not called
    expect(mockSubscribe).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('should not subscribe when taskId is missing', async () => {
    const mockSubscribe = vi.fn()
    
    vi.mocked(useSubTableWebSocket).mockReturnValue({
      connected: { value: false },
      subscribe: mockSubscribe,
      unsubscribe: vi.fn(),
      disconnect: vi.fn(),
    } as any)

    const wrapper = createWrapper({
      taskId: undefined,
      enableWebSocket: true,
    })

    await wrapper.vm.$nextTick()

    // Verify subscription was not called
    expect(mockSubscribe).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('should resubscribe when taskId changes', async () => {
    const mockSubscribe = vi.fn()
    const mockUnsubscribe = vi.fn()
    
    vi.mocked(useSubTableWebSocket).mockReturnValue({
      connected: { value: true },
      subscribe: mockSubscribe,
      unsubscribe: mockUnsubscribe,
      disconnect: vi.fn(),
    } as any)

    const wrapper = createWrapper({
      enableWebSocket: true,
    })

    await wrapper.vm.$nextTick()

    expect(mockSubscribe).toHaveBeenCalledWith('task-123', expect.any(Function))
    expect(mockSubscribe).toHaveBeenCalledTimes(1)

    // Change taskId
    await wrapper.setProps({ taskId: 'task-456' })
    await wrapper.vm.$nextTick()

    // Should unsubscribe from old topic and subscribe to new one
    expect(mockUnsubscribe).toHaveBeenCalled()
    expect(mockSubscribe).toHaveBeenCalledWith('task-456', expect.any(Function))
    expect(mockSubscribe).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('should work alongside polling when both are enabled', async () => {
    const mockSubscribe = vi.fn()
    const mockGetSubTableData = vi.mocked(taskApi.getSubTableData)
    mockGetSubTableData.mockResolvedValue({
      data: {
        taskId: 'task-123',
        subTableName: 'test_table',
        rows: [],
      },
    } as any)
    
    vi.mocked(useSubTableWebSocket).mockReturnValue({
      connected: { value: true },
      subscribe: mockSubscribe,
      unsubscribe: vi.fn(),
      disconnect: vi.fn(),
    } as any)

    vi.useFakeTimers()

    const wrapper = createWrapper({
      enablePolling: true,
      pollingInterval: 5000,
      enableWebSocket: true,
    })

    await wrapper.vm.$nextTick()

    // Both should be active
    expect(mockSubscribe).toHaveBeenCalled()

    // Advance timer for polling
    await vi.advanceTimersByTimeAsync(5000)

    // Polling should still work
    expect(mockGetSubTableData).toHaveBeenCalled()

    vi.useRealTimers()
    wrapper.unmount()
  })
})
