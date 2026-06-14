import { ref } from 'vue'
import { permissionApi, type UserBusinessUnitRole } from '@/api/permission'

/** 「我的业务单元角色」列表的加载与状态。 */
export function useMyBuRoles() {
  const loadingMyBuRoles = ref(false)
  const myBuRoles = ref<UserBusinessUnitRole[]>([])

  const loadMyBuRoles = async () => {
    loadingMyBuRoles.value = true
    try {
      const res = (await permissionApi.getMyMemberships()) as any
      const payload = res?.data ?? res
      const raw = payload?.businessUnitRoles
      if (Array.isArray(raw)) {
        myBuRoles.value = raw as UserBusinessUnitRole[]
      } else {
        myBuRoles.value = []
      }
    } catch (e) {
      console.error('Failed to load my BU roles:', e)
      myBuRoles.value = []
    } finally {
      loadingMyBuRoles.value = false
    }
  }

  return {
    loadingMyBuRoles,
    myBuRoles,
    loadMyBuRoles
  }
}
