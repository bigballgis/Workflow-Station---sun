import { defineStore } from 'pinia'
import { ref } from 'vue'
import { delegationApi, type DelegationRule, type DelegationRuleRequest } from '@/api/delegation'

export const useDelegationStore = defineStore('delegation', () => {
  const rules = ref<DelegationRule[]>([])
  const loading = ref(false)
  const total = ref(0)

  const fetchRules = async () => {
    loading.value = true
    try {
      const res = await delegationApi.getDelegationRules()
      rules.value = res.data
      total.value = res.data.length
    } finally {
      loading.value = false
    }
  }

  const createRule = async (data: DelegationRuleRequest) => {
    await delegationApi.createDelegationRule(data)
    await fetchRules()
  }

  const updateRule = async (id: number, data: DelegationRuleRequest) => {
    await delegationApi.updateDelegationRule(id, data)
    await fetchRules()
  }

  const deleteRule = async (id: number) => {
    await delegationApi.deleteDelegationRule(id)
    await fetchRules()
  }

  const toggleRuleStatus = async (id: number, enabled: boolean) => {
    if (enabled) {
      await delegationApi.resumeDelegationRule(id)
    } else {
      await delegationApi.suspendDelegationRule(id)
    }
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
