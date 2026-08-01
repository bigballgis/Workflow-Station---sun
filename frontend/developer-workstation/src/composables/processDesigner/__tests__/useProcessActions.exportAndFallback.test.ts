import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
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

const confirmMock = ElMessageBox.confirm as unknown as ReturnType<typeof vi.fn>

/** 库里的版本：带扩展属性。 */
const PERSISTED_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:custom="http://workflow.platform/schema/custom" id="Definitions_1">
  <bpmn:process id="Process_50030" isExecutable="true">
    <bpmn:userTask id="Task_1">
      <bpmn:extensionElements>
        <custom:properties><custom:property name="assigneeType" value="ROLE" /></custom:properties>
      </bpmn:extensionElements>
    </bpmn:userTask>
  </bpmn:process>
</bpmn:definitions>`

/** 画布重新序列化后的版本（这里模拟扩展被吞掉的情形）。 */
const CANVAS_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1">
  <bpmn:process id="Process_50030" isExecutable="true">
    <bpmn:userTask id="Task_1" />
  </bpmn:process>
</bpmn:definitions>`

/** import 失败后的兜底占位图：有节点有 shape，空图护栏拦不住。 */
const FALLBACK_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" id="Definitions_1">
  <bpmn:process id="Process_50030" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" />
    <bpmn:endEvent id="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_50030">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1" />
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

/** 捕获 downloadFile 落到 Blob 里的内容（jsdom 的 Blob 没有 text()，改用桩记录入参）。 */
function captureDownloads() {
  const contents: string[] = []
  const original = {
    Blob: globalThis.Blob,
    createObjectURL: URL.createObjectURL,
    revokeObjectURL: URL.revokeObjectURL,
    createElement: document.createElement,
  }

  class RecordingBlob {
    constructor(parts: string[]) {
      contents.push(parts.join(''))
    }
  }
  globalThis.Blob = RecordingBlob as unknown as typeof Blob
  URL.createObjectURL = vi.fn(() => 'blob:mock') as unknown as typeof URL.createObjectURL
  URL.revokeObjectURL = vi.fn() as unknown as typeof URL.revokeObjectURL
  document.createElement = ((tag: string) => {
    const el = original.createElement.call(document, tag)
    if (tag === 'a') {
      el.click = vi.fn()
    }
    return el
  }) as typeof document.createElement

  return {
    contents,
    restore() {
      globalThis.Blob = original.Blob
      URL.createObjectURL = original.createObjectURL
      URL.revokeObjectURL = original.revokeObjectURL
      document.createElement = original.createElement
    },
  }
}

function setup(options: {
  canvasXml?: string
  savedXml?: string | null
  isFallback?: boolean
}) {
  const modeler = {
    saveXML: vi.fn().mockResolvedValue({ xml: options.canvasXml ?? CANVAS_XML }),
    get: () => ({ getAll: () => [] }),
  }
  const store = {
    process: options.savedXml === undefined || options.savedXml === null
      ? null
      : { bpmnXml: options.savedXml },
    fetchProcess: vi.fn().mockResolvedValue({}),
    saveProcess: vi.fn().mockResolvedValue({}),
  }
  const diagramIsFallback = ref(!!options.isFallback)
  const actions = useProcessActions({
    functionUnitId: 50030,
    getModeler: () => modeler,
    store,
    showImportDialog: ref(false),
    importXml: ref(''),
    diagramIsFallback,
    t: (key: string) => key,
  })
  return { modeler, store, actions, diagramIsFallback }
}

describe('useProcessActions — 导出走库里的 XML', () => {
  let downloads: ReturnType<typeof captureDownloads>

  beforeEach(() => {
    vi.clearAllMocks()
    downloads = captureDownloads()
  })

  afterEach(() => {
    downloads.restore()
  })

  it('导出持久化版本而非画布重新序列化的结果', async () => {
    const { actions, store, modeler } = setup({ savedXml: PERSISTED_XML })

    await actions.handleExportXML()

    expect(store.fetchProcess).toHaveBeenCalledWith(50030)
    expect(modeler.saveXML).not.toHaveBeenCalled()
    expect(downloads.contents[0]).toBe(PERSISTED_XML)
    expect(ElMessage.success).toHaveBeenCalledWith('process.xmlExportSavedVersion')
  })

  it('导出前重新拉取，用的是后端最新版本', async () => {
    const { actions, store } = setup({ savedXml: '<stale/>' })
    // 模拟 Pinia store 被 fetchProcess 刷新成后端最新版本
    store.fetchProcess = vi.fn().mockImplementation(async () => {
      store.process = { bpmnXml: PERSISTED_XML }
    })

    await actions.handleExportXML()

    expect(downloads.contents[0]).toBe(PERSISTED_XML)
  })

  it('从未落库的 FU 退回画布序列化', async () => {
    const { actions, modeler } = setup({ savedXml: null })

    await actions.handleExportXML()

    expect(modeler.saveXML).toHaveBeenCalled()
    expect(downloads.contents[0]).toBe(CANVAS_XML)
    expect(ElMessage.success).toHaveBeenCalledWith('process.xmlExportSuccess')
  })
})

describe('useProcessActions — 兜底占位图护栏', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('拒绝把占位图自动保存覆盖已存流程', async () => {
    const { actions, store } = setup({
      canvasXml: FALLBACK_XML,
      savedXml: PERSISTED_XML,
      isFallback: true,
    })

    await actions.handleSave(true)

    expect(store.saveProcess).not.toHaveBeenCalled()
    expect(actions.autoSaveBlocked.value).toBe(true)
    expect(ElMessage.warning).toHaveBeenCalledWith('process.fallbackDiagramAutoSaveBlocked')
  })

  it('每轮阻断只提示一次', async () => {
    const { actions } = setup({
      canvasXml: FALLBACK_XML,
      savedXml: PERSISTED_XML,
      isFallback: true,
    })

    await actions.handleSave(true)
    await actions.handleSave(true)
    await actions.handleSave(true)

    expect(ElMessage.warning).toHaveBeenCalledTimes(1)
  })

  it('手动保存需确认，确认后落库并解除标记', async () => {
    confirmMock.mockResolvedValue('confirm')
    const { actions, store, diagramIsFallback } = setup({
      canvasXml: FALLBACK_XML,
      savedXml: PERSISTED_XML,
      isFallback: true,
    })

    await actions.handleSave(false)

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(store.saveProcess).toHaveBeenCalledWith(
      50030,
      { bpmnXml: FALLBACK_XML },
      { allowEmpty: false }
    )
    expect(diagramIsFallback.value).toBe(false)
  })

  it('用户取消时保持已存版本', async () => {
    confirmMock.mockRejectedValue(new Error('cancel'))
    const { actions, store } = setup({
      canvasXml: FALLBACK_XML,
      savedXml: PERSISTED_XML,
      isFallback: true,
    })

    await actions.handleSave(false)

    expect(store.saveProcess).not.toHaveBeenCalled()
    expect(actions.saving.value).toBe(false)
  })

  it('未触发兜底时自动保存照常', async () => {
    const { actions, store } = setup({ canvasXml: CANVAS_XML, savedXml: PERSISTED_XML })

    await actions.handleSave(true)

    expect(store.saveProcess).toHaveBeenCalled()
    expect(ElMessage.warning).not.toHaveBeenCalled()
  })
})
