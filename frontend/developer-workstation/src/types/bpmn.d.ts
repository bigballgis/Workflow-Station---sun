// BPMN.js 类型定义

export interface BpmnElement {
  id: string
  type: string
  businessObject: BpmnBusinessObject
}

export interface BpmnBusinessObject {
  id: string
  name?: string
  $type: string
  extensionElements?: BpmnExtensionElements
  [key: string]: any
}

export interface BpmnExtensionElements {
  values: BpmnExtensionValue[]
}

export interface BpmnExtensionValue {
  $type: string
  values?: BpmnCustomProperty[]
  [key: string]: any
}

export interface BpmnCustomProperty {
  $type: string
  name: string
  value: string
}

export interface BpmnModeler {
  get(service: string): any
  on(event: string, callback: (e: any) => void): void
  off(event: string, callback?: (e: any) => void): void
  importXML(xml: string): Promise<{ warnings: string[] }>
  saveXML(options?: { format?: boolean }): Promise<{ xml: string }>
  saveSVG(): Promise<{ svg: string }>
  destroy(): void
}

export interface BpmnModeling {
  updateProperties(element: BpmnElement, properties: Record<string, any>): void
}

export interface BpmnModdle {
  create(type: string, attrs?: Record<string, any>): any
}

export interface BpmnSelection {
  get(): BpmnElement[]
}

export interface BpmnCanvas {
  zoom(level: number | 'fit-viewport'): void
  getZoom(): number
}

export interface BpmnCommandStack {
  undo(): void
  redo(): void
  canUndo(): boolean
  canRedo(): boolean
}

// 自定义属性类型
export interface UserTaskProperties {
  assigneeType?: 'user' | 'role' | 'expression'
  assigneeValue?: string
  candidateUsers?: string[]
  candidateGroups?: string[]
  formId?: number
  formName?: string
  timeoutEnabled?: boolean
  timeoutDuration?: string
  timeoutAction?: 'remind' | 'approve' | 'reject'
  multiInstance?: boolean
  sequential?: boolean
  collection?: string
  completionCondition?: string
}

export interface ServiceTaskProperties {
  serviceType?: 'http' | 'script' | 'message'
  httpUrl?: string
  httpMethod?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  httpHeaders?: Record<string, string>
  httpBody?: string
  httpResponseVar?: string
  scriptLanguage?: 'javascript' | 'groovy'
  scriptContent?: string
  retryEnabled?: boolean
  retryCount?: number
  retryInterval?: string
}

export interface GatewayProperties {
  defaultFlow?: string
}

export interface SequenceFlowProperties {
  conditionExpression?: string
  conditionType?: 'juel' | 'script'
}

export interface StartEventProperties {
  formId?: number
  formName?: string
  initiator?: string
}

export interface EndEventProperties {
  endAction?: 'none' | 'notify' | 'service'
  notifyConfig?: Record<string, any>
  serviceConfig?: Record<string, any>
}

export interface TimerEventProperties {
  timerType?: 'date' | 'duration' | 'cycle'
  timerValue?: string
}

export interface MessageEventProperties {
  messageName?: string
  correlationKey?: string
}

export type NodeProperties = 
  | UserTaskProperties 
  | ServiceTaskProperties 
  | GatewayProperties 
  | SequenceFlowProperties
  | StartEventProperties
  | EndEventProperties
  | TimerEventProperties
  | MessageEventProperties
