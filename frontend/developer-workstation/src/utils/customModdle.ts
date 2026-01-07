/**
 * Custom Moddle Extension for BPMN
 * 定义自定义命名空间用于存储扩展属性
 */

export const customModdleDescriptor = {
  name: 'Custom',
  prefix: 'custom',
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
          type: 'Property',
          isMany: true,
          xml: {
            serialize: 'property'
          }
        }
      ]
    },
    {
      name: 'Property',
      superClass: ['Element'],
      properties: [
        {
          name: 'name',
          type: 'String',
          isAttr: true
        },
        {
          name: 'value',
          type: 'String',
          isAttr: true
        }
      ]
    }
  ]
}

export default customModdleDescriptor
