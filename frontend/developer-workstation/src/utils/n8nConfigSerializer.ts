/**
 * N8N Task Config Serialization/Deserialization utilities
 * Handles conversion between N8nTaskConfig objects and BPMN extension properties
 */

import type { N8nTaskConfig, VariableMapping } from '@/api/n8n'

/** BPMN extension property prefix for N8N config */
export const N8N_PREFIX = 'n8n:'

/** BPMN extension property keys */
export const N8N_KEYS = {
  configId: `${N8N_PREFIX}configId`,
  workflowId: `${N8N_PREFIX}workflowId`,
  webhookUrl: `${N8N_PREFIX}webhookUrl`,
  timeoutSeconds: `${N8N_PREFIX}timeoutSeconds`,
  retryCount: `${N8N_PREFIX}retryCount`,
  inputMapping: `${N8N_PREFIX}inputMapping`,
  outputMapping: `${N8N_PREFIX}outputMapping`,
} as const

/** Validation error messages */
export interface N8nValidationErrors {
  configId: string
  webhookUrl: string
}

/** Default N8N task config */
export function createDefaultN8nConfig(): N8nTaskConfig {
  return {
    configId: '',
    workflowId: '',
    webhookUrl: '',
    timeoutSeconds: 300,
    retryCount: 3,
    inputMapping: [],
    outputMapping: [],
  }
}

/**
 * Serialize N8nTaskConfig to BPMN extension property key-value pairs.
 * Variable mappings are serialized as JSON strings.
 */
export function serializeN8nConfig(config: N8nTaskConfig): Record<string, string> {
  return {
    [N8N_KEYS.configId]: config.configId || '',
    [N8N_KEYS.workflowId]: config.workflowId || '',
    [N8N_KEYS.webhookUrl]: config.webhookUrl || '',
    [N8N_KEYS.timeoutSeconds]: String(config.timeoutSeconds ?? 300),
    [N8N_KEYS.retryCount]: String(config.retryCount ?? 3),
    [N8N_KEYS.inputMapping]: JSON.stringify(config.inputMapping || []),
    [N8N_KEYS.outputMapping]: JSON.stringify(config.outputMapping || []),
  }
}

/**
 * Deserialize BPMN extension properties (Record<string, any>) back to N8nTaskConfig.
 * Handles both prefixed (n8n:configId) and raw property names.
 */
export function deserializeN8nConfig(ext: Record<string, any>): N8nTaskConfig {
  const get = (key: string): any => {
    // Try prefixed key first, then raw key
    return ext[`${N8N_PREFIX}${key}`] ?? ext[key]
  }

  return {
    configId: String(get('configId') ?? ''),
    workflowId: String(get('workflowId') ?? ''),
    webhookUrl: String(get('webhookUrl') ?? ''),
    timeoutSeconds: parseNumberWithDefault(get('timeoutSeconds'), 300),
    retryCount: parseNumberWithDefault(get('retryCount'), 3),
    inputMapping: parseMappingArray(get('inputMapping')),
    outputMapping: parseMappingArray(get('outputMapping')),
  }
}

/**
 * Validate N8N task config required fields.
 * Returns error messages for invalid fields (empty string = valid).
 */
export function validateN8nConfig(config: N8nTaskConfig): N8nValidationErrors {
  return {
    configId: config.configId ? '' : 'N8N 连接配置不能为空',
    webhookUrl: config.webhookUrl ? '' : 'Webhook URL 不能为空',
  }
}

/** Parse a number with a default fallback */
function parseNumberWithDefault(value: any, defaultValue: number): number {
  if (value === undefined || value === null || value === '') return defaultValue
  const num = Number(value)
  return isNaN(num) ? defaultValue : num
}

/** Parse variable mapping array from JSON string or array */
function parseMappingArray(value: any): VariableMapping[] {
  if (!value) return []
  if (Array.isArray(value)) return value.filter(isValidMapping)
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      if (Array.isArray(parsed)) return parsed.filter(isValidMapping)
    } catch {
      // ignore parse errors
    }
  }
  return []
}

/** Check if an object is a valid VariableMapping */
function isValidMapping(item: any): item is VariableMapping {
  return item && typeof item === 'object' && 'source' in item && 'target' in item
}
