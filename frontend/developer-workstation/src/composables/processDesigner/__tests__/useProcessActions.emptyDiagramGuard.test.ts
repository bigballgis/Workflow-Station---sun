import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

vi.mock('@/api/functionUnit', () => ({
  functionUnitApi: {
    validateProcess: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
  ElMessageBox: {
    confirm: vi.fn(),
  },
}))

import { ElMessage, ElMessageBox } from 'element-plus'
import { useProcessActions } from '../useProcessActions'

// element-plus 把 MessageBoxData 声明成「对象 & 字符串字面量」，对象字面量无法满足它 —— mock 侧按裸 mock 用。
const confirmMock = ElMessageBox.confirm as unknown as ReturnType<typeof vi.fn>

const NON_EMPTY_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" id="Definitions_1">
  <bpmn:process id="Process_50030" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" />
    <bpmn:serviceTask id="Activity_1" />
    <bpmn:endEvent id="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_50030">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1" />
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

const WIPED_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" id="Definitions_1">
  <bpmn:process id="Process_50030" isExecutable="true" />
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_50030" />
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

function setup(options: { canvasXml: string; savedXml?: string }) {
  const modeler = {
    saveXML: vi.fn().mockResolvedValue({ xml: options.canvasXml }),
    // 无 UserTask 时拓扑校验直接通过
    get: () => ({ getAll: () => [] }),
  }
  const store = {
    process: options.savedXml === undefined ? null : { bpmnXml: options.savedXml },
    saveProcess: vi.fn().mockResolvedValue({}),
  }
  const actions = useProcessActions({
    functionUnitId: 50030,
    getModeler: () => modeler,
    store,
    showImportDialog: ref(false),
    importXml: ref(''),
    t: (key: string) => key,
  })
  return { modeler, store, actions }
}

describe('useProcessActions — empty diagram guard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('refuses to auto-save an empty canvas over a non-empty saved process', async () => {
    const { store, actions } = setup({ canvasXml: WIPED_XML, savedXml: NON_EMPTY_XML })

    await actions.handleSave(true)

    expect(store.saveProcess).not.toHaveBeenCalled()
    expect(actions.autoSaveBlocked.value).toBe(true)
    expect(actions.lastAutoSaveTime.value).toBeNull()
    expect(ElMessage.warning).toHaveBeenCalledWith('process.emptyDiagramAutoSaveBlocked')
  })

  it('warns once per blocked streak instead of on every change', async () => {
    const { actions } = setup({ canvasXml: WIPED_XML, savedXml: NON_EMPTY_XML })

    await actions.handleSave(true)
    await actions.handleSave(true)
    await actions.handleSave(true)

    expect(ElMessage.warning).toHaveBeenCalledTimes(1)
  })

  it('auto-saves normally when the canvas still has nodes', async () => {
    const { store, actions } = setup({ canvasXml: NON_EMPTY_XML, savedXml: NON_EMPTY_XML })

    await actions.handleSave(true)

    expect(store.saveProcess).toHaveBeenCalledWith(
      50030,
      { bpmnXml: NON_EMPTY_XML },
      { allowEmpty: false }
    )
    expect(actions.autoSaveBlocked.value).toBe(false)
    expect(ElMessage.warning).not.toHaveBeenCalled()
  })

  it('auto-saves an empty canvas when the saved process is empty too', async () => {
    const { store, actions } = setup({ canvasXml: WIPED_XML, savedXml: WIPED_XML })

    await actions.handleSave(true)

    expect(store.saveProcess).toHaveBeenCalled()
    expect(actions.autoSaveBlocked.value).toBe(false)
  })

  it('never blocks the very first save when nothing is stored yet', async () => {
    const { store, actions } = setup({ canvasXml: WIPED_XML })

    await actions.handleSave(true)

    expect(store.saveProcess).toHaveBeenCalled()
  })

  it('manual save asks for confirmation and then persists with allowEmpty', async () => {
    confirmMock.mockResolvedValue('confirm')
    const { store, actions } = setup({ canvasXml: WIPED_XML, savedXml: NON_EMPTY_XML })

    await actions.handleSave(false)

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(store.saveProcess).toHaveBeenCalledWith(
      50030,
      { bpmnXml: WIPED_XML },
      { allowEmpty: true }
    )
    expect(ElMessage.success).toHaveBeenCalledWith('process.saveSuccess')
  })

  it('manual save keeps the stored process when the user cancels', async () => {
    confirmMock.mockRejectedValue(new Error('cancel'))
    const { store, actions } = setup({ canvasXml: WIPED_XML, savedXml: NON_EMPTY_XML })

    await actions.handleSave(false)

    expect(store.saveProcess).not.toHaveBeenCalled()
    expect(actions.saving.value).toBe(false)
  })

  it('clears the blocked state once a real save succeeds', async () => {
    const { actions } = setup({ canvasXml: WIPED_XML, savedXml: NON_EMPTY_XML })
    await actions.handleSave(true)
    expect(actions.autoSaveBlocked.value).toBe(true)

    // 用户撤销回非空图后的下一次自动保存
    const restored = setup({ canvasXml: NON_EMPTY_XML, savedXml: NON_EMPTY_XML })
    await restored.actions.handleSave(true)
    expect(restored.actions.autoSaveBlocked.value).toBe(false)
  })

  it('surfaces the backend rejection code with its own message', async () => {
    const { store, actions } = setup({ canvasXml: NON_EMPTY_XML, savedXml: NON_EMPTY_XML })
    store.saveProcess.mockRejectedValue({
      response: { status: 400, data: { error: { code: 'EMPTY_PROCESS_OVERWRITE_BLOCKED' } } },
    })

    await actions.handleSave(false)

    expect(ElMessage.error).toHaveBeenCalledWith('process.emptyDiagramSaveRejected')
  })
})
