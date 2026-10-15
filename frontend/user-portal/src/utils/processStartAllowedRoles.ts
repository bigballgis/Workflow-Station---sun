/**
 * Process start: when BPMN bound custom actions but none pass Allowed Roles, do not fall back to default Submit.
 */
export function isStartSubmitBlockedByAllowedRoles(
  hadBpmnActionIds: boolean,
  resolvedActionCount: number,
  visibleActionCount: number,
): boolean {
  return hadBpmnActionIds && resolvedActionCount > 0 && visibleActionCount === 0
}
