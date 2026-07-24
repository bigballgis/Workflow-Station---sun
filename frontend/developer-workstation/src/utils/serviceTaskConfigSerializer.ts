/**
 * ServiceTask (AP) Task Config Serialization/Deserialization utilities.
 * Handles conversion between ServiceTaskConfig objects and BPMN extension properties.
 *
 * The engine reads these `ap:`-prefixed properties from the deployed service task and
 * (because `ap:flowId` is present) binds the task to the `${apTaskExecutor}` delegate at
 * deploy time.
 */

import type { ServiceTaskConfig, VariableMapping } from '@/api/serviceTask'

/** BPMN extension property prefix for AP config */
export const AP_PREFIX = 'ap:'

/** BPMN extension property keys */
export const AP_KEYS = {
  flowId: `${AP_PREFIX}flowId`,
  webhookUrl: `${AP_PREFIX}webhookUrl`,
  timeoutSeconds: `${AP_PREFIX}timeoutSeconds`,
  retryCount: `${AP_PREFIX}retryCount`,
  inputMapping: `${AP_PREFIX}inputMapping`,
  outputMapping: `${AP_PREFIX}outputMapping`,
} as const

/** Validation error messages */
export interface ApValidationErrors {
  flowId: string
}

/** Default AP task config */
export function createDefaultApConfig(): ServiceTaskConfig {
  return {
    flowId: '',
    webhookUrl: '',
    timeoutSeconds: 120,
    retryCount: 3,
    inputMapping: [],
    outputMapping: [],
  }
}

/**
 * Serialize ServiceTaskConfig to BPMN extension property key-value pairs.
 * Variable mappings are serialized as JSON strings.
 */
export function serializeApConfig(config: ServiceTaskConfig): Record<string, string> {
  return {
    [AP_KEYS.flowId]: config.flowId || '',
    [AP_KEYS.webhookUrl]: config.webhookUrl || '',
    [AP_KEYS.timeoutSeconds]: String(config.timeoutSeconds ?? 120),
    [AP_KEYS.retryCount]: String(config.retryCount ?? 3),
    [AP_KEYS.inputMapping]: JSON.stringify(config.inputMapping || []),
    [AP_KEYS.outputMapping]: JSON.stringify(config.outputMapping || []),
  }
}

/**
 * Deserialize BPMN extension properties (Record<string, any>) back to ServiceTaskConfig.
 * Handles both prefixed (ap:flowId) and raw property names.
 */
export function deserializeApConfig(ext: Record<string, any>): ServiceTaskConfig {
  const get = (key: string): any => ext[`${AP_PREFIX}${key}`] ?? ext[key]

  return {
    flowId: String(get('flowId') ?? ''),
    webhookUrl: String(get('webhookUrl') ?? ''),
    timeoutSeconds: parseNumberWithDefault(get('timeoutSeconds'), 120),
    retryCount: parseNumberWithDefault(get('retryCount'), 3),
    inputMapping: parseMappingArray(get('inputMapping')),
    outputMapping: parseMappingArray(get('outputMapping')),
  }
}

/**
 * Validate AP task config required fields.
 * Returns error messages for invalid fields (empty string = valid).
 */
export function validateApConfig(config: ServiceTaskConfig): ApValidationErrors {
  return {
    flowId: config.flowId || config.webhookUrl ? '' : 'AP flow ID is required',
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
