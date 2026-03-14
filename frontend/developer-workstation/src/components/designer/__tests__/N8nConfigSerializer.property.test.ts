import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import {
  serializeN8nConfig,
  deserializeN8nConfig,
  validateN8nConfig,
  createDefaultN8nConfig,
} from '@/utils/n8nConfigSerializer'
import type { N8nTaskConfig, VariableMapping } from '@/api/n8n'

/**
 * Feature: n8n-workflow-integration, Property 2: N8N 任务配置序列化往返一致性
 *
 * For any valid N8N task config object (containing configId, workflowId, webhookUrl,
 * timeoutSeconds, retryCount, inputMapping, outputMapping), serializing to BPMN
 * custom:Properties extension attributes (with n8n: prefix) and then deserializing
 * should produce an object equivalent to the original config.
 *
 * Validates: Requirements 3.6, 8.1, 8.2, 8.3, 8.4
 */
describe('Property 2: N8N Task Config Serialization Roundtrip Consistency', () => {
  // Arbitrary for VariableMapping
  const variableMappingArb: fc.Arbitrary<VariableMapping> = fc.record({
    source: fc.string({ minLength: 1, maxLength: 50 }).filter(s => !s.includes('"') && !s.includes('\\')),
    target: fc.string({ minLength: 1, maxLength: 50 }).filter(s => !s.includes('"') && !s.includes('\\')),
  })

  // Arbitrary for N8nTaskConfig
  const n8nTaskConfigArb: fc.Arbitrary<N8nTaskConfig> = fc.record({
    configId: fc.stringMatching(/^[a-zA-Z0-9-]{1,36}$/),
    workflowId: fc.stringMatching(/^[a-zA-Z0-9-]{1,50}$/),
    webhookUrl: fc.stringMatching(/^https?:\/\/[a-z0-9./-]{1,100}$/),
    timeoutSeconds: fc.integer({ min: 1, max: 3600 }),
    retryCount: fc.integer({ min: 0, max: 10 }),
    inputMapping: fc.array(variableMappingArb, { minLength: 0, maxLength: 5 }),
    outputMapping: fc.array(variableMappingArb, { minLength: 0, maxLength: 5 }),
  })

  it('serialize → deserialize roundtrip produces equivalent config', () => {
    fc.assert(
      fc.property(n8nTaskConfigArb, (config) => {
        const serialized = serializeN8nConfig(config)
        const deserialized = deserializeN8nConfig(serialized)

        expect(deserialized.configId).toBe(config.configId)
        expect(deserialized.workflowId).toBe(config.workflowId)
        expect(deserialized.webhookUrl).toBe(config.webhookUrl)
        expect(deserialized.timeoutSeconds).toBe(config.timeoutSeconds)
        expect(deserialized.retryCount).toBe(config.retryCount)
        expect(deserialized.inputMapping).toEqual(config.inputMapping)
        expect(deserialized.outputMapping).toEqual(config.outputMapping)
      }),
      { numRuns: 100 }
    )
  })

  it('serialized keys use n8n: prefix', () => {
    fc.assert(
      fc.property(n8nTaskConfigArb, (config) => {
        const serialized = serializeN8nConfig(config)
        const keys = Object.keys(serialized)
        keys.forEach(key => {
          expect(key.startsWith('n8n:')).toBe(true)
        })
      }),
      { numRuns: 100 }
    )
  })

  it('variable mappings are serialized as JSON strings', () => {
    fc.assert(
      fc.property(n8nTaskConfigArb, (config) => {
        const serialized = serializeN8nConfig(config)
        const inputMappingStr = serialized['n8n:inputMapping']
        const outputMappingStr = serialized['n8n:outputMapping']

        // Should be valid JSON strings
        expect(typeof inputMappingStr).toBe('string')
        expect(typeof outputMappingStr).toBe('string')

        const parsedInput = JSON.parse(inputMappingStr)
        const parsedOutput = JSON.parse(outputMappingStr)

        expect(Array.isArray(parsedInput)).toBe(true)
        expect(Array.isArray(parsedOutput)).toBe(true)
      }),
      { numRuns: 100 }
    )
  })

  it('deserialization handles missing properties gracefully with defaults', () => {
    const deserialized = deserializeN8nConfig({})
    const defaults = createDefaultN8nConfig()

    expect(deserialized.configId).toBe(defaults.configId)
    expect(deserialized.workflowId).toBe(defaults.workflowId)
    expect(deserialized.webhookUrl).toBe(defaults.webhookUrl)
    expect(deserialized.timeoutSeconds).toBe(defaults.timeoutSeconds)
    expect(deserialized.retryCount).toBe(defaults.retryCount)
    expect(deserialized.inputMapping).toEqual(defaults.inputMapping)
    expect(deserialized.outputMapping).toEqual(defaults.outputMapping)
  })
})

/**
 * Feature: n8n-workflow-integration, Property 4: N8N 任务必填字段验证
 *
 * For any N8N task config missing configId or webhookUrl, validation should fail
 * and block save. For any config with valid configId and webhookUrl, validation
 * should pass.
 *
 * Validates: Requirements 3.7
 */
describe('Property 4: N8N Task Required Field Validation', () => {
  const nonEmptyStringArb = fc.string({ minLength: 1, maxLength: 100 }).filter(s => s.trim().length > 0)

  it('configs with both configId and webhookUrl pass validation', () => {
    fc.assert(
      fc.property(nonEmptyStringArb, nonEmptyStringArb, (configId, webhookUrl) => {
        const config: N8nTaskConfig = {
          configId,
          workflowId: '',
          webhookUrl,
          timeoutSeconds: 300,
          retryCount: 3,
          inputMapping: [],
          outputMapping: [],
        }
        const errors = validateN8nConfig(config)
        expect(errors.configId).toBe('')
        expect(errors.webhookUrl).toBe('')
      }),
      { numRuns: 100 }
    )
  })

  it('configs missing configId fail validation', () => {
    fc.assert(
      fc.property(nonEmptyStringArb, (webhookUrl) => {
        const config: N8nTaskConfig = {
          configId: '',
          workflowId: '',
          webhookUrl,
          timeoutSeconds: 300,
          retryCount: 3,
          inputMapping: [],
          outputMapping: [],
        }
        const errors = validateN8nConfig(config)
        expect(errors.configId).not.toBe('')
        expect(errors.webhookUrl).toBe('')
      }),
      { numRuns: 100 }
    )
  })

  it('configs missing webhookUrl fail validation', () => {
    fc.assert(
      fc.property(nonEmptyStringArb, (configId) => {
        const config: N8nTaskConfig = {
          configId,
          workflowId: '',
          webhookUrl: '',
          timeoutSeconds: 300,
          retryCount: 3,
          inputMapping: [],
          outputMapping: [],
        }
        const errors = validateN8nConfig(config)
        expect(errors.configId).toBe('')
        expect(errors.webhookUrl).not.toBe('')
      }),
      { numRuns: 100 }
    )
  })

  it('configs missing both required fields fail validation on both', () => {
    const config: N8nTaskConfig = {
      configId: '',
      workflowId: '',
      webhookUrl: '',
      timeoutSeconds: 300,
      retryCount: 3,
      inputMapping: [],
      outputMapping: [],
    }
    const errors = validateN8nConfig(config)
    expect(errors.configId).not.toBe('')
    expect(errors.webhookUrl).not.toBe('')
  })

  it('validation result is deterministic for random configs', () => {
    fc.assert(
      fc.property(
        fc.record({
          configId: fc.oneof(fc.constant(''), nonEmptyStringArb),
          webhookUrl: fc.oneof(fc.constant(''), nonEmptyStringArb),
        }),
        ({ configId, webhookUrl }) => {
          const config: N8nTaskConfig = {
            configId,
            workflowId: '',
            webhookUrl,
            timeoutSeconds: 300,
            retryCount: 3,
            inputMapping: [],
            outputMapping: [],
          }
          const errors = validateN8nConfig(config)

          // configId error iff configId is empty
          if (configId) {
            expect(errors.configId).toBe('')
          } else {
            expect(errors.configId).not.toBe('')
          }

          // webhookUrl error iff webhookUrl is empty
          if (webhookUrl) {
            expect(errors.webhookUrl).toBe('')
          } else {
            expect(errors.webhookUrl).not.toBe('')
          }
        }
      ),
      { numRuns: 100 }
    )
  })
})
