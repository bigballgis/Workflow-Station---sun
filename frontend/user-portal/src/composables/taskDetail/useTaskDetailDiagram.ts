import type { TaskDetailCtx } from './context'

export interface TaskDetailDiagramFns {
  disconnectDiagramViewportObserver: () => void
  connectDiagramViewportObserver: () => void
}

export function createTaskDetailDiagram(ctx: TaskDetailCtx): TaskDetailDiagramFns {
  const { workflowSectionRef, diagramInViewport } = ctx

  function disconnectDiagramViewportObserver() {
    ctx.diagramViewportObserver?.disconnect()
    ctx.diagramViewportObserver = null
  }

  function connectDiagramViewportObserver() {
    disconnectDiagramViewportObserver()
    const el = workflowSectionRef.value
    if (!el || diagramInViewport.value) return
    ctx.diagramViewportObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          diagramInViewport.value = true
          disconnectDiagramViewportObserver()
        }
      },
      { root: null, rootMargin: '200px 0px 0px 0px', threshold: 0 },
    )
    ctx.diagramViewportObserver.observe(el)
  }

  return {
    disconnectDiagramViewportObserver,
    connectDiagramViewportObserver,
  }
}
