/**
 * Environment-level settings. They come from the platform deployment (K8s ConfigMap + Key Vault,
 * dev compose) and reach this process through AP_SANDBOX_PROPAGATED_ENV_VARS — never from the flow,
 * so a flow exported from one environment runs unchanged in another and no secret is stored in it.
 */
export type ContentOrganizerConfig = {
  ib2bTokenUrl: string;
  ib2bUsername: string;
  ib2bSecret: string;
  /** e.g. https://uat-api.gcp.cloud.hk.hsbc/cmb-hase-co-pa-completion-proxy/v1 (no trailing slash) */
  baseUrl: string;
  applicationId: string;
  apiVersion: string;
  /**
   * The extraction prompt setting configured in the Content Organizer Prompt Settings Workbench
   * ("Hermes Field Extraction (JSON)"): Content Organizer rejects a completion without one. The
   * template holds the extraction rules; this piece only fills its `{fields}` variable.
   */
  promptSettingId: string;
  /** ID (not name) of the template's `{fields}` variable — `promptVars[].name` takes the ID. */
  promptVariableId: string;
  /**
   * Top-level `workflow` / `version` of the completion request. The API document's values
   * (default / 1.0) are accepted by UAT; the Content Organizer web UI itself sends
   * "ReasearchChatCompletion" / "001".
   */
  workflow: string;
  workflowVersion: string;
  /**
   * `parameter.model` / `parameter.applicationName` — required by the real API (422 without them),
   * although the API document does not list them. Defaults are the Hermes use case's values.
   */
  model: string;
  applicationName: string;
};

const REQUIRED = {
  ib2bTokenUrl: 'IB2B_TOKEN_URL',
  ib2bUsername: 'IB2B_USERNAME',
  ib2bSecret: 'IB2B_SECRET',
  baseUrl: 'CONTENT_ORGANIZER_BASE_URL',
  applicationId: 'CONTENT_ORGANIZER_APPLICATION_ID',
  promptSettingId: 'CONTENT_ORGANIZER_PROMPT_SETTING_ID',
  promptVariableId: 'CONTENT_ORGANIZER_PROMPT_VARIABLE_ID',
} as const;

/** API version sent in `metadata.apiVersion` — the value in the Content Organizer request examples. */
const DEFAULT_API_VERSION = '2024-10-01-preview';
const DEFAULT_WORKFLOW = 'default';
const DEFAULT_WORKFLOW_VERSION = '1.0';
/** The model configured on the Hermes Workflow use case. */
const DEFAULT_MODEL = 'gemini-3.5-flash';
const DEFAULT_APPLICATION_NAME = 'Hermes Workflow';

export function readConfig(env: NodeJS.ProcessEnv = process.env): ContentOrganizerConfig {
  const missing = Object.values(REQUIRED).filter((name) => !env[name]?.trim());
  if (missing.length > 0) {
    throw new Error(
      `Content Organizer is not configured on this Automation instance; missing environment variable(s): ${missing.join(', ')}. ` +
        'They must be set on the Automation deployment and listed in AP_SANDBOX_PROPAGATED_ENV_VARS.',
    );
  }
  const value = (name: string) => env[name]!.trim();
  return {
    ib2bTokenUrl: value(REQUIRED.ib2bTokenUrl),
    ib2bUsername: value(REQUIRED.ib2bUsername),
    ib2bSecret: value(REQUIRED.ib2bSecret),
    baseUrl: value(REQUIRED.baseUrl).replace(/\/+$/, ''),
    applicationId: value(REQUIRED.applicationId),
    apiVersion: env['CONTENT_ORGANIZER_API_VERSION']?.trim() || DEFAULT_API_VERSION,
    promptSettingId: value(REQUIRED.promptSettingId),
    promptVariableId: value(REQUIRED.promptVariableId),
    workflow: env['CONTENT_ORGANIZER_WORKFLOW']?.trim() || DEFAULT_WORKFLOW,
    workflowVersion: env['CONTENT_ORGANIZER_WORKFLOW_VERSION']?.trim() || DEFAULT_WORKFLOW_VERSION,
    model: env['CONTENT_ORGANIZER_MODEL']?.trim() || DEFAULT_MODEL,
    applicationName: env['CONTENT_ORGANIZER_APPLICATION_NAME']?.trim() || DEFAULT_APPLICATION_NAME,
  };
}
