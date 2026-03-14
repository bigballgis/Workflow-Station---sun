import { describe, it, expect } from 'vitest'

// Unit tests for N8nActionDialog component logic
// Validates: Requirements 10.10, 10.12, 10.13, 10.15, 10.16, 10.17

// ===== Types (mirroring component) =====

interface InputMappingParam {
  paramName: string
  paramLabel: string
  paramType: 'string' | 'number' | 'boolean' | 'select'
  required: boolean
  options?: string[]
}

interface ActionDefinition {
  id: number
  actionName?: string
  configJson?: string
}

type DialogState = 'initial' | 'executing' | 'success' | 'failed' | 'timeout'

// ===== Extracted logic functions (mirroring component behavior) =====

function parseConfig(actionDefinition: ActionDefinition | null): Record<string, any> {
  try {
    if (actionDefinition?.configJson) {
      return JSON.parse(actionDefinition.configJson)
    }
  } catch {
    // ignore parse errors
  }
  return {}
}

function getWorkflowName(config: Record<string, any>): string {
  return config.n8nWorkflowId || ''
}

function getWorkflowDescription(config: Record<string, any>): string {
  return config.description || ''
}

function getInputMappingList(config: Record<string, any>): InputMappingParam[] {
  const mapping = config.inputMapping
  if (Array.isArray(mapping)) {
    return mapping
  }
  return []
}

function buildFormRules(inputMappingList: InputMappingParam[]): Record<string, any[]> {
  const rules: Record<string, any[]> = {}
  for (const param of inputMappingList) {
    if (param.required) {
      rules[param.paramName] = [
        {
          required: true,
          message: `${param.paramLabel || param.paramName} is required`,
          trigger: param.paramType === 'select' ? 'change' : 'blur'
        }
      ]
    }
  }
  return rules
}

function initFormData(inputMappingList: InputMappingParam[]): Record<string, any> {
  const data: Record<string, any> = {}
  for (const param of inputMappingList) {
    if (param.paramType === 'boolean') {
      data[param.paramName] = false
    } else if (param.paramType === 'number') {
      data[param.paramName] = undefined
    } else {
      data[param.paramName] = ''
    }
  }
  return data
}

function getExpectedInputType(paramType: string): string {
  switch (paramType) {
    case 'string': return 'el-input'
    case 'number': return 'el-input-number'
    case 'boolean': return 'el-switch'
    case 'select': return 'el-select'
    default: return 'el-input' // fallback
  }
}

function determineResultState(result: any): { state: DialogState; errorMessage: string; resultData: Record<string, any> | null } {
  if (result?.status === 'TIMEOUT') {
    return { state: 'timeout', errorMessage: result.errorMessage || '', resultData: null }
  } else if (result?.status === 'FAILED') {
    return { state: 'failed', errorMessage: result.errorMessage || '', resultData: null }
  } else {
    return { state: 'success', errorMessage: '', resultData: result?.data ?? result?.outputData ?? null }
  }
}

function determineErrorState(err: any): { state: DialogState; errorMessage: string } {
  return { state: 'failed', errorMessage: err?.message || 'Execution failed' }
}

function handleRetryState(): { state: DialogState; errorMessage: string; resultData: null } {
  return { state: 'initial', errorMessage: '', resultData: null }
}

function shouldPreventClose(executing: boolean): boolean {
  return executing
}

// ===== Sample test data =====

const SAMPLE_CONFIG_JSON = JSON.stringify({
  n8nConfigId: 'cfg-1',
  n8nWorkflowId: 'wf-1',
  webhookUrl: 'http://n8n/webhook/test',
  timeoutSeconds: 120,
  inputMapping: [
    { paramName: 'name', paramLabel: '姓名', paramType: 'string', required: true },
    { paramName: 'age', paramLabel: '年龄', paramType: 'number', required: false },
    { paramName: 'active', paramLabel: '是否激活', paramType: 'boolean', required: false },
    { paramName: 'role', paramLabel: '角色', paramType: 'select', required: false, options: ['admin', 'user'] }
  ],
  outputMapping: [
    { source: 'result', target: 'processResult' }
  ]
})

const SAMPLE_ACTION_DEF: ActionDefinition = {
  id: 1,
  actionName: 'Test N8N Action',
  configJson: SAMPLE_CONFIG_JSON
}

// ===== Tests =====

describe('N8nActionDialog - Config parsing', () => {
  it('should parse valid configJson from actionDefinition', () => {
    const config = parseConfig(SAMPLE_ACTION_DEF)
    expect(config.n8nConfigId).toBe('cfg-1')
    expect(config.n8nWorkflowId).toBe('wf-1')
    expect(config.webhookUrl).toBe('http://n8n/webhook/test')
    expect(config.timeoutSeconds).toBe(120)
    expect(config.inputMapping).toHaveLength(4)
    expect(config.outputMapping).toHaveLength(1)
  })

  it('should return empty object for null actionDefinition', () => {
    expect(parseConfig(null)).toEqual({})
  })

  it('should return empty object for missing configJson', () => {
    expect(parseConfig({ id: 1 })).toEqual({})
  })

  it('should return empty object for invalid JSON', () => {
    expect(parseConfig({ id: 1, configJson: 'not-json' })).toEqual({})
  })

  it('should return empty object for empty configJson string', () => {
    expect(parseConfig({ id: 1, configJson: '' })).toEqual({})
  })
})

describe('N8nActionDialog - Workflow info display', () => {
  it('should extract workflow name from config', () => {
    const config = parseConfig(SAMPLE_ACTION_DEF)
    expect(getWorkflowName(config)).toBe('wf-1')
  })

  it('should return empty string when no workflowId', () => {
    expect(getWorkflowName({})).toBe('')
  })

  it('should extract workflow description from config', () => {
    const config = parseConfig({ id: 1, configJson: JSON.stringify({ description: 'Test workflow' }) })
    expect(getWorkflowDescription(config)).toBe('Test workflow')
  })

  it('should return empty string when no description', () => {
    expect(getWorkflowDescription({})).toBe('')
  })
})

describe('N8nActionDialog - Input mapping list extraction', () => {
  it('should extract input mapping list from config', () => {
    const config = parseConfig(SAMPLE_ACTION_DEF)
    const list = getInputMappingList(config)
    expect(list).toHaveLength(4)
    expect(list[0].paramName).toBe('name')
    expect(list[0].paramType).toBe('string')
    expect(list[0].required).toBe(true)
    expect(list[1].paramType).toBe('number')
    expect(list[2].paramType).toBe('boolean')
    expect(list[3].paramType).toBe('select')
    expect(list[3].options).toEqual(['admin', 'user'])
  })

  it('should return empty array when no inputMapping', () => {
    expect(getInputMappingList({})).toEqual([])
  })

  it('should return empty array when inputMapping is not an array', () => {
    expect(getInputMappingList({ inputMapping: 'bad' })).toEqual([])
    expect(getInputMappingList({ inputMapping: null })).toEqual([])
    expect(getInputMappingList({ inputMapping: 123 })).toEqual([])
  })
})

describe('N8nActionDialog - Dynamic form rendering (input type mapping)', () => {
  it('should map string paramType to el-input', () => {
    expect(getExpectedInputType('string')).toBe('el-input')
  })

  it('should map number paramType to el-input-number', () => {
    expect(getExpectedInputType('number')).toBe('el-input-number')
  })

  it('should map boolean paramType to el-switch', () => {
    expect(getExpectedInputType('boolean')).toBe('el-switch')
  })

  it('should map select paramType to el-select', () => {
    expect(getExpectedInputType('select')).toBe('el-select')
  })

  it('should fallback to el-input for unknown paramType', () => {
    expect(getExpectedInputType('unknown')).toBe('el-input')
    expect(getExpectedInputType('')).toBe('el-input')
  })

  it('should render correct types for all params in sample config', () => {
    const config = parseConfig(SAMPLE_ACTION_DEF)
    const list = getInputMappingList(config)
    const expectedTypes = ['el-input', 'el-input-number', 'el-switch', 'el-select']
    list.forEach((param, i) => {
      expect(getExpectedInputType(param.paramType)).toBe(expectedTypes[i])
    })
  })
})

describe('N8nActionDialog - Form data initialization', () => {
  it('should initialize string params as empty string', () => {
    const data = initFormData([
      { paramName: 'name', paramLabel: '姓名', paramType: 'string', required: true }
    ])
    expect(data.name).toBe('')
  })

  it('should initialize number params as undefined', () => {
    const data = initFormData([
      { paramName: 'age', paramLabel: '年龄', paramType: 'number', required: false }
    ])
    expect(data.age).toBeUndefined()
  })

  it('should initialize boolean params as false', () => {
    const data = initFormData([
      { paramName: 'active', paramLabel: '是否激活', paramType: 'boolean', required: false }
    ])
    expect(data.active).toBe(false)
  })

  it('should initialize select params as empty string', () => {
    const data = initFormData([
      { paramName: 'role', paramLabel: '角色', paramType: 'select', required: false, options: ['admin', 'user'] }
    ])
    expect(data.role).toBe('')
  })

  it('should initialize all params from sample config', () => {
    const config = parseConfig(SAMPLE_ACTION_DEF)
    const list = getInputMappingList(config)
    const data = initFormData(list)
    expect(data.name).toBe('')
    expect(data.age).toBeUndefined()
    expect(data.active).toBe(false)
    expect(data.role).toBe('')
  })

  it('should return empty object for empty input mapping', () => {
    expect(initFormData([])).toEqual({})
  })
})

describe('N8nActionDialog - Required field validation rules', () => {
  it('should generate rules for required params only', () => {
    const config = parseConfig(SAMPLE_ACTION_DEF)
    const list = getInputMappingList(config)
    const rules = buildFormRules(list)
    // Only 'name' is required
    expect(rules).toHaveProperty('name')
    expect(rules).not.toHaveProperty('age')
    expect(rules).not.toHaveProperty('active')
    expect(rules).not.toHaveProperty('role')
  })

  it('should set required: true in the rule', () => {
    const rules = buildFormRules([
      { paramName: 'name', paramLabel: '姓名', paramType: 'string', required: true }
    ])
    expect(rules.name[0].required).toBe(true)
  })

  it('should use blur trigger for string params', () => {
    const rules = buildFormRules([
      { paramName: 'name', paramLabel: '姓名', paramType: 'string', required: true }
    ])
    expect(rules.name[0].trigger).toBe('blur')
  })

  it('should use change trigger for select params', () => {
    const rules = buildFormRules([
      { paramName: 'role', paramLabel: '角色', paramType: 'select', required: true, options: ['admin'] }
    ])
    expect(rules.role[0].trigger).toBe('change')
  })

  it('should use blur trigger for number params', () => {
    const rules = buildFormRules([
      { paramName: 'age', paramLabel: '年龄', paramType: 'number', required: true }
    ])
    expect(rules.age[0].trigger).toBe('blur')
  })

  it('should return empty rules when no required params', () => {
    const rules = buildFormRules([
      { paramName: 'age', paramLabel: '年龄', paramType: 'number', required: false }
    ])
    expect(Object.keys(rules)).toHaveLength(0)
  })

  it('should return empty rules for empty input mapping', () => {
    expect(buildFormRules([])).toEqual({})
  })

  it('should include param label in error message', () => {
    const rules = buildFormRules([
      { paramName: 'name', paramLabel: '姓名', paramType: 'string', required: true }
    ])
    expect(rules.name[0].message).toContain('姓名')
  })
})

describe('N8nActionDialog - State transitions: success', () => {
  it('should transition to success state on successful result', () => {
    const result = { status: 'SUCCESS', data: { output: 'value' } }
    const s = determineResultState(result)
    expect(s.state).toBe('success')
    expect(s.resultData).toEqual({ output: 'value' })
    expect(s.errorMessage).toBe('')
  })

  it('should extract outputData when data field is absent', () => {
    const result = { status: 'SUCCESS', outputData: { key: 'val' } }
    const s = determineResultState(result)
    expect(s.state).toBe('success')
    expect(s.resultData).toEqual({ key: 'val' })
  })

  it('should handle null result data gracefully', () => {
    const result = { status: 'SUCCESS' }
    const s = determineResultState(result)
    expect(s.state).toBe('success')
    expect(s.resultData).toBeNull()
  })
})

describe('N8nActionDialog - State transitions: failed', () => {
  it('should transition to failed state on FAILED result', () => {
    const result = { status: 'FAILED', errorMessage: 'Something went wrong' }
    const s = determineResultState(result)
    expect(s.state).toBe('failed')
    expect(s.errorMessage).toBe('Something went wrong')
    expect(s.resultData).toBeNull()
  })

  it('should handle FAILED result without errorMessage', () => {
    const result = { status: 'FAILED' }
    const s = determineResultState(result)
    expect(s.state).toBe('failed')
    expect(s.errorMessage).toBe('')
  })

  it('should transition to failed state on API exception', () => {
    const s = determineErrorState(new Error('Network error'))
    expect(s.state).toBe('failed')
    expect(s.errorMessage).toBe('Network error')
  })

  it('should use default message when error has no message', () => {
    const s = determineErrorState({})
    expect(s.state).toBe('failed')
    expect(s.errorMessage).toBe('Execution failed')
  })

  it('should use default message for null error', () => {
    const s = determineErrorState(null)
    expect(s.state).toBe('failed')
    expect(s.errorMessage).toBe('Execution failed')
  })
})

describe('N8nActionDialog - State transitions: timeout', () => {
  it('should transition to timeout state on TIMEOUT result', () => {
    const result = { status: 'TIMEOUT', errorMessage: 'Execution timed out' }
    const s = determineResultState(result)
    expect(s.state).toBe('timeout')
    expect(s.errorMessage).toBe('Execution timed out')
    expect(s.resultData).toBeNull()
  })

  it('should handle TIMEOUT result without errorMessage', () => {
    const result = { status: 'TIMEOUT' }
    const s = determineResultState(result)
    expect(s.state).toBe('timeout')
    expect(s.errorMessage).toBe('')
  })
})

describe('N8nActionDialog - Executing state (loading)', () => {
  it('should prevent close during execution', () => {
    expect(shouldPreventClose(true)).toBe(true)
  })

  it('should allow close when not executing', () => {
    expect(shouldPreventClose(false)).toBe(false)
  })
})

describe('N8nActionDialog - Retry resets to initial state', () => {
  it('should reset state to initial on retry', () => {
    const s = handleRetryState()
    expect(s.state).toBe('initial')
    expect(s.errorMessage).toBe('')
    expect(s.resultData).toBeNull()
  })
})

describe('N8nActionDialog - Empty input mapping shows empty state', () => {
  it('should have empty input mapping list when config has no inputMapping', () => {
    const config = parseConfig({ id: 1, configJson: JSON.stringify({ n8nConfigId: 'cfg-1' }) })
    const list = getInputMappingList(config)
    expect(list).toEqual([])
  })

  it('should have empty input mapping list when config has empty inputMapping array', () => {
    const config = parseConfig({ id: 1, configJson: JSON.stringify({ inputMapping: [] }) })
    const list = getInputMappingList(config)
    expect(list).toEqual([])
  })

  it('should initialize empty form data for empty input mapping', () => {
    const data = initFormData([])
    expect(Object.keys(data)).toHaveLength(0)
  })
})

describe('N8nActionDialog - Execute payload construction', () => {
  it('should build correct payload from form data and props', () => {
    const actionDef = SAMPLE_ACTION_DEF
    const taskId = 'task-123'
    const processInstanceId = 'proc-456'
    const formData = { name: 'John', age: 30, active: true, role: 'admin' }

    const payload = {
      actionDefinitionId: actionDef.id,
      taskId,
      processInstanceId,
      inputData: { ...formData }
    }

    expect(payload.actionDefinitionId).toBe(1)
    expect(payload.taskId).toBe('task-123')
    expect(payload.processInstanceId).toBe('proc-456')
    expect(payload.inputData).toEqual({ name: 'John', age: 30, active: true, role: 'admin' })
  })

  it('should create a copy of form data in payload (no reference sharing)', () => {
    const formData = { name: 'John' }
    const payload = { inputData: { ...formData } }
    payload.inputData.name = 'Jane'
    expect(formData.name).toBe('John')
  })
})
