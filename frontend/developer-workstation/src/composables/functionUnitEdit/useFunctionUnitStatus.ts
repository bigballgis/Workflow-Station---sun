import { useI18n } from 'vue-i18n'

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/** Status tag color/label helpers for the function unit header. */
export function useFunctionUnitStatus() {
  const { t } = useI18n()

  const statusTagType = (status?: string): TagType => {
    const map: Record<string, TagType> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }
    return map[status || ''] || 'info'
  }

  const statusLabel = (status?: string) => {
    const map: Record<string, string> = {
      DRAFT: t('functionUnit.draft'),
      PUBLISHED: t('functionUnit.published'),
      ARCHIVED: t('functionUnit.archived')
    }
    return map[status || ''] || status
  }

  return { statusTagType, statusLabel }
}
