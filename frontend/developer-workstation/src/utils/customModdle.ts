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

/** assigneeType, subTableId, … under custom:properties */
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
        }
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

/** flowable:collection / flowable:elementVariable inside multiInstance extensionElements */
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
      name: 'collection',
      superClass: ['Element'],
      meta: {
        allowedIn: ['bpmn:ExtensionElements']
      },
      properties: [{ name: 'body', type: 'String', isBody: true }]
    },
    {
      name: 'elementVariable',
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
