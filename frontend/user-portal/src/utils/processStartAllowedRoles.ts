/**
 * How the first node (New Request) lets the initiator submit:
 * - `configured`: a submit Action (PROCESS_SUBMIT) bound in DW is visible to this session — it is the submit button;
 * - `default`: no submit Action is bound — the default "Submit Application" is offered next to the bound ones;
 * - `blocked`: submit Actions are bound but Allowed Roles hide all of them — no submit at all, so the
 *   default button never bypasses a role restriction.
 *
 * Only the first node has this default; task pages render exactly the Actions configured in DW.
 */
export type StartSubmitMode = 'configured' | 'default' | 'blocked'

interface StartActionLike {
  actionType?: string | null
}

const isSubmitAction = (action: StartActionLike) => action.actionType === 'PROCESS_SUBMIT'

export function startSubmitMode(
  boundActions: readonly StartActionLike[],
  visibleActions: readonly StartActionLike[],
): StartSubmitMode {
  if (visibleActions.some(isSubmitAction)) return 'configured'
  return boundActions.some(isSubmitAction) ? 'blocked' : 'default'
}
