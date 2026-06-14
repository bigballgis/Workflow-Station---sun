import type { useI18n } from 'vue-i18n'
import type { Router } from 'vue-router'
import type { ApplicationDetailState } from './useApplicationDetailState'
import type { ApplicationDetailColumnsFns } from './useApplicationDetailColumns'
import type { ApplicationDetailFormSchemaFns } from './useApplicationDetailFormSchema'
import type { ApplicationDetailLinkBindingsFns } from './useApplicationDetailLinkBindings'
import type { ApplicationDetailMiHydrationFns } from './useApplicationDetailMiHydration'
import type { ApplicationDetailMiScopeFns } from './useApplicationDetailMiScope'
import type { ApplicationDetailSubTaskDialogFns } from './useApplicationDetailSubTaskDialog'
import type { ApplicationDetailBpmnCurrentFormFns } from './useApplicationDetailBpmnCurrentForm'
import type { ApplicationDetailNodeFormMapFns } from './useApplicationDetailNodeFormMap'
import type { ApplicationDetailBottomBindingsFns } from './useApplicationDetailBottomBindings'
import type { ApplicationDetailPreviousFormsFns } from './useApplicationDetailPreviousForms'
import type { ApplicationDetailHistoryFns } from './useApplicationDetailHistory'
import type { ApplicationDetailSecondaryFns } from './useApplicationDetailSecondary'
import type { ApplicationDetailLoadersFns } from './useApplicationDetailLoaders'
import type { ApplicationDetailActionsFns } from './useApplicationDetailActions'
import type { ApplicationDetailDiagramParserFns } from './useApplicationDetailDiagramParser'

/**
 * Late-bound externals provided by the SFC: i18n + router only. The BPMN
 * diagram parse (`scheduleParseApplicationBpmnDiagram` / `parseBpmnXml`) now
 * lives in `useApplicationDetailDiagramParser` and is registered onto the
 * shared context like the other composable function groups.
 */
export interface ApplicationDetailExternals {
  t: ReturnType<typeof useI18n>['t']
  router: Router
}

/**
 * Cross-module function registry. Functions are registered onto the shared
 * context by the SFC during setup; all cross-module calls go through `ctx.*`
 * at invocation time (post-setup), mirroring the hoisting semantics the
 * original single-file functions relied on.
 */
export type ApplicationDetailFns =
  ApplicationDetailColumnsFns &
  ApplicationDetailFormSchemaFns &
  ApplicationDetailLinkBindingsFns &
  ApplicationDetailMiHydrationFns &
  ApplicationDetailMiScopeFns &
  ApplicationDetailSubTaskDialogFns &
  ApplicationDetailBpmnCurrentFormFns &
  ApplicationDetailNodeFormMapFns &
  ApplicationDetailBottomBindingsFns &
  ApplicationDetailPreviousFormsFns &
  ApplicationDetailHistoryFns &
  ApplicationDetailSecondaryFns &
  ApplicationDetailLoadersFns &
  ApplicationDetailActionsFns &
  ApplicationDetailDiagramParserFns

export type ApplicationDetailCtx = ApplicationDetailState & ApplicationDetailExternals & ApplicationDetailFns
