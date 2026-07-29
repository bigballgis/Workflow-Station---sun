import { ref } from 'vue'

const STORAGE_KEY = 'sidebar-collapsed'

/**
 * Composable for managing sidebar collapse state
 * Extracted for testability
 *
 * The collapsed/expanded widths live in AppSidebar's stylesheet (248px / 64px, shared with
 * admin-center and user-portal) so the transition is pure CSS — this owns only the state.
 */
export function useSidebarState() {
  const isCollapsed = ref(false)

  /**
   * Toggle sidebar collapse state
   */
  function toggleSidebar(): void {
    isCollapsed.value = !isCollapsed.value
    try {
      localStorage.setItem(STORAGE_KEY, String(isCollapsed.value))
    } catch {
      // localStorage not available, ignore
    }
  }

  /**
   * Initialize sidebar state from localStorage
   */
  function initSidebarState(): void {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      isCollapsed.value = stored === 'true'
    } catch {
      // localStorage not available, use default
      isCollapsed.value = false
    }
  }

  return {
    isCollapsed,
    toggleSidebar,
    initSidebarState
  }
}

// Export pure functions for property-based testing
export function toggleState(currentState: boolean): boolean {
  return !currentState
}

export function parseStoredState(stored: string | null): boolean {
  return stored === 'true'
}

export function serializeState(state: boolean): string {
  return String(state)
}
