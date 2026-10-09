type LeaveConfirmation = () => boolean | Promise<boolean>

let activeLeaveConfirmation: LeaveConfirmation | null = null

/** Lets shell-level navigation ask the mounted Process/Form designer before leaving. */
export function registerUnsavedDesignerLeaveConfirmation(confirm: LeaveConfirmation): () => void {
  activeLeaveConfirmation = confirm
  return () => {
    if (activeLeaveConfirmation === confirm) {
      activeLeaveConfirmation = null
    }
  }
}

/** Workspace switching reloads the page, so it cannot rely on a component route guard. */
export async function confirmUnsavedDesignerBeforeReload(): Promise<boolean> {
  return activeLeaveConfirmation ? await activeLeaveConfirmation() : true
}
