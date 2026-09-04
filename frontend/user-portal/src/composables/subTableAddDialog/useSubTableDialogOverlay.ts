import { computed, getCurrentScope, onScopeDispose, ref, watch, type Ref } from 'vue'
import { useZIndex } from 'element-plus'

/**
 * Add/Edit / Link Form overlay z-index.
 *
 * Element Plus select / date-picker poppers teleport to body and call
 * `useZIndex().nextZIndex()` (baseline 2000). A hardcoded dialog z-index of
 * 2010 puts those poppers *behind* the dialog: the Merchant Credit list and
 * Temporary Refund Date calendar never appear, while the input itself still
 * accepts typed values.
 *
 * Take a ticket from the same counter when the dialog opens, then lift
 * teleported poppers above it via `--sub-table-dialog-popper-z`. Nested
 * overlays push/pop that variable so closing an inner dialog restores the
 * parent, and closing the last overlay clears it.
 */
export const SUB_TABLE_DIALOG_POPPER_CLASS = 'sub-table-dialog-popper'
export const SUB_TABLE_DIALOG_POPPER_Z_VAR = '--sub-table-dialog-popper-z'
const POPPER_Z_GAP = 50
const popperZStack: number[] = []

function applyPopperZVar(): void {
  const top = popperZStack[popperZStack.length - 1]
  if (top == null) {
    document.documentElement.style.removeProperty(SUB_TABLE_DIALOG_POPPER_Z_VAR)
    return
  }
  document.documentElement.style.setProperty(SUB_TABLE_DIALOG_POPPER_Z_VAR, String(top))
}

export function useSubTableDialogOverlay(visible: Ref<boolean>) {
  const { nextZIndex } = useZIndex()
  const dialogZIndex = ref(2010)
  const backdropZIndex = computed(() => Math.max(dialogZIndex.value - 1, 1))
  let holdingTicket = false

  function refreshOverlayZIndex(): void {
    const z = nextZIndex()
    dialogZIndex.value = z
    if (holdingTicket) popperZStack.pop()
    popperZStack.push(z + POPPER_Z_GAP)
    holdingTicket = true
    applyPopperZVar()
  }

  function releaseOverlayZIndex(): void {
    if (!holdingTicket) return
    popperZStack.pop()
    holdingTicket = false
    applyPopperZVar()
  }

  watch(visible, (open, wasOpen) => {
    if (open) refreshOverlayZIndex()
    else if (wasOpen === true) releaseOverlayZIndex()
  }, { immediate: true })

  if (getCurrentScope()) {
    onScopeDispose(() => releaseOverlayZIndex())
  }

  return {
    dialogZIndex,
    backdropZIndex,
    popperClass: SUB_TABLE_DIALOG_POPPER_CLASS,
  }
}
