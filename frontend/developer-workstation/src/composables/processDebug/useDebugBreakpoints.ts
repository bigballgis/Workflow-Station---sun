import { computed } from 'vue'
import type { Breakpoint, DebugState } from './useDebugState'

const BREAKPOINT_NODE_TYPES = new Set([
  'startEvent',
  'endEvent',
  'userTask',
  'serviceTask',
  'scriptTask',
  'businessRuleTask',
  'exclusiveGateway',
  'parallelGateway',
  'inclusiveGateway',
  'subProcess',
  'callActivity'
])

interface UseDebugBreakpointsOptions {
  state: DebugState
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * 断点管理：候选节点、增删、启用切换与命中判断。
 * 行为与原 SFC 完全一致。
 */
export function useDebugBreakpoints(options: UseDebugBreakpointsOptions) {
  const { state, t } = options
  const {
    processNodes,
    breakpoints,
    breakpointCandidate,
    simulationSteps,
    addLog,
  } = state

  const breakpointCandidates = computed(() =>
    processNodes.value.filter(node => BREAKPOINT_NODE_TYPES.has(node.type))
  )

  function hasBreakpoint(nodeId: string): boolean {
    return breakpoints.value.some(bp => bp.nodeId === nodeId)
  }

  function isBreakpointHit(index: number): boolean {
    const step = simulationSteps.value[index]
    if (!step) return false
    return breakpoints.value.some(bp => bp.enabled && bp.nodeId === step.nodeId)
  }

  function handleBreakpointToggle(bp: Breakpoint) {
    addLog('info', t('debug.breakpointToggled', {
      name: bp.nodeName,
      state: bp.enabled ? t('debug.enabled') : t('debug.disabled')
    }), 'NODE_ENTER')
  }

  function addBreakpoint(nodeId: string) {
    const node = processNodes.value.find(item => item.id === nodeId)
    if (!node || hasBreakpoint(nodeId)) return

    breakpoints.value.push({
      nodeId: node.id,
      nodeName: node.name,
      nodeType: node.type,
      enabled: true
    })
    breakpointCandidate.value = null
    addLog('info', t('debug.breakpointAdded', { name: node.name }), 'NODE_ENTER')
  }

  function removeBreakpoint(nodeId: string) {
    const index = breakpoints.value.findIndex(bp => bp.nodeId === nodeId)
    if (index > -1) {
      breakpoints.value.splice(index, 1)
    }
  }

  return {
    breakpointCandidates,
    hasBreakpoint,
    isBreakpointHit,
    handleBreakpointToggle,
    addBreakpoint,
    removeBreakpoint,
  }
}
