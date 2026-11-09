import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { adminCenterApi } from '@/api/adminCenter'
import { mergeActionRoleOptions, type ActionRoleOption } from '@/utils/actionRoleOptions'

/** Business-role catalog for Action Design Allowed Roles. */
export function useActionRoleCatalog(selectedCodes: () => readonly string[]) {
  const { t } = useI18n()
  const catalog = ref<ActionRoleOption[]>([])
  const loadingRoles = ref(false)

  const roleOptions = computed(() => mergeActionRoleOptions(catalog.value, selectedCodes()))

  async function loadRoleCatalog(): Promise<void> {
    loadingRoles.value = true
    try {
      const bounded = await adminCenterApi.getBuBoundedRoles()
      const seen = new Set<string>()
      const next: ActionRoleOption[] = []
      for (const role of bounded) {
        if (!role.code || seen.has(role.code) || role.type !== 'BU_BOUNDED') continue
        seen.add(role.code)
        next.push({ code: role.code, name: role.name || role.code })
      }
      catalog.value = next
    } catch {
      // FALLBACK(ux): a failed catalog load leaves the picker empty; already saved codes still show.
      ElMessage.warning(t('action.allowedRolesCatalogLoadFailed'))
      catalog.value = []
    } finally {
      loadingRoles.value = false
    }
  }

  onMounted(() => {
    void loadRoleCatalog()
  })

  return { roleOptions, loadingRoles }
}
