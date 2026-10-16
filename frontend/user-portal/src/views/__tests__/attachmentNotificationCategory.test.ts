import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import Notifications from '../notifications/index.vue'
import en from '@/i18n/locales/en'
import zhCN from '@/i18n/locales/zh-CN'
import zhTW from '@/i18n/locales/zh-TW'
import { notificationApi, type NotificationData } from '@/api/notification'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/api/notification', () => ({ notificationApi: {
  getNotifications: vi.fn(), getUnreadCount: vi.fn(), markAsRead: vi.fn(),
  markAllAsRead: vi.fn(), deleteNotification: vi.fn()
} }))
vi.mock('@/composables/useNotificationWebSocket', () => ({
  useNotificationWebSocket: () => ({ connected: { value: false }, connect: vi.fn(), disconnect: vi.fn() })
}))

const note: NotificationData = { id: 7, type: 'ATTACHMENT_UPDATE', title: 'Attachments updated',
  content: 'Credit Card Reimbursement (REQ-001)\n2 attachment(s) added to Transaction Notes · TX-B.\nUploaded from: Credit Card Transaction',
  link: '/record-note-notifications/submission-qa', isRead: false, createdAt: '2026-10-15T12:00:00' }
let wrapper: ReturnType<typeof mount>
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(notificationApi.getNotifications).mockImplementation(async params => ({ data: {
    content: params?.type === 'ATTACHMENT_UPDATE' ? [{ ...note }] : [], totalElements: params?.type === 'ATTACHMENT_UPDATE' ? 1 : 0,
    page: 0, size: 20, totalPages: 1, hasNext: false, hasPrevious: false
  } }))
  vi.mocked(notificationApi.getUnreadCount).mockResolvedValue({ data: 1 })
  vi.mocked(notificationApi.markAsRead).mockResolvedValue({} as Awaited<ReturnType<typeof notificationApi.markAsRead>>)
})
afterEach(() => wrapper?.unmount())

describe('Attachment Updates notification category', () => {
  it.each([['en', 'Attachment Updates'], ['zh-CN', '附件更新'], ['zh-TW', '附件更新']])(
    'filters independently and opens the exact submission in %s', async (locale, label) => {
      wrapper = mount(Notifications, { global: { plugins: [createPinia(), ElementPlus,
        createI18n({ legacy: false, locale, messages: { en, 'zh-CN': zhCN, 'zh-TW': zhTW } })] } })
      await flushPromises()
      const help = wrapper.find('[data-testid="attachment-updates-guide-link"]')
      expect(help.attributes('href')).toContain('/help/form-ctl-record-note#open')
      expect(help.attributes('aria-label')).toBe({ en: 'Read the attachment update notification guide',
        'zh-CN': '查看附件更新通知指南', 'zh-TW': '查看附件更新通知指南' }[locale])
      const tab = wrapper.findAll('[role="tab"]').find(item => item.text() === label)!
      expect(tab.exists()).toBe(true)
      await tab.trigger('click')
      await flushPromises()
      expect(notificationApi.getNotifications).toHaveBeenLastCalledWith({ page: 0, size: 20, type: 'ATTACHMENT_UPDATE' })
      expect(wrapper.find('.notification-title').text()).toBe('Attachments updated')
      expect(wrapper.find('.notification-desc').text()).toBe(note.content)
      await wrapper.find('.notification-item').trigger('click')
      await flushPromises()
      expect(notificationApi.markAsRead).toHaveBeenCalledWith(7)
      expect(push).toHaveBeenCalledWith(note.link)
      await wrapper.find('#tab-task').trigger('click')
      await flushPromises()
      expect(notificationApi.getNotifications).toHaveBeenLastCalledWith({ page: 0, size: 20, type: 'TASK' })
      expect(wrapper.findAll('.notification-item')).toHaveLength(0)
    })
})
