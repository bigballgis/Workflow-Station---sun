<template>
  <div class="notifications-page">
    <div class="page-header">
      <h1>{{ t('notification.title') }}</h1>
      <el-button @click="handleMarkAllAsRead">{{ t('notification.markAllAsRead') }}</el-button>
    </div>

    <div class="portal-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane :label="`${t('notification.unread')} (${store.unreadCount})`" name="unread" />
        <el-tab-pane :label="t('notification.all')" name="all" />
        <el-tab-pane :label="t('notification.system')" name="system" />
        <el-tab-pane :label="t('notification.task')" name="task" />
        <el-tab-pane :label="t('notification.process')" name="process" />
      </el-tabs>

      <div v-loading="store.loading" class="notification-list">
        <div
          v-for="item in store.notifications"
          :key="item.id"
          :class="['notification-item', { unread: !item.isRead }]"
          @click="handleClick(item)"
        >
          <div class="notification-icon">
            <el-icon :size="24" :color="getIconColor(item.type)">
              <component :is="getIcon(item.type)" />
            </el-icon>
          </div>
          <div class="notification-content">
            <div class="notification-title">{{ item.title }}</div>
            <div class="notification-desc">{{ item.content }}</div>
            <div class="notification-time">{{ formatTime(item.createdAt) }}</div>
          </div>
          <div class="notification-actions">
            <el-button v-if="!item.isRead" type="primary" link size="small" @click.stop="handleMarkAsRead(item)">
              {{ t('notification.markAsRead') }}
            </el-button>
            <el-button type="danger" link size="small" @click.stop="handleDelete(item)">
              {{ t('notification.delete') }}
            </el-button>
          </div>
        </div>
        <el-empty v-if="!store.loading && store.notifications.length === 0" :description="t('notification.noNotifications')" />
      </div>

      <div v-if="store.total > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="store.total"
          layout="prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Document, Setting, Warning } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import type { NotificationData } from '@/api/notification'
import dayjs from 'dayjs'

const { t } = useI18n()
const router = useRouter()
const store = useNotificationStore()

const activeTab = ref('unread')
const currentPage = ref(1)
const pageSize = 20

const getQueryParams = () => {
  const params: any = { page: currentPage.value - 1, size: pageSize }
  if (activeTab.value === 'unread') {
    params.isRead = false
  } else if (activeTab.value === 'system') {
    params.type = 'SYSTEM'
  } else if (activeTab.value === 'task') {
    params.type = 'TASK'
  } else if (activeTab.value === 'process') {
    params.type = 'PROCESS'
  }
  return params
}

const loadNotifications = () => {
  store.fetchNotifications(getQueryParams())
}

const handleTabChange = () => {
  currentPage.value = 1
  loadNotifications()
}

const handlePageChange = () => {
  loadNotifications()
}

const handleClick = async (item: NotificationData) => {
  try {
    if (!item.isRead) {
      await store.markAsRead(item.id)
    }
    if (item.link) {
      router.push(item.link)
    }
  } catch (e) {
    // error already shown by request interceptor
  }
}

const handleMarkAsRead = async (item: NotificationData) => {
  try {
    await store.markAsRead(item.id)
    ElMessage.success(t('notification.markedAsRead'))
  } catch (e) {
    // error already shown by request interceptor
  }
}

const handleMarkAllAsRead = async () => {
  try {
    await store.markAllAsRead()
    loadNotifications()
    ElMessage.success(t('notification.allMarkedAsRead'))
  } catch (e) {
    // error already shown by request interceptor
  }
}

const handleDelete = async (item: NotificationData) => {
  try {
    await store.deleteNotification(item.id)
    ElMessage.success(t('notification.deleteSuccess'))
  } catch (e) {
    // error already shown by request interceptor
  }
}

const formatTime = (time: string | number[]) => {
  if (!time) return '-'
  if (Array.isArray(time)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = time
    const d = dayjs(new Date(year, month - 1, day, hour, minute, second))
    return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-'
  }
  const d = dayjs(time)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-'
}

const getIcon = (type: string) => {
  const map: Record<string, any> = {
    TASK: Document,
    PROCESS: Bell,
    SYSTEM: Setting,
    REMINDER: Warning
  }
  return map[type] || Bell
}

const getIconColor = (type: string) => {
  const map: Record<string, string> = {
    TASK: 'var(--success-green)',
    PROCESS: 'var(--warning-orange)',
    SYSTEM: 'var(--info-blue)',
    REMINDER: 'var(--error-red)'
  }
  return map[type] || 'var(--text-secondary)'
}

onMounted(() => {
  loadNotifications()
  store.fetchUnreadCount()
})
</script>

<style lang="scss" scoped>
.notifications-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  .notification-list {
    min-height: 200px;

    .notification-item {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 16px;
      border-bottom: 1px solid var(--border-color);
      cursor: pointer;
      transition: background-color 0.2s;
      
      &:hover {
        background-color: var(--background-light);
      }
      
      &.unread {
        background-color: rgba(219, 0, 17, 0.02);
        
        .notification-title {
          font-weight: 600;
        }
      }
      
      .notification-icon {
        flex-shrink: 0;
        width: 40px;
        height: 40px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: var(--background-light);
        border-radius: 50%;
      }
      
      .notification-content {
        flex: 1;
        
        .notification-title {
          font-size: 14px;
          color: var(--text-primary);
          margin-bottom: 4px;
        }
        
        .notification-desc {
          font-size: 13px;
          color: var(--text-secondary);
          margin-bottom: 8px;
        }
        
        .notification-time {
          font-size: 12px;
          color: var(--text-placeholder);
        }
      }
      
      .notification-actions {
        flex-shrink: 0;
      }
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    padding: 16px 0;
  }
}
</style>
