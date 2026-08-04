/**
 * BPMN moddle extensions for Developer Workstation.
 *
 * Platform BPMN uses three namespaces:
 * - custom     → http://workflow.platform/schema/custom  (assignee / task extensions)
 * - custom_1   → http://custom.bpmn.io/schema             (formId / formName values)
 * - flowable   → http://flowable.org/bpmn                  (multi-instance collection)
 */

/** formId / formName / … under custom_1:properties */
export const bpmnIoCustomModdleDescriptor = {
  name: 'BpmnIoCustom',
  prefix: 'custom_1',
  uri: 'http://custom.bpmn.io/schema',
  xml: {
    tagAlias: 'lowerCase'
  },
  types: [
    {
      name: 'Properties',
      superClass: ['Element'],
      meta: {
        allowedIn: ['bpmn:ExtensionElements']
      },
      properties: [
        {
          name: 'values',
          type: 'Values',
          isMany: true
        },
        {
          name: 'property',
          type: 'Property',
          isMany: true
        }
      ]
    },
    {
      name: 'Values',
      superClass: ['Element'],
      properties: [
        { name: 'name', type: 'String', isAttr: true },
        { name: 'value', type: 'String', isAttr: true }
      ]
    },
    {
      name: 'Property',
      superClass: ['Element'],
      properties: [
        { name: 'name', type: 'String', isAttr: true },
        { name: 'value', type: 'String', isAttr: true }
      ]
    }
  ]
}

/**
 * assigneeType, subTableId, … under custom:properties
 *
 * `Values` 与 `Property` 同时声明：历史 BPMN 里两种子元素写法都出现过
 * （`<custom:property>` / `<custom:values>`）。moddle 只保留声明过的类型，
 * 少声明一个就会在 saveXML 时被当作 unparsable content 静默丢弃 ——
 * 表现为 `<custom:properties />` 空壳，assigneeType / formId 全没。
 */
export const workflowPlatformModdleDescriptor = {
  name: 'WorkflowPlatform',
  prefix: 'custom',
  uri: 'http://workflow.platform/schema/custom',
  xml: {
    tagAlias: 'lowerCase'
  },
  types: [
    {
      name: 'Properties',
      superClass: ['Element'],
      meta: {
        allowedIn: ['bpmn:ExtensionElements']
      },
      properties: [
        {
          name: 'property',
          type: 'Property',
          isMany: true
        },
        {
          name: 'values',
          type: 'Values',
          isMany: true
        }
      ]
    },
    {
      name: 'Values',
      superClass: ['Element'],
      properties: [
        { name: 'name', type: 'String', isAttr: true },
        { name: 'value', type: 'String', isAttr: true }
      ]
    },
    {
      name: 'Property',
      superClass: ['Element'],
      properties: [
        { name: 'name', type: 'String', isAttr: true },
        { name: 'value', type: 'String', isAttr: true }
      ]
    }
  ]
}

/**
 * flowable:collection / flowable:elementVariable —— 属性写法（写在
 * multiInstanceLoopCharacteristics 上）和元素写法（写在其 extensionElements 里）都要认。
 *
 * 类型名必须首字母大写：`tagAlias: 'lowerCase'` 让 moddle 把 XML 标签
 * `<flowable:collection>` 解析成类型 `flowable:Collection`。写成小写 `collection`
 * 时查不到类型，整个元素会被当 unparsable content 丢弃（连 xmlns:flowable 一起消失），
 * 后端 BpmnXmlGenerator 生成的元素写法多实例配置因此会在设计器存盘后丢失。
 */
export const flowableModdleDescriptor = {
  name: 'Flowable',
  prefix: 'flowable',
  uri: 'http://flowable.org/bpmn',
  xml: {
    tagAlias: 'lowerCase'
  },
  types: [
    {
      name: 'MultiInstanceLoopCharacteristics',
      extends: ['bpmn:MultiInstanceLoopCharacteristics'],
      properties: [
        { name: 'collection', type: 'String', isAttr: true },
        { name: 'elementVariable', type: 'String', isAttr: true }
      ]
    },
    {
      name: 'Collection',
      superClass: ['Element'],
      meta: {
        allowedIn: ['bpmn:ExtensionElements']
      },
      properties: [{ name: 'body', type: 'String', isBody: true }]
    },
    {
      name: 'ElementVariable',
      superClass: ['Element'],
      meta: {
        allowedIn: ['bpmn:ExtensionElements']
      },
      properties: [{ name: 'body', type: 'String', isBody: true }]
    }
  ]
}

/** @deprecated Use bpmnIoCustomModdleDescriptor + workflowPlatformModdleDescriptor + flowableModdleDescriptor */
export const customModdleDescriptor = bpmnIoCustomModdleDescriptor

export default bpmnIoCustomModdleDescriptor
