// ---------------------------------------------------------------------------
// FieldRenderer — department tree-select (Task 6.4, Req 27)
// Behaviour copied verbatim from FieldRenderer.vue. Exposes fetchDepartmentTree
// (invoked from the orchestrator's onMounted).
// ---------------------------------------------------------------------------
import { ref, computed, inject } from 'vue'
import type { Ref } from 'vue'
import api from '@/api/request'
import type { FieldRendererProps } from './types'

interface DepartmentNode {
  id: string
  name: string
  children?: DepartmentNode[]
}

export function useFieldDepartment(props: FieldRendererProps) {
  const departmentTreeData = ref<DepartmentNode[]>([])
  const departmentLoading = ref(false)

  // Use injected shared cache from FormRenderer if available (Req 27)
  const sharedDepartmentData = inject<Ref<DepartmentNode[]> | undefined>('departmentTreeData')
  const sharedDepartmentLoading = inject<Ref<boolean> | undefined>('departmentTreeLoading')

  /** Recursively find a node by id to resolve display name */
  function findDepartmentName(
    nodes: DepartmentNode[],
    id: string,
  ): string | undefined {
    for (const node of nodes) {
      if (node.id === id) return node.name
      if (node.children) {
        const found = findDepartmentName(node.children, id)
        if (found) return found
      }
    }
    return undefined
  }

  const departmentDisplayName = computed(() => {
    if (!props.modelValue || departmentTreeData.value.length === 0) return ''
    return findDepartmentName(departmentTreeData.value, props.modelValue) ?? ''
  })

  async function fetchDepartmentTree() {
    // Use shared cache from FormRenderer if available (Req 27)
    if (sharedDepartmentData?.value && sharedDepartmentData.value.length > 0) {
      departmentTreeData.value = sharedDepartmentData.value
      return
    }
    if (departmentTreeData.value.length > 0) return // already cached locally
    departmentLoading.value = true
    if (sharedDepartmentLoading) sharedDepartmentLoading.value = true
    try {
      const res = await api.get('/api/portal/departments/tree')
      const data = res.data?.data ?? res.data ?? []
      departmentTreeData.value = data
      // Write back to shared cache
      if (sharedDepartmentData) sharedDepartmentData.value = data
    } catch (err) {
      console.warn('[FieldRenderer] Department API error:', err)
    } finally {
      departmentLoading.value = false
      if (sharedDepartmentLoading) sharedDepartmentLoading.value = false
    }
  }

  return {
    departmentTreeData,
    departmentLoading,
    departmentDisplayName,
    fetchDepartmentTree,
  }
}
