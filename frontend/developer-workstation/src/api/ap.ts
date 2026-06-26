/**
 * Activepieces (AP) types for the BPMN service-task designer.
 *
 * AP runs as a single shared runtime per environment, so — unlike the old per-connection
 * model — the designer does not fetch connection configs or workflow lists. The service task
 * only stores the AP flow id; the engine builds the sync webhook URL from its own
 * per-environment configuration at runtime.
 */

/** Variable mapping entry */
export interface VariableMapping {
  source: string
  target: string
}

/** AP task config for BPMN serialization */
export interface ApTaskConfig {
  /** AP flow id (the webhook flow identifier) */
  flowId: string
  /** Optional full sync webhook URL override; blank = build from engine config + flowId */
  webhookUrl: string
  /** Execution timeout seconds */
  timeoutSeconds: number
  /** Retry count on transient failure */
  retryCount: number
  /** Process-variable → AP input mapping */
  inputMapping: VariableMapping[]
  /** AP output → process-variable mapping */
  outputMapping: VariableMapping[]
}
