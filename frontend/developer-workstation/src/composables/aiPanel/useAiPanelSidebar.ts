import { ref } from 'vue'

/**
 * Composable for tracking the app sidebar width so the docked AI panel can
 * align its left edge. Reads the actual sidebar width from the DOM and watches
 * for changes (collapse/expand) via MutationObserver + transitionend.
 */
export function useAiPanelSidebar() {
  // Sidebar state — read actual sidebar width from DOM and watch for changes
  const sidebarWidth = ref('240px')
  let sidebarObserver: MutationObserver | null = null

  function updateSidebarWidth() {
    const aside = document.querySelector('.sidebar') as HTMLElement
    if (aside) {
      sidebarWidth.value = aside.offsetWidth + 'px'
    } else {
      try {
        const collapsed = localStorage.getItem('sidebar-collapsed') === 'true'
        sidebarWidth.value = collapsed ? '64px' : '240px'
      } catch {
        sidebarWidth.value = '240px'
      }
    }
  }

  function startWatchingSidebar() {
    const aside = document.querySelector('.sidebar') as HTMLElement
    if (!aside) return
    sidebarObserver = new MutationObserver(() => {
      sidebarWidth.value = aside.offsetWidth + 'px'
    })
    sidebarObserver.observe(aside, { attributes: true, attributeFilter: ['style'] })
    // Also listen for transition end (el-aside animates width)
    aside.addEventListener('transitionend', updateSidebarWidth)
  }

  function stopWatchingSidebar() {
    if (sidebarObserver) {
      sidebarObserver.disconnect()
      sidebarObserver = null
    }
    const aside = document.querySelector('.sidebar') as HTMLElement
    if (aside) {
      aside.removeEventListener('transitionend', updateSidebarWidth)
    }
  }

  return {
    sidebarWidth,
    updateSidebarWidth,
    startWatchingSidebar,
    stopWatchingSidebar
  }
}
