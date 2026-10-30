import { computed, onScopeDispose, ref, watch } from 'vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { assignmentExclusionEnabled, emptyAssignmentExclusion, parseAssignmentExclusion } from '@/utils/assignmentExclusion'
import { assignmentNodeBuSupported, assignmentProcessOwner, readAssignmentExclusion, writeAssignmentExclusion } from '@/utils/assignmentExclusionBpmn'

interface Options { readonly element: BpmnElement; readonly modeler: BpmnModeler; readonly disabled: boolean }
interface Registry { getAll(): BpmnElement[] }

export function useAssignmentExclusion(props: Options) {
  const draft = ref(emptyAssignmentExclusion())
  const invalid = ref(false)
  const nodes = ref<Array<{ id: string; name: string }>>([])
  const modelRevision = ref(0)
  let loadedNode = ''
  const enabled = computed(() => assignmentExclusionEnabled(draft.value))
  const missingNodes = computed(() => draft.value.selectedNodeKeys.filter(id => !nodes.value.some(node => node.id === id)))
  const canUseNodeBu = computed(() => {
    void modelRevision.value
    return assignmentNodeBuSupported(props.element)
  })

  function refresh(): void {
    loadedNode = props.element.id
    modelRevision.value++
    try {
      draft.value = parseAssignmentExclusion(readAssignmentExclusion(props.element))
      const owner = assignmentProcessOwner(props.element)
      if (!owner) throw new Error('Missing BPMN process owner')
      nodes.value = (props.modeler.get('elementRegistry') as Registry).getAll()
        .filter(node => node.type === 'bpmn:UserTask' && assignmentProcessOwner(node) === owner)
        .map(node => ({ id: node.id, name: node.businessObject.name || node.id }))
      invalid.value = false
    } catch {
      invalid.value = true
    }
  }
  function commit(): void {
    if (props.disabled || invalid.value) return
    if (loadedNode !== props.element.id) { refresh(); return }
    writeAssignmentExclusion(props.modeler, props.element, draft.value)
  }
  function reset(): void {
    if (props.disabled) return
    writeAssignmentExclusion(props.modeler, props.element, emptyAssignmentExclusion())
    refresh()
  }
  watch(() => props.element, refresh, { immediate: true })
  watch(() => props.modeler, (modeler, previous) => {
    previous?.off('commandStack.changed', refresh)
    modeler.on('commandStack.changed', refresh)
    refresh()
  }, { immediate: true })
  onScopeDispose(() => props.modeler.off('commandStack.changed', refresh))
  return { draft, invalid, nodes, enabled, missingNodes, canUseNodeBu, commit, reset }
}
