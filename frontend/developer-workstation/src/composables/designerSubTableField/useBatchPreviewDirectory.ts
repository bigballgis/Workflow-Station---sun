import { reactive, ref } from 'vue'
import type { BatchAssignmentDirectory } from '@platform-shared/batch/assignmentDirectoryTypes'
import { DEMO_BU_OPTIONS, DEMO_ROLE_OPTIONS } from '@/utils/miAssignmentConfig'

/** Explicit Preview samples, not a directory fallback or real assignment eligibility. No HTTP. */
export function useBatchPreviewDirectory(t: (key: string) => string): BatchAssignmentDirectory {
  const units = DEMO_BU_OPTIONS.map((option, index) => ({ id: `preview-bu-query-${index}`, name: option.label, code: option.value }))
  const users = [1, 2].map(index => ({ id: `__demo_user_${index}`, username: `preview-user-${index}`, name: `${t('subTable.batchPreviewUser')} ${index}` }))
  const directory: BatchAssignmentDirectory = {
    error: ref(''), businessUnits: ref(units), userOptions: reactive({}), userLoading: reactive({}),
    report: cause => { directory.error.value = cause instanceof Error ? cause.message : t('common.operationFailed') },
    loadBusinessUnits: async () => {},
    requireBusinessUnitCode: id => {
      const unit = units.find(item => item.id === id)
      if (!unit) throw new Error('common.operationFailed')
      return unit.code
    },
    requireBusinessUnitId: code => {
      const unit = units.find(item => item.code === code)
      if (!unit) throw new Error('common.operationFailed')
      return unit.id
    },
    loadRoles: async () => DEMO_ROLE_OPTIONS.map(option => ({ ...option })),
    searchUsers: (key, query) => { directory.userOptions[key] = users.filter(user => user.name.toLowerCase().includes(query.toLowerCase())) },
    loadUsers: async ids => users.filter(user => ids.includes(user.id)),
  }
  return directory
}
