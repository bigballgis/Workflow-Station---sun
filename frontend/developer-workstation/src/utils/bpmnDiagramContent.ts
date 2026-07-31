/**
 * BPMN「空图」判定 —— 自动保存空图护栏的唯一前端实现点。
 *
 * 背景：2026-07-31 dev 上 FU 50030 的 Start→serviceTask→End 被 2s 自动保存整体覆盖成
 * 空 `<bpmn:process/>`（触发源是 issue #1509 的画布快捷键外泄）。后端同一判定在
 * `backend/developer-workstation/src/main/java/com/developer/component/impl/ProcessBpmnValidator.java`
 * 的 `isEmptyDiagram`，跨语言无法共享实现，两处规则必须同步修改。
 */

/** BPMN 流程节点（不含 sequenceFlow：连线本身不构成「图里有东西」）。 */
const FLOW_NODE_LOCAL_NAMES = new Set([
  'startEvent',
  'endEvent',
  'intermediateCatchEvent',
  'intermediateThrowEvent',
  'boundaryEvent',
  'task',
  'userTask',
  'serviceTask',
  'scriptTask',
  'businessRuleTask',
  'manualTask',
  'receiveTask',
  'sendTask',
  'callActivity',
  'subProcess',
  'transaction',
  'adHocSubProcess',
  'exclusiveGateway',
  'inclusiveGateway',
  'parallelGateway',
  'eventBasedGateway',
  'complexGateway'
])

export interface BpmnDiagramContent {
  /** 语义流程节点数量（不含连线）。 */
  flowNodeCount: number
  /** BPMNDI 图形数量（bpmndi:BPMNShape）。 */
  shapeCount: number
  /** XML 解析失败 —— 此时无法判定内容，护栏必须放行（见 isEmptyBpmnDiagram）。 */
  unparsable: boolean
}

/** 统计 BPMN XML 里的流程节点与 DI 图形。空/空白字符串视为「解析成功且什么都没有」。 */
export function inspectBpmnDiagram(xml: string | null | undefined): BpmnDiagramContent {
  if (!xml || !xml.trim()) {
    return { flowNodeCount: 0, shapeCount: 0, unparsable: false }
  }

  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length > 0) {
    console.warn('[bpmnDiagramContent] BPMN XML failed to parse; empty-diagram guard will not apply')
    return { flowNodeCount: 0, shapeCount: 0, unparsable: true }
  }

  let flowNodeCount = 0
  let shapeCount = 0
  const all = doc.getElementsByTagName('*')
  for (let i = 0; i < all.length; i++) {
    // localName 忽略命名空间前缀：bpmn:userTask / userTask / semantic:userTask 一视同仁。
    const localName = all[i].localName
    if (!localName) continue
    if (FLOW_NODE_LOCAL_NAMES.has(localName)) {
      flowNodeCount++
    } else if (localName === 'BPMNShape') {
      shapeCount++
    }
  }

  return { flowNodeCount, shapeCount, unparsable: false }
}

/**
 * 「这张图什么都没有」：既无流程节点也无 BPMNShape。
 *
 * 解析失败返回 false —— 一段读不懂的 XML 不等于空图，护栏宁可放行也不能挡住正常保存
 * （真正的兜底在后端 ProcessDesignComponentImpl#save）。
 */
export function isEmptyBpmnDiagram(xml: string | null | undefined): boolean {
  const content = inspectBpmnDiagram(xml)
  return !content.unparsable && content.flowNodeCount === 0 && content.shapeCount === 0
}
