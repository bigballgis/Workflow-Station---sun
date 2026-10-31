import { nextTick } from 'vue'

/**
 * wangEditor fires onChange during create, often with empty `<p><br></p>`.
 * Keep that create-time event from overwriting a saved / email-written value.
 *
 * Call before swapping in the new editor instance. `setReady(true)` runs on the
 * next tick only if `isCurrent()` still matches that instance.
 */
export function armEditorChangeGate(
  isCurrent: () => boolean,
  setReady: (ready: boolean) => void,
): void {
  setReady(false)
  void nextTick(() => {
    if (isCurrent()) setReady(true)
  })
}
