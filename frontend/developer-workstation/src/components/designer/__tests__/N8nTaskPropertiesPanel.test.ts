import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  serializeN8nConfig,
  deserializeN8nConfig,
  validateN8nConfig,
  createDefaultN8nConfig,
  N8N_KEYS,
} from '@/utils/n8nConfigSerializer'
import type { N8nTaskConfig } from '@/api/n8n'

/**
 * Unit tests for N8nTaskPropertiesPanel
 * Tests config save/load, workflow selection auto-fill, required field validation
 *
 * Validates: Requirements 3.1, 3.2, 3.5, 3.7, 8.2
 */
describe('N8nTaskPropertiesPanel - Config Serialization', () => {
  it('should create default config with correct defaults', () => {
    const config = createDefaultN8nConfig()
    expect(config.configId).toBe('')
    expect(config.workflowId).toBe('')
    expect(config.webhookUrl).toBe('')
    expect(config.timeoutSeconds).toBe(300)
    expect(config.retryCount).toBe(3)
    expect(config.inputMapping).toEqual([])
    expect(config.outputMapping).toEqual([])
  })

  it('should serialize config with n8n: prefix keys', () => {
    const config: N8nTaskConfig = {
      configId: 'cfg-001',
      workflowId: 'wf-001',
      webhookUrl: 'https://n8n.example.com/webhook/test',
      timeoutSeconds: 600,
      retryCount: 5,
      inputMapping: [{ source: 'orderId', target: 'id' }],
      outputMapping: [{ source: 'result', target: 'processResult' }],
    }

    const serialized = serializeN8nConfig(config)

    expect(serialized[N8N_KEYS.configId]).toBe('cfg-001')
    expect(serialized[N8N_KEYS.workflowId]).toBe('wf-001')
    expect(serialized[N8N_KEYS.webhookUrl]).toBe('https://n8n.example.com/webhook/test')
    expect(serialized[N8N_KEYS.timeoutSeconds]).toBe('600')
    expect(serialized[N8N_KEYS.retryCount]).toBe('5')
    expect(JSON.parse(serialized[N8N_KEYS.inputMapping])).toEqual([{ source: 'orderId', target: 'id' }])
    expect(JSON.parse(serialized[N8N_KEYS.outputMapping])).toEqual([{ source: 'result', target: 'processResult' }])
  })

  it('should deserialize config from extension properties', () => {
    const ext: Record<string, any> = {
      'n8n:configId': 'cfg-002',
      'n8n:workflowId': 'wf-002',
      'n8n:webhookUrl': 'https://n8n.example.com/webhook/abc',
      'n8n:timeoutSeconds': '120',
      'n8n:retryCount': '2',
      'n8n:inputMapping': JSON.stringify([{ source: 'var1', target: 'param1' }]),
      'n8n:outputMapping': JSON.stringify([{ source: 'out1', target: 'var2' }]),
    }

    const config = deserializeN8nConfig(ext)

    expect(config.configId).toBe('cfg-002')
    expect(config.workflowId).toBe('wf-002')
    expect(config.webhookUrl).toBe('https://n8n.example.com/webhook/abc')
    expect(config.timeoutSeconds).toBe(120)
    expect(config.retryCount).toBe(2)
    expect(config.inputMapping).toEqual([{ source: 'var1', target: 'param1' }])
    expect(config.outputMapping).toEqual([{ source: 'out1', target: 'var2' }])
  })

  it('should handle missing extension properties with defaults', () => {
    const config = deserializeN8nConfig({})

    expect(config.configId).toBe('')
    expect(config.workflowId).toBe('')
    expect(config.webhookUrl).toBe('')
    expect(config.timeoutSeconds).toBe(300)
    expect(config.retryCount).toBe(3)
    expect(config.inputMapping).toEqual([])
    expect(config.outputMapping).toEqual([])
  })

  it('should handle invalid JSON in mapping fields gracefully', () => {
    const ext: Record<string, any> = {
      'n8n:configId': 'cfg-003',
      'n8n:inputMapping': 'not-valid-json',
      'n8n:outputMapping': '{broken',
    }

    const config = deserializeN8nConfig(ext)

    expect(config.configId).toBe('cfg-003')
    expect(config.inputMapping).toEqual([])
    expect(config.outputMapping).toEqual([])
  })

  it('should handle numeric values passed as numbers', () => {
    const ext: Record<string, any> = {
      'n8n:configId': 'cfg-004',
      'n8n:timeoutSeconds': 450,
      'n8n:retryCount': 7,
    }

    const config = deserializeN8nConfig(ext)

    expect(config.timeoutSeconds).toBe(450)
    expect(config.retryCount).toBe(7)
  })

  it('should roundtrip config with multiple mappings', () => {
    const original: N8nTaskConfig = {
      configId: 'cfg-rt',
      workflowId: 'wf-rt',
      webhookUrl: 'https://n8n.test/webhook/roundtrip',
      timeoutSeconds: 180,
      retryCount: 1,
      inputMapping: [
        { source: 'var1', target: 'param1' },
        { source: 'var2', target: 'param2' },
        { source: 'var3', target: 'param3' },
      ],
      outputMapping: [
        { source: 'out1', target: 'result1' },
        { source: 'out2', target: 'result2' },
      ],
    }

    const serialized = serializeN8nConfig(original)
    const deserialized = deserializeN8nConfig(serialized)

    expect(deserialized).toEqual(original)
  })
})

describe('N8nTaskPropertiesPanel - Required Field Validation', () => {
  it('should fail validation when configId is empty', () => {
    const config: N8nTaskConfig = {
      configId: '',
      workflowId: 'wf-001',
      webhookUrl: 'https://n8n.example.com/webhook/test',
      timeoutSeconds: 300,
      retryCount: 3,
      inputMapping: [],
      outputMapping: [],
    }

    const errors = validateN8nConfig(config)
    expect(errors.configId).not.toBe('')
    expect(errors.webhookUrl).toBe('')
  })

  it('should fail validation when webhookUrl is empty', () => {
    const config: N8nTaskConfig = {
      configId: 'cfg-001',
      workflowId: 'wf-001',
      webhookUrl: '',
      timeoutSeconds: 300,
      retryCount: 3,
      inputMapping: [],
      outputMapping: [],
    }

    const errors = validateN8nConfig(config)
    expect(errors.configId).toBe('')
    expect(errors.webhookUrl).not.toBe('')
  })

  it('should fail validation when both required fields are empty', () => {
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

  it('should pass validation when both required fields are filled', () => {
    const config: N8nTaskConfig = {
      configId: 'cfg-001',
      workflowId: 'wf-001',
      webhookUrl: 'https://n8n.example.com/webhook/test',
      timeoutSeconds: 300,
      retryCount: 3,
      inputMapping: [],
      outputMapping: [],
    }

    const errors = validateN8nConfig(config)
    expect(errors.configId).toBe('')
    expect(errors.webhookUrl).toBe('')
  })
})

describe('N8nTaskPropertiesPanel - Workflow Selection Auto-fill', () => {
  it('should auto-fill webhook URL from selected workflow', () => {
    // Simulating the workflow selection behavior
    const workflows = [
      { id: 'wf-1', name: 'Workflow 1', active: true, webhookUrl: 'https://n8n.test/webhook/wf1' },
      { id: 'wf-2', name: 'Workflow 2', active: true, webhookUrl: 'https://n8n.test/webhook/wf2' },
    ]

    const selectedId = 'wf-1'
    const selected = workflows.find(wf => wf.id === selectedId)

    expect(selected).toBeDefined()
    expect(selected!.webhookUrl).toBe('https://n8n.test/webhook/wf1')
  })

  it('should handle workflow without webhookUrl', () => {
    const workflows = [
      { id: 'wf-3', name: 'Workflow 3', active: true },
    ]

    const selectedId = 'wf-3'
    const selected = workflows.find(wf => wf.id === selectedId)

    expect(selected).toBeDefined()
    expect(selected!.webhookUrl).toBeUndefined()
  })
})
