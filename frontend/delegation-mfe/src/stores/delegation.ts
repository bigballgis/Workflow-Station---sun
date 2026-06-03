import { ref } from 'vue'
import * as api from '@/api/delegation'

const rules = ref<api.DelegationRule[]>([])
const loading = ref(false)
const total = ref(0)

export function useDelegationStore() {
  const fetchRules = async () => {
    loading.value = true
    try {
      const res = await api.getDelegationRules()
      rules.value = res?.data?.data ?? []
      total.value = rules.value.length
    } catch {
      rules.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  const createRule = async (data: api.DelegationRuleRequest) => {
    await api.createDelegationRule(data)
    await fetchRules()
  }

  const deleteRule = async (id: number) => {
    await api.deleteDelegationRule(id)
    await fetchRules()
  }

  const toggleRuleStatus = async (id: number, enabled: boolean) => {
    if (enabled) await api.resumeDelegationRule(id)
    else await api.suspendDelegationRule(id)
    await fetchRules()
  }

  return { rules, loading, total, fetchRules, createRule, deleteRule, toggleRuleStatus }
}
