import { defineStore } from 'pinia'
import { ref } from 'vue'
import { delegationApi, type DelegationRule, type DelegationRuleRequest } from '@/api/delegation'

export const useDelegationStore = defineStore('delegation', () => {
  const rules = ref<DelegationRule[]>([])
  const loading = ref(false)
  const total = ref(0)

  const fetchRules = async (params?: { page?: number; size?: number }) => {
    loading.value = true
    try {
      const res = await delegationApi.getMyRules(params)
      rules.value = res.data.content
      total.value = res.data.totalElements
    } finally {
      loading.value = false
    }
  }

  const createRule = async (data: DelegationRuleRequest) => {
    await delegationApi.createRule(data)
    await fetchRules()
  }

  const updateRule = async (id: string, data: DelegationRuleRequest) => {
    await delegationApi.updateRule(id, data)
    await fetchRules()
  }

  const deleteRule = async (id: string) => {
    await delegationApi.deleteRule(id)
    await fetchRules()
  }

  const toggleRuleStatus = async (id: string, enabled: boolean) => {
    await delegationApi.toggleRuleStatus(id, enabled)
    await fetchRules()
  }

  return {
    rules,
    loading,
    total,
    fetchRules,
    createRule,
    updateRule,
    deleteRule,
    toggleRuleStatus
  }
})
