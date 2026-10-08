import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { ApplicationDetailCtx } from '../context'
import type { HistoryRecord } from '@/types/historyRecord'
import { getHistoryAction, getHistoryStatus } from '../subTableRowHelpers'

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://test">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="Start_1" />
    <bpmn:userTask id="UserTask_Submit" name="Submit" />
    <bpmn:serviceTask id="Svc_A" name="Call automation" />
    <bpmn:endEvent id="End_1" />
    <bpmn:sequenceFlow id="f1" sourceRef="Start_1" targetRef="UserTask_Submit" />
    <bpmn:sequenceFlow id="f2" sourceRef="UserTask_Submit" targetRef="Svc_A" />
    <bpmn:sequenceFlow id="f3" sourceRef="Svc_A" targetRef="End_1" />
  </bpmn:process>
</bpmn:definitions>`

vi.mock('@/api/process', () => ({
  processApi: {
    getProcessDetail: vi.fn(async () => ({
      data: { id: 'p1', status: 'COMPLETED', functionUnitCatalogId: 'fu-1', variables: {} },
    })),
    // A Function Unit with a process but no forms.
    getFunctionUnitContent: vi.fn(async () => ({ data: { processes: [{ data: xml }], forms: [] } })),
  },
}))

const { createApplicationDetailLoaders } = await import('../useApplicationDetailLoaders')
const { createApplicationDetailDiagramParser } = await import('../useApplicationDetailDiagramParser')

const engineRow = (activityId: string, activityName: string, operationType: string): HistoryRecord => ({
  id: `h_${activityId}`,
  nodeId: activityId,
  nodeName: activityName,
  status: getHistoryStatus(operationType),
  action: getHistoryAction(operationType),
  createdTime: '',
})

describe('application detail loaders — FU without forms', () => {
  it('still parses the diagram after history loads so nodes get status colours', async () => {
    let releaseHistory!: () => void
    const ctx = {
      t: (key: string) => key,
      processId: 'p1',
      snapshotTime: undefined,
      snapshotTaskName: undefined,
      snapshotTaskDefinitionKey: undefined,
      isInitiatorMyRequestView: ref(false),
      loading: ref(false),
      processInfo: ref<Record<string, unknown>>({}),
      currentNodeId: ref(''),
      bpmnXml: ref(''),
      diagramReady: ref(false),
      formData: ref<Record<string, unknown>>({}),
      currentFormName: ref(''),
      subTableBindings: ref([]),
      functionUnitIdRef: ref(''),
      previousForms: ref([]),
      selectedNodeId: ref<string | null>(null),
      nodeFormMap: ref(new Map()),
      processNodes: ref([]),
      processFlows: ref([]),
      completedNodeIds: ref<string[]>([]),
      historyRecords: ref<HistoryRecord[]>([]),
      snapshotActivityId: ref(''),
      hasIncompleteMiRows: () => false,
      hasCompletedMiRows: () => false,
      diagramParseScheduled: false,
      pendingApplicationDetailSecondary: null,
      applicationDetailSecondaryScheduled: false,
      parseBpmnXmlAndGetFormId: () => ({ formId: null, formName: null, scene: 'TASK' }),
      refreshActiveMiSubProcessScopeFromBpmn: () => {},
      scheduleApplicationDetailSecondary: vi.fn(),
      // History arrives after FU content — the parse must wait for it.
      loadProcessHistory: () =>
        new Promise<void>(resolve => {
          releaseHistory = () => {
            ctx.historyRecords.value = [
              engineRow('Start_1', '', 'SUBMIT'),
              engineRow('UserTask_Submit', 'Submit', 'APPROVE'),
              engineRow('Svc_A', 'Call automation', 'AUTO'),
            ]
            resolve()
          }
        }),
    } as unknown as ApplicationDetailCtx
    Object.assign(ctx, createApplicationDetailDiagramParser(ctx))
    Object.assign(ctx, createApplicationDetailLoaders(ctx))

    await ctx.loadProcessDetail()
    // Diagram waits for history instead of rendering uncoloured.
    expect(ctx.diagramReady.value).toBe(false)

    releaseHistory()
    await vi.waitFor(() => expect(ctx.diagramReady.value).toBe(true))

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('UserTask_Submit')?.status).toBe('completed')
    expect(byId.get('Svc_A')?.status).toBe('completed')
    expect(ctx.scheduleApplicationDetailSecondary).not.toHaveBeenCalled()
  })
})
