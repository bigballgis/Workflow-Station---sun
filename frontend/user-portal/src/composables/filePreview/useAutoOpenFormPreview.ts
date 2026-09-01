import { onBeforeUnmount, watch } from 'vue'
import { debounce } from 'lodash-es'
import { useUserPreferenceStore } from '@/stores/userPreference'
import {
  openFilePreview,
  useFilePreviewState,
  type FilePreviewItem,
} from './useFilePreview'

const SETTLE_MS = 400
const GIVE_UP_MS = 3000

/** Primitive watch key so in-place upload URL writes retrigger without deep-watching formData. */
export function autoOpenPreviewWatchKey(
  enabled: boolean,
  processInstanceId: string | undefined,
  files: FilePreviewItem[],
): string {
  return `${enabled ? '1' : '0'}\0${processInstanceId ?? ''}\0${files.map((file) => file.url).join('\0')}`
}

/**
 * When the account switch is on, open the first previewable form file once per
 * process instance after form data settles. Default off — callers must opt in.
 */
export function useAutoOpenFormPreview(deps: {
  enabled: () => boolean
  processInstanceId: () => string | undefined
  collect: () => FilePreviewItem[]
}) {
  const preferenceStore = useUserPreferenceStore()
  let attemptedKey = ''
  let giveUpAt = 0

  const tryOpen = debounce(async () => {
    if (!deps.enabled()) return
    const processInstanceId = deps.processInstanceId()
    if (!processInstanceId) return
    await preferenceStore.load()
    if (!preferenceStore.autoPreviewOnOpen) return
    if (attemptedKey === processInstanceId) return
    const files = deps.collect()
    if (files.length > 0) {
      attemptedKey = processInstanceId
      if (!useFilePreviewState().visible) {
        openFilePreview({
          url: files[0].url,
          name: files[0].name,
          cannotDownload: files[0].cannotDownload,
          items: files,
          index: 0,
        })
      }
      return
    }
    if (Date.now() >= giveUpAt) attemptedKey = processInstanceId
  }, SETTLE_MS)

  watch(
    () => deps.processInstanceId(),
    (pi) => {
      attemptedKey = ''
      giveUpAt = pi ? Date.now() + GIVE_UP_MS : 0
    },
    { immediate: true },
  )

  watch(
    () => autoOpenPreviewWatchKey(deps.enabled(), deps.processInstanceId(), deps.collect()),
    () => {
      void tryOpen()
    },
    { immediate: true },
  )

  onBeforeUnmount(() => {
    tryOpen.cancel()
  })
}
