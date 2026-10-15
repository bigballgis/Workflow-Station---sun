/** Minimal FU content + actions for Allowed Roles UI verification (network mocks). */

export const VERIFY_FU_KEY = 'verify-allowed-roles'

const MIN_FORM_DATA = JSON.stringify({
  rule: [
    {
      type: 'input',
      field: 'title',
      title: 'Title',
      props: { placeholder: 'Allowed Roles verify fixture' },
    },
  ],
  option: {},
})

const MIN_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://workflow.platform/schema/custom" id="Definitions_verify">
  <bpmn:process id="Process_verify" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_start" />
    <bpmn:userTask id="Activity_start" name="Start">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="form-verify" />
          <custom:property name="formName" value="Verify" />
          <custom:property name="actionIds" value="901,902,903" />
        </custom:properties>
      </bpmn:extensionElements>
    </bpmn:userTask>
  </bpmn:process>
</bpmn:definitions>`

export function buildVerifyFunctionUnitContent() {
  return {
    success: true,
    data: {
      name: 'Allowed Roles Verify',
      version: '1',
      code: VERIFY_FU_KEY,
      forms: [
        {
          id: 'form-verify',
          name: 'Verify Form',
          sourceId: 'form-verify',
          type: 'FORM',
          data: MIN_FORM_DATA,
          tableBindings: [],
        },
      ],
      processes: [{ id: 'proc-verify', name: 'main.bpmn', data: MIN_BPMN }],
      dataTables: [],
      miAssignments: [],
    },
  }
}

export const VERIFY_ACTIONS = [
  {
    id: '901',
    actionId: '901',
    actionName: 'Unrestricted Action',
    actionType: 'PROCESS_SUBMIT',
    buttonColor: 'primary',
    // Legacy shape: no allowedRoles key (same as empty selection — no restriction)
    configJson: JSON.stringify({ sortOrder: 0 }),
  },
  {
    id: '902',
    actionId: '902',
    actionName: 'Role Match Action',
    actionType: 'PROCESS_SUBMIT',
    buttonColor: 'success',
    configJson: JSON.stringify({ allowedRoles: ['ROLE_MATCH'] }),
  },
  {
    id: '903',
    actionId: '903',
    actionName: 'Hidden Role Action',
    actionType: 'PROCESS_SUBMIT',
    buttonColor: 'warning',
    configJson: JSON.stringify({ allowedRoles: ['ROLE_OTHER'] }),
  },
]

/** Every action requires ROLE_MATCH — used to assert no default Submit when all are hidden. */
export const VERIFY_ACTIONS_ALL_RESTRICTED = VERIFY_ACTIONS.map((action) => ({
  ...action,
  configJson: JSON.stringify({ allowedRoles: ['ROLE_MATCH'] }),
}))

/**
 * @param {import('playwright').Page} page
 * @param {string} fuKey
 * @param {{ actions?: typeof VERIFY_ACTIONS }} [opts]
 */
export async function installAllowedRolesStartFixtures(page, fuKey = VERIFY_FU_KEY, opts = {}) {
  const contentBody = buildVerifyFunctionUnitContent()
  const actions = opts.actions ?? VERIFY_ACTIONS
  const actionsBody = { success: true, data: actions }

  await page.route(`**/api/portal/processes/function-units/${fuKey}/content**`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(contentBody),
    })
  })

  await page.route('**/api/portal/processes/actions**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(actionsBody),
    })
  })
}
