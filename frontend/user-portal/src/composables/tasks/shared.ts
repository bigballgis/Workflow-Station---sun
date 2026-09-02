/**
 * Shared utility functions for task detail composables.
 * Pure helpers — no reactive state, no Vue/API dependencies.
 *
 * Backward-compatible re-export barrel: implementations live in the focused sibling modules
 * (see {@code subTable*.ts} / {@code mi*.ts} / {@code shared*SubTable*.ts}). Import sites keep
 * using {@code '@/composables/tasks/shared'} unchanged.
 */

export {
  normalizeSubTableName,
  stripLinkFormDesignerTableLabel,
  subTableBindingMatches,
  cloneSubTableRows,
  stripNestedSubTablesFromRows,
  cloneSubTableBindings,
} from './subTableCore'

export { mergeSubTableRowsByRowId, rowResolvesDesignerPrimaryKey } from './subTableRowMerge'

export {
  isSubTableRowMetaField,
  stripSubTableRowMetaFields,
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  isFileOnlySubTableBinding,
  isSharedAttachmentFileBinding,
  isSubTableMiDashboardRow,
  resolveMiDashboardFieldNames,
  type MiDashboardFieldNames,
} from './subTableBindingKinds'

export {
  mergeSubTableSlicesForRelationTableId,
  collectSubTableSliceRowsForRelationTableId,
  collectAllNestedSlicesForBindingDeep,
  mergeAllSlicesForSharedProcessSubTableBinding,
  syncMiLinkChildEditedRowsIntoSiblingSlices,
} from './subTableSliceMerge'

export {
  resolveSubTableRowsForBinding,
  mergeAllSubTableSlicesFromVariables,
  resolveSubTablePrimaryKeyFields,
  getSavedSubTableRows,
  coerceSubTablesVariableToMap,
  collectSubTableSliceArraysDeep,
} from './subTableSliceResolve'

export {
  dropSubsumedSubTableRows,
  normalizeSubTableRowsForBinding,
} from './subTableRowNormalize'

export {
  filterRowsForMiParticipantSubTableBinding,
  filterRowsForSharedProcessSubTableBinding,
  finalizeSharedProcessSubTableBindingRows,
  type SharedProcessSubTableFilterContext,
} from './sharedProcessSubTableFilters'

export {
  applySharedAttachmentFinalizeAndMaterialize,
  collectForeignSubTableRowIdsFromVariables,
  type SharedAttachmentBindingLike,
} from './sharedAttachmentSubTable'

export {
  filterRowsForMiCollectionSubTableBinding,
  finalizeMiCollectionSubTableBindingRows,
  mergeMiCollectionSubTableRows,
  resolveMiCollectionPrimaryKeyFields,
  shouldSyncStaleSiblingSubTableSlice,
  subTableVariablesIncludeMiRows,
  buildMiCollectionSliceKeySet,
} from './miCollectionSubTable'

export {
  miLinkChildRowBusinessFieldRank,
  resolveMiChildStructuralParentFk,
  rowIsSelfOwnedByStructuralFk,
  repairMisassignedLinkChildStructuralFk,
  linkChildRowIsForeignParticipantPlaceholder,
  stripForeignParticipantIdIdwFromLinkChildRow,
  scoreMiLinkChildRowQuality,
  miParentRowAlignsWithChildRow,
  miLinkChildRowBelongsToParticipant,
  rowMatchesMiExpansionId,
} from './miLinkChildIdentity'

export {
  backfillMiLinkChildPrimaryKeysFromVariables,
  collapseMiLinkChildRowsToOnePerParticipant,
  collapseSubTableRowsPreferFilled,
  pickMiLinkChildRowsForParent,
  findSubTableRowByMiExpansionId,
  findMiIsolatedParentRow,
  scopeMiLinkChildRowsForParentRow,
  scopeLinkChildRowsToMiHostRow,
  hostRowIsMiParticipant,
} from './miLinkChildRows'

export {
  scrubMiCorruptLinkChildRowsForParent,
  flattenNestedSubTableRowsIntoPayload,
} from './miLinkChildScrub'

export {
  buildBindingIdToRelationTableIdMap,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
} from './subTableVariableHydration'

export {
  collectNestedSlicesForBindingFromSubTablesWalk,
  pullNestedRowsForBindingFromParentRows,
  collectNestedChildRowsFromPeerBindings,
  hydrateChildSubTablesFromParentsNestedRows,
} from './subTableNestedRows'

export { enrichChildBindingRowsFromParentsNestedSubTables } from './subTableNestedEnrich'

export {
  expansionKeyMatchesParticipantRow,
  rowMatchesSubTablePrimaryKey,
  bindingMatchesMiSubTableName,
  findBindingForMiSubTableName,
  resolveMiSubProcessScopeFromBpmn,
  filterBindingsToMiParticipantRow,
  resolveViewerParticipantRowIdFromCollectionBinding,
  extractParticipantRowIdFromVariables,
  type MiSubProcessScopeConfig,
  type SubTableBindingLike,
} from './miSubProcessScope'
