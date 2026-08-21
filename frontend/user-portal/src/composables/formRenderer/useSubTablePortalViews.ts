import {
  isMiDashboardSubTableBinding,
} from '../tasks/shared'
import type { FormField } from '../../components/formRendererHelpers'
import type { SubTableBinding } from './useSubTableBindings'

/**
 * Sub-table presentation inside a rendered form.
 *
 * <p>How a sub-table looks is now part of the form design itself: To Do and My
 * Requests each own a form, so each lays its sub-tables out directly. The former
 * `subTablePortalViews` axis — which described both scenes inside a single shared
 * form — has been removed along with its `tableOnly` / `formBelowTable` /
 * `summaryWithLinkFormModal` modes, and the inline form-below-table strip itself
 * has since been removed too; rows are viewed/edited via the Link Form modal.
 */
interface PortalViewsDeps {
  resolveBinding: (id?: number) => SubTableBinding | undefined
}

export function useSubTablePortalViews(deps: PortalViewsDeps) {
  const { resolveBinding } = deps

  /** Compact cells: no inline lookup / user-snapshot detail block inside a cell. */
  function subTableCompactLookupCells(field: FormField): boolean {
    return field.compactCells === true
  }

  /**
   * Per-row Status column for a multi-instance collection, matching the runtime MI
   * dashboard. A property of the binding, not of who is looking at it.
   */
  function subTableShowTaskStatus(field: FormField): boolean {
    const binding = resolveBinding(field._bindingId)
    return !!binding && isMiDashboardSubTableBinding(binding)
  }

  return {
    subTableCompactLookupCells,
    subTableShowTaskStatus,
  }
}
