import { describe, expect, it } from 'vitest'
import {
  mergeSubTablePortalViewsForRuntime,
  resolveSubTableDisplayMode,
} from '../formRendererHelpers'

describe('mergeSubTablePortalViewsForRuntime', () => {
  it('binding formBelowTable wins when canvas props omit assigneeTodo but still carry legacy tableOnly-like partials', () => {
    const binding = { assigneeTodo: 'formBelowTable', initiatorRequest: 'mirrorTodo' as const }
    /** Canvas node saved only initiator / form-source fragment — common when binding bar was edited later */
    const widget = { initiatorRequest: 'mirrorTodo' as const }

    const merged = mergeSubTablePortalViewsForRuntime(widget, binding)
    expect(resolveSubTableDisplayMode(merged, 'assigneeTodo')).toBe('formBelowTable')
  })

  it('canvas explicit assigneeTodo=tableOnly still overrides binding formBelowTable', () => {
    const binding = { assigneeTodo: 'formBelowTable', initiatorRequest: 'mirrorTodo' as const }
    const widget = { assigneeTodo: 'tableOnly' as const }

    const merged = mergeSubTablePortalViewsForRuntime(widget, binding)
    expect(resolveSubTableDisplayMode(merged, 'assigneeTodo')).toBe('tableOnly')
  })

  it('full form-create default portalViews on canvas does not suppress binding formBelowTable', () => {
    const binding = { assigneeTodo: 'formBelowTable', initiatorRequest: 'mirrorTodo' as const }
    const widget = {
      assigneeTodo: 'tableOnly' as const,
      initiatorRequest: 'mirrorTodo' as const,
      assigneeTodoFormSource: {
        type: 'subForm' as const,
        formId: null,
        linkFormColumnId: null,
      },
    }

    const merged = mergeSubTablePortalViewsForRuntime(widget, binding)
    expect(resolveSubTableDisplayMode(merged, 'assigneeTodo')).toBe('formBelowTable')
  })

  it('inherits binding linkForm column when widget form-source is absent', () => {
    const binding = {
      assigneeTodo: 'formBelowTable',
      initiatorRequest: 'mirrorTodo' as const,
      assigneeTodoFormSource: {
        type: 'linkForm' as const,
        linkFormColumnId: 42,
        formId: null,
      },
    }
    const widget = { initiatorRequest: 'mirrorTodo' as const }

    const merged = mergeSubTablePortalViewsForRuntime(widget, binding)
    expect(merged.assigneeTodoFormSource.type).toBe('linkForm')
    expect(merged.assigneeTodoFormSource.linkFormColumnId).toBe(42)
  })
})
