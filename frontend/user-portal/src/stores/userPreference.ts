import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserPreference, updateUserPreference, type UserPreference } from '@/api/preference'
import i18n from '@/i18n'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

/**
 * Account-scoped UI preferences (follows the user across browsers).
 */
export const useUserPreferenceStore = defineStore('userPreference', () => {
  const autoClaimOnOpen = ref(false)
  const loaded = ref(false)
  const saving = ref(false)
  const snapshot = ref<UserPreference | null>(null)
  let inFlight: Promise<void> | null = null

  async function load(): Promise<void> {
    if (loaded.value) {
      return
    }
    if (inFlight) {
      return inFlight
    }
    inFlight = (async () => {
      try {
        const res = (await getUserPreference()) as { data?: UserPreference }
        const data = res?.data
        snapshot.value = data ?? null
        autoClaimOnOpen.value = Boolean(data?.autoClaimOnOpen)
        loaded.value = data != null
      } catch {
        // FALLBACK(ux): preference GET failed; keep default off and retry on next open
        autoClaimOnOpen.value = false
        snapshot.value = null
      } finally {
        inFlight = null
      }
    })()
    return inFlight
  }

  async function setAutoClaimOnOpen(value: boolean): Promise<void> {
    await load()
    const previous = autoClaimOnOpen.value
    if (snapshot.value == null) {
      ElMessage.error(i18n.global.t('api.requestFailed'))
      return
    }
    autoClaimOnOpen.value = value
    saving.value = true
    try {
      await updateUserPreference(fullPreferencePut(snapshot.value, value))
      snapshot.value = { ...snapshot.value, autoClaimOnOpen: value }
      loaded.value = true
    } catch (error) {
      autoClaimOnOpen.value = previous
      ElMessage.error(resolveUserFacingHttpMessage(error, (key) => i18n.global.t(key)))
    } finally {
      saving.value = false
    }
  }

  return { autoClaimOnOpen, loaded, saving, load, setAutoClaimOnOpen }
})

/** PUT the full row so Java field defaults on omitted JSON keys cannot wipe other settings. */
function fullPreferencePut(current: UserPreference, autoClaim: boolean): Partial<UserPreference> {
  return {
    theme: current.theme,
    themeColor: current.themeColor,
    fontSize: current.fontSize,
    layoutDensity: current.layoutDensity,
    language: current.language,
    timezone: current.timezone,
    dateFormat: current.dateFormat,
    pageSize: current.pageSize,
    autoClaimOnOpen: autoClaim,
  }
}
