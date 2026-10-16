import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNotificationStore } from '../notification'
import { notificationApi, type NotificationData } from '@/api/notification'
const { receive } = vi.hoisted(() => ({ receive: { callback: vi.fn<(data: unknown) => void>() as (data: unknown) => void } }))
vi.mock('@/api/notification', () => ({ notificationApi: { getNotifications: vi.fn(), getUnreadCount: vi.fn(), markAsRead: vi.fn(), markAllAsRead: vi.fn(), deleteNotification: vi.fn() } }))
vi.mock('@/composables/useNotificationWebSocket', () => ({ useNotificationWebSocket: (callback: (data: unknown) => void) => { receive.callback=callback; return { connected: { value: false }, connect: vi.fn(), disconnect: vi.fn() } } }))
const note: NotificationData={id:51,type:'ATTACHMENT_UPDATE',title:'Attachments updated',content:'QA',isRead:false,createdAt:'2026-10-16T08:00:00'}
const page=(content: NotificationData[])=>({data:{content,totalElements:content.length,page:0,size:20,totalPages:1,hasNext:false,hasPrevious:false}})
beforeEach(()=>{setActivePinia(createPinia());vi.clearAllMocks()})
describe('Record Note plan: category race and realtime pagination',()=>{
  it('old rejected query cannot clear the successful newer category',async()=>{
    let rejectOld!: (reason: Error)=>void
    vi.mocked(notificationApi.getNotifications).mockImplementationOnce(()=>new Promise((_resolve,reject)=>{rejectOld=reject}))
      .mockResolvedValueOnce(page([note]))
    const store=useNotificationStore();const old=store.fetchNotifications({type:'TASK'})
    await store.fetchNotifications({type:'ATTACHMENT_UPDATE'});rejectOld(new Error('old query failed'));await old
    expect(store.notifications).toEqual([note]);expect(store.total).toBe(1);expect(store.loading).toBe(false)
  })
  it('second page keeps its rows while a matching push updates total and unread',async()=>{
    vi.mocked(notificationApi.getNotifications).mockResolvedValue(page([{...note,id:30,isRead:true}]))
    const store=useNotificationStore();await store.fetchNotifications({type:'ATTACHMENT_UPDATE',page:1,size:20})
    receive.callback(note);expect(store.notifications.map(n=>n.id)).toEqual([30]);expect(store.total).toBe(2);expect(store.unreadCount).toBe(1)
  })
  it('read-only query excludes new unread messages while preserving global unread count',async()=>{
    vi.mocked(notificationApi.getNotifications).mockResolvedValue(page([]))
    const store=useNotificationStore();await store.fetchNotifications({type:'ATTACHMENT_UPDATE',isRead:true})
    receive.callback(note);expect(store.notifications).toHaveLength(0);expect(store.total).toBe(0);expect(store.unreadCount).toBe(1)
  })
})
