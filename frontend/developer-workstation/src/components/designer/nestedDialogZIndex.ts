/** Stack sub-table add/edit dialog above Form Preview overlay. */
const PREVIEW_NESTED_DIALOG_Z = 10000

export interface NestedDialogZStack {
  backdropZ: number
  dialogZ: number
  popperZ: number
}

export function resolveNestedDialogStack(): NestedDialogZStack {
  let maxZ = 2000
  document.querySelectorAll(
    '.el-overlay, .el-overlay-dialog, .sub-table-nested-modal-overlay, .el-dialog__wrapper',
  ).forEach((el) => {
    const z = Number.parseInt(window.getComputedStyle(el).zIndex || '0', 10)
    if (Number.isFinite(z) && z > maxZ) maxZ = z
  })
  const dialogZ = Math.max(PREVIEW_NESTED_DIALOG_Z, maxZ + 200)
  return {
    backdropZ: dialogZ - 1,
    dialogZ,
    popperZ: dialogZ + 50,
  }
}

export function resolveNestedDialogZIndex(): number {
  return resolveNestedDialogStack().dialogZ
}
