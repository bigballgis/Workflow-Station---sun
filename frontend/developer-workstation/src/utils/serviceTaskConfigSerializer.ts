/**
 * ServiceTask (AP) config serialization for the BPMN designer.
 *
 * FR-C01/C02: a service task of type "ap" stores ONE business key (`ap:flowKey`,
 * the flow's `metadata.hermesFlowKey`). The engine resolves the key to the
 * environment-local flow id at deploy/run time; webhook URL, timeout, retry and
 * variable mappings are no longer configured on the BPMN — the flow itself reads
 * `{{trigger.body.variables.<name>}}` and returns `{"variables": {...}}`.
 *
 * Legacy BPMN may still carry `ap:flowId` (+ webhook/timeout/mapping keys). On
 * load the old flow id is surfaced as `legacyFlowId` so the panel can prefill it;
 * on save everything is written as `ap:flowKey` and every legacy key is cleared
 * (the engine resolves by reference, so an id value in `ap:flowKey` still works).
 */

/** AP task config for BPMN serialization */
export interface ServiceTaskConfig {
  /** Business key of the automation flow (metadata.hermesFlowKey) */
  flowKey: string
}

/** BPMN extension property prefix for AP config */
export const AP_PREFIX = 'ap:'

/** The single extension property the panel writes */
export const AP_FLOW_KEY = `${AP_PREFIX}flowKey`

/**
 * Superseded `ap:*` keys — cleared on every save and on service-type switches so
 * exports / version snapshots don't carry dead config.
 */
export const LEGACY_AP_KEYS = [
  `${AP_PREFIX}flowId`,
  `${AP_PREFIX}webhookUrl`,
  `${AP_PREFIX}timeoutSeconds`,
  `${AP_PREFIX}retryCount`,
  `${AP_PREFIX}inputMapping`,
  `${AP_PREFIX}outputMapping`,
] as const

/** All ap:* keys a service task may carry (current + legacy) */
export const ALL_AP_KEYS = [AP_FLOW_KEY, ...LEGACY_AP_KEYS] as const

/** Validation error messages */
export interface ApValidationErrors {
  flowKey: string
}

/** Default AP task config */
export function createDefaultApConfig(): ServiceTaskConfig {
  return { flowKey: '' }
}

/** Serialize ServiceTaskConfig to BPMN extension property key-value pairs. */
export function serializeApConfig(config: ServiceTaskConfig): Record<string, string> {
  return { [AP_FLOW_KEY]: config.flowKey || '' }
}

/** Result of reading a service task's ap:* properties. */
export interface DeserializedApConfig extends ServiceTaskConfig {
  /**
   * Set when the task has no `ap:flowKey` but still carries a legacy `ap:flowId`.
   * The panel prefills the input with it and flags the binding as legacy; saving
   * rewrites it as `ap:flowKey` (same value — the engine resolves ids too).
   */
  legacyFlowId: string
}

/**
 * Deserialize BPMN extension properties back to ServiceTaskConfig.
 * Handles both prefixed (ap:flowKey) and raw property names.
 */
export function deserializeApConfig(ext: Record<string, unknown>): DeserializedApConfig {
  const get = (key: string): unknown => ext[`${AP_PREFIX}${key}`] ?? ext[key]
  const flowKey = String(get('flowKey') ?? '')
  const legacyFlowId = flowKey ? '' : String(get('flowId') ?? '')
  return { flowKey, legacyFlowId }
}

/**
 * Validate AP task config required fields.
 * Returns error messages for invalid fields (empty string = valid).
 */
export function validateApConfig(config: ServiceTaskConfig): ApValidationErrors {
  return {
    flowKey: config.flowKey.trim() ? '' : 'Automation flow key is required',
  }
}
