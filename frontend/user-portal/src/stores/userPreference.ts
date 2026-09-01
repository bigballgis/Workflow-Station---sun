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
  const autoPreviewOnOpen = ref(false)
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
        autoPreviewOnOpen.value = Boolean(data?.autoPreviewOnOpen)
        loaded.value = data != null
      } catch {
        // FALLBACK(ux): preference GET failed; keep default off and retry on next open
        autoClaimOnOpen.value = false
        autoPreviewOnOpen.value = false
        snapshot.value = null
      } finally {
        inFlight = null
      }
    })()
    return inFlight
  }

  async function patchBooleans(patch: {
    autoClaimOnOpen?: boolean
    autoPreviewOnOpen?: boolean
  }): Promise<void> {
    await load()
    const previousClaim = autoClaimOnOpen.value
    const previousPreview = autoPreviewOnOpen.value
    if (snapshot.value == null) {
      ElMessage.error(i18n.global.t('api.requestFailed'))
      return
    }
    if (patch.autoClaimOnOpen != null) autoClaimOnOpen.value = patch.autoClaimOnOpen
    if (patch.autoPreviewOnOpen != null) autoPreviewOnOpen.value = patch.autoPreviewOnOpen
    saving.value = true
    try {
      const next = { ...snapshot.value, ...patch }
      await updateUserPreference(fullPreferencePut(next))
      snapshot.value = next
      loaded.value = true
    } catch (error) {
      autoClaimOnOpen.value = previousClaim
      autoPreviewOnOpen.value = previousPreview
      ElMessage.error(resolveUserFacingHttpMessage(error, (key) => i18n.global.t(key)))
    } finally {
      saving.value = false
    }
  }

  function setAutoClaimOnOpen(value: boolean): Promise<void> {
    return patchBooleans({ autoClaimOnOpen: value })
  }

  function setAutoPreviewOnOpen(value: boolean): Promise<void> {
    return patchBooleans({ autoPreviewOnOpen: value })
  }

  return {
    autoClaimOnOpen,
    autoPreviewOnOpen,
    loaded,
    saving,
    load,
    setAutoClaimOnOpen,
    setAutoPreviewOnOpen,
  }
})

/** PUT the full row so Java field defaults on omitted JSON keys cannot wipe other settings. */
function fullPreferencePut(current: UserPreference): Partial<UserPreference> {
  return {
    theme: current.theme,
    themeColor: current.themeColor,
    fontSize: current.fontSize,
    layoutDensity: current.layoutDensity,
    language: current.language,
    timezone: current.timezone,
    dateFormat: current.dateFormat,
    pageSize: current.pageSize,
    autoClaimOnOpen: Boolean(current.autoClaimOnOpen),
    autoPreviewOnOpen: Boolean(current.autoPreviewOnOpen),
  }
}
