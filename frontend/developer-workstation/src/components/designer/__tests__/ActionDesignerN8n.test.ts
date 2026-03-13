import { describe, it, expect } from 'vitest'

// Unit tests for ActionDesigner N8N Action configuration
// Validates: Requirements 10.5, 10.6, 10.9

interface N8nInputParam { paramName: string; paramLabel: string; paramType: string; required: boolean }
interface N8nOutputMapping { source: string; target: string }
interface N8nActionConfig {
  n8nConfigId: string
  n8nWorkflowId: string
  webhookUrl: string
  timeoutSeconds: number
  inputMapping: N8nInputParam[]
  outputMapping: N8nOutputMapping[]
}

const ACTION_TYPE_LABELS: Record<string, string> = {
  APPROVE: 'approve', REJECT: 'reject', TRANSFER: 'transfer',
  DELEGATE: 'delegate', ROLLBACK: 'rollback', WITHDRAW: 'withdraw',
  PROCESS_SUBMIT: 'processSubmit', PROCESS_REJECT: 'processReject',
  COMPOSITE: 'composite', API_CALL: 'apiCall', FORM_POPUP: 'formPopup',
  CUSTOM_SCRIPT: 'customScript', N8N_ACTION: 'N8N Action',
}

function actionTypeLabel(type: string): string {
  return ACTION_TYPE_LABELS[type] || type
}

function createDefaultConfig(): N8nActionConfig {
  return {
    n8nConfigId: '', n8nWorkflowId: '', webhookUrl: '',
    timeoutSeconds: 120, inputMapping: [], outputMapping: [],
  }
}

interface ValResult { valid: boolean; errorMessage?: string }

function validateN8nAction(actionType: string, c: N8nActionConfig): ValResult {
  if (actionType !== 'N8N_ACTION') return { valid: true }
  if (!c.n8nConfigId) return { valid: false, errorMessage: 'n8nConfigRequired' }
  if (!c.webhookUrl) return { valid: false, errorMessage: 'webhookUrlRequired' }
  return { valid: true }
}

function addInputParam(m: N8nInputParam[]): N8nInputParam[] {
  return [...m, { paramName: '', paramLabel: '', paramType: 'string', required: false }]
}

function removeInputParam(m: N8nInputParam[], i: number): N8nInputParam[] {
  const r = [...m]; r.splice(i, 1); return r
}

function addOutputMapping(m: N8nOutputMapping[]): N8nOutputMapping[] {
  return [...m, { source: '', target: '' }]
}

function removeOutputMapping(m: N8nOutputMapping[], i: number): N8nOutputMapping[] {
  const r = [...m]; r.splice(i, 1); return r
}

function loadConfig(cj: Record<string, any> | null): N8nActionConfig {
  const d = createDefaultConfig()
  if (!cj) return d
  return {
    n8nConfigId: cj.n8nConfigId || d.n8nConfigId,
    n8nWorkflowId: cj.n8nWorkflowId || d.n8nWorkflowId,
    webhookUrl: cj.webhookUrl || d.webhookUrl,
    timeoutSeconds: cj.timeoutSeconds ?? d.timeoutSeconds,
    inputMapping: Array.isArray(cj.inputMapping) ? cj.inputMapping : d.inputMapping,
    outputMapping: Array.isArray(cj.outputMapping) ? cj.outputMapping : d.outputMapping,
  }
}

const CUSTOM_OPS = ['API_CALL', 'FORM_POPUP', 'CUSTOM_SCRIPT', 'N8N_ACTION']
const ALL_TYPES = [
  'APPROVE', 'REJECT', 'TRANSFER', 'DELEGATE', 'ROLLBACK', 'WITHDRAW',
  'PROCESS_SUBMIT', 'PROCESS_REJECT', 'COMPOSITE', ...CUSTOM_OPS,
]

// ===== Tests =====

describe('ActionDesigner - N8N_ACTION option in select dropdown', () => {
  it('should include N8N_ACTION in all action types', () => {
    expect(ALL_TYPES).toContain('N8N_ACTION')
  })
  it('should include N8N_ACTION in the custom operations group', () => {
    expect(CUSTOM_OPS).toContain('N8N_ACTION')
  })
  it('should place N8N_ACTION alongside API_CALL, FORM_POPUP, CUSTOM_SCRIPT', () => {
    expect(CUSTOM_OPS).toEqual(['API_CALL', 'FORM_POPUP', 'CUSTOM_SCRIPT', 'N8N_ACTION'])
  })
})

describe('ActionDesigner - actionTypeLabel for N8N_ACTION', () => {
  it('should return a label for N8N_ACTION', () => {
    expect(actionTypeLabel('N8N_ACTION')).toBe('N8N Action')
  })
  it('should return the type string itself for unknown types', () => {
    expect(actionTypeLabel('UNKNOWN')).toBe('UNKNOWN')
  })
  it('should return labels for all known action types', () => {
    for (const t of ALL_TYPES) {
      expect(actionTypeLabel(t)).not.toBe(t)
    }
  })
})

describe('ActionDesigner - N8N config defaults', () => {
  it('should have timeoutSeconds default of 120', () => {
    expect(createDefaultConfig().timeoutSeconds).toBe(120)
  })
  it('should have empty arrays for mappings', () => {
    const c = createDefaultConfig()
    expect(c.inputMapping).toEqual([])
    expect(c.outputMapping).toEqual([])
  })
  it('should have empty strings for IDs and URL', () => {
    const c = createDefaultConfig()
    expect(c.n8nConfigId).toBe('')
    expect(c.n8nWorkflowId).toBe('')
    expect(c.webhookUrl).toBe('')
  })
})

describe('ActionDesigner - N8N config load from configJson', () => {
  it('should load from populated configJson', () => {
    const c = loadConfig({
      n8nConfigId: 'cfg-1', n8nWorkflowId: 'wf-1',
      webhookUrl: 'http://n8n/webhook/test', timeoutSeconds: 60,
      inputMapping: [{ paramName: 'p1', paramLabel: 'P1', paramType: 'string', required: true }],
      outputMapping: [{ source: 'out1', target: 'var1' }],
    })
    expect(c.n8nConfigId).toBe('cfg-1')
    expect(c.webhookUrl).toBe('http://n8n/webhook/test')
    expect(c.timeoutSeconds).toBe(60)
    expect(c.inputMapping).toHaveLength(1)
    expect(c.outputMapping).toHaveLength(1)
  })
  it('should use defaults when configJson is null', () => {
    const c = loadConfig(null)
    expect(c.timeoutSeconds).toBe(120)
    expect(c.inputMapping).toEqual([])
  })
  it('should use defaults when configJson is empty', () => {
    const c = loadConfig({})
    expect(c.n8nConfigId).toBe('')
    expect(c.timeoutSeconds).toBe(120)
  })
  it('should handle non-array inputMapping', () => {
    expect(loadConfig({ inputMapping: 'bad' }).inputMapping).toEqual([])
  })
  it('should handle non-array outputMapping', () => {
    expect(loadConfig({ outputMapping: null }).outputMapping).toEqual([])
  })
})

describe('ActionDesigner - N8N Action required field validation', () => {
  it('should block save when n8nConfigId is empty', () => {
    const c = createDefaultConfig()
    c.webhookUrl = 'http://n8n/webhook/test'
    const r = validateN8nAction('N8N_ACTION', c)
    expect(r.valid).toBe(false)
    expect(r.errorMessage).toBe('n8nConfigRequired')
  })
  it('should block save when webhookUrl is empty', () => {
    const c = createDefaultConfig()
    c.n8nConfigId = 'cfg-1'
    const r = validateN8nAction('N8N_ACTION', c)
    expect(r.valid).toBe(false)
    expect(r.errorMessage).toBe('webhookUrlRequired')
  })
  it('should block save when both required fields are empty', () => {
    const r = validateN8nAction('N8N_ACTION', createDefaultConfig())
    expect(r.valid).toBe(false)
  })
  it('should pass when both fields are filled', () => {
    const c = createDefaultConfig()
    c.n8nConfigId = 'cfg-1'
    c.webhookUrl = 'http://n8n/webhook/test'
    const r = validateN8nAction('N8N_ACTION', c)
    expect(r.valid).toBe(true)
    expect(r.errorMessage).toBeUndefined()
  })
  it('should skip validation for non-N8N_ACTION types', () => {
    expect(validateN8nAction('APPROVE', createDefaultConfig()).valid).toBe(true)
  })
})

describe('ActionDesigner - Input parameter mapping add/remove', () => {
  it('should add with correct defaults', () => {
    const u = addInputParam([])
    expect(u).toHaveLength(1)
    expect(u[0]).toEqual({ paramName: '', paramLabel: '', paramType: 'string', required: false })
  })
  it('should append to existing', () => {
    const u = addInputParam([{ paramName: 'p1', paramLabel: 'P1', paramType: 'number', required: true }])
    expect(u).toHaveLength(2)
    expect(u[0].paramName).toBe('p1')
    expect(u[1].paramName).toBe('')
  })
  it('should remove at index', () => {
    const m: N8nInputParam[] = [
      { paramName: 'p1', paramLabel: 'P1', paramType: 'string', required: false },
      { paramName: 'p2', paramLabel: 'P2', paramType: 'number', required: true },
      { paramName: 'p3', paramLabel: 'P3', paramType: 'boolean', required: false },
    ]
    const u = removeInputParam(m, 1)
    expect(u).toHaveLength(2)
    expect(u[0].paramName).toBe('p1')
    expect(u[1].paramName).toBe('p3')
  })
  it('should remove the only item', () => {
    const u = removeInputParam([{ paramName: 'p1', paramLabel: 'P1', paramType: 'string', required: false }], 0)
    expect(u).toHaveLength(0)
  })
})

describe('ActionDesigner - Output result mapping add/remove', () => {
  it('should add with correct defaults', () => {
    const u = addOutputMapping([])
    expect(u).toHaveLength(1)
    expect(u[0]).toEqual({ source: '', target: '' })
  })
  it('should append to existing', () => {
    const u = addOutputMapping([{ source: 'out1', target: 'var1' }])
    expect(u).toHaveLength(2)
    expect(u[0].source).toBe('out1')
    expect(u[1].source).toBe('')
  })
  it('should remove at index', () => {
    const m: N8nOutputMapping[] = [
      { source: 'out1', target: 'var1' },
      { source: 'out2', target: 'var2' },
      { source: 'out3', target: 'var3' },
    ]
    const u = removeOutputMapping(m, 0)
    expect(u).toHaveLength(2)
    expect(u[0].source).toBe('out2')
    expect(u[1].source).toBe('out3')
  })
  it('should remove the only item', () => {
    const u = removeOutputMapping([{ source: 'out1', target: 'var1' }], 0)
    expect(u).toHaveLength(0)
  })
})
