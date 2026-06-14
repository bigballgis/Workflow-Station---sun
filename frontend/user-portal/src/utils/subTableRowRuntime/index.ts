/**
 * Sub-table add-row orchestration (PRD S5) — guard, FK fill, PK allocate, column presentation.
 * Parity with developer-workstation Form Preview runtime.
 *
 * Barrel: re-exports the split modules so `@/utils/subTableRowRuntime` keeps every original
 * export (names + types) unchanged. FK/PK hot path — behavior identical to pre-split.
 */
export {
  type BindingFieldDefinition,
  type AllocatePrimaryKeysFn,
  type BindingLinkMode,
  toFieldFkMetas,
  filterStructuralFkMetasForBinding,
  buildRowAddContext,
  bindingForeignKeyFieldIsRowPrimaryKey,
  relationFieldsToBindingDefs,
  isFkFieldReadonly,
  isFkFieldHidden,
} from './types'

export {
  applyFkPresentationToDialogColumns,
  applyFieldDefinitionsToFormFields,
} from './columnPresentation'

export {
  repairMisassignedPrimaryKeyFromParentId,
  ensureAutoPrimaryKeysForRows,
  allocateChildRowAutoPrimaryKeys,
  ensureParentRowsForChildAdd,
} from './primaryKeyAllocation'

export {
  seedLinkChildForeignKeysFromParentRow,
  applyMiParticipantRowSeedToInitialRow,
  prepareSubTableAddRow,
  finalizeSubTableRowOnSave,
} from './rowOrchestration'
