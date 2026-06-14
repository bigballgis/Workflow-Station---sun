import { ref, computed } from 'vue'

/**
 * Task 16.3: Regenerate scope selection (MODIFY mode only).
 *
 * Manages the set of regeneration scopes a user can toggle in MODIFY mode and
 * derives the `regenerateScope` request value (undefined === ALL).
 */
export function useChatDialogScope() {
  const SCOPE_OPTIONS = ['TABLES', 'FORMS', 'ACTIONS', 'DECISIONS', 'PROCESS', 'TABLE_RELATIONS'] as const
  const selectedScopes = ref<string[]>([...SCOPE_OPTIONS])
  const showScopeSelector = ref(false)

  const scopeKeyMap: Record<string, string> = {
    TABLES: 'tables',
    FORMS: 'forms',
    ACTIONS: 'actions',
    DECISIONS: 'decisions',
    PROCESS: 'process',
    TABLE_RELATIONS: 'tableRelations'
  }

  const regenerateScope = computed(() => {
    if (selectedScopes.value.length === SCOPE_OPTIONS.length) return undefined // ALL
    return selectedScopes.value.join(',')
  })

  return {
    SCOPE_OPTIONS,
    selectedScopes,
    showScopeSelector,
    scopeKeyMap,
    regenerateScope
  }
}
