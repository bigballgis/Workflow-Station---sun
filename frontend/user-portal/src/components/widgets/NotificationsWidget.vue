<template>
  <div class="notifications-widget">
    <div class="notifications-list" v-if="notifications.length > 0">
      <div
        v-for="notification in notifications"
        :key="notification.id"
        class="notification-item"
        :class="{ unread: !notification.read }"
        @click="handleClick(notification)"
      >
        <div class="notification-icon" :class="notification.type">
          <el-icon>
            <component :is="getIcon(notification.type)" />
          </el-icon>
        </div>
        <div class="notification-content">
          <div class="notification-title">{{ notification.title }}</div>
          <div class="notification-time">{{ formatTime(notification.createdAt) }}</div>
        </div>
        <el-badge v-if="!notification.read" is-dot class="unread-dot" />
      </div>
    </div>
    <el-empty v-else :description="$t('notification.noNotifications')" :image-size="60" />
    
    <div class="widget-footer" v-if="notifications.length > 0">
      <el-link type="primary" @click="goToNotifications">
        {{ $t('common.more') }}
      </el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Bell, Document, Warning, InfoFilled } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'

interface Notification {
  id: string
  type: 'task' | 'process' | 'system' | 'warning'
  title: string
  content?: string
  read: boolean
  createdAt: string
  link?: string
}

const router = useRouter()
const { t } = useI18n()
const notificationStore = useNotificationStore()

const notifications = ref<Notification[]>([])

const getIcon = (type: string) => {
  const icons: Record<string, any> = {
    task: markRaw(Document),
    process: markRaw(Bell),
    system: markRaw(InfoFilled),
    warning: markRaw(Warning)
  }
  return icons[type] || Bell
}

const formatTime = (time: string) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return t('widget.justNow')
  if (minutes < 60) return t('widget.minutesAgo', { n: minutes })
  if (hours < 24) return t('widget.hoursAgo', { n: hours })
  if (days < 7) return t('widget.daysAgo', { n: days })
  return date.toLocaleDateString()
}

const handleClick = async (notification: Notification) => {
  if (!notification.read) {
    await notificationStore.markAsRead(notification.id)
    notification.read = true
  }
  if (notification.link) {
    router.push(notification.link)
  }
}

const goToNotifications = () => {
  router.push('/notifications')
}

onMounted(async () => {
  await notificationStore.fetchNotifications()
  notifications.value = notificationStore.notifications.slice(0, 5)
})
</script>

<style scoped lang="scss">
.notifications-widget {
  height: 100%;
  display: flex;
  flex-direction: column;

  .notifications-list {
    flex: 1;
    overflow: auto;

    .notification-item {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 10px 0;
      border-bottom: 1px solid #f0f0f0;
      cursor: pointer;
      position: relative;

      &:hover {
        background: #fafafa;
      }

      &.unread {
        .notification-title {
          font-weight: 600;
        }
      }

      .notification-icon {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;

        &.task {
          background: #e6f7ff;
          color: #1890ff;
        }

        &.process {
          background: #f6ffed;
          color: #00A651;
        }

        &.system {
          background: #f5f7fa;
          color: #909399;
        }

        &.warning {
          background: #fff7e6;
          color: #FF6600;
        }
      }

      .notification-content {
        flex: 1;
        min-width: 0;

        .notification-title {
          font-size: 13px;
          color: #303133;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .notification-time {
          font-size: 11px;
          color: #909399;
          margin-top: 2px;
        }
      }

      .unread-dot {
        position: absolute;
        right: 0;
        top: 50%;
        transform: translateY(-50%);
      }
    }
  }

  .widget-footer {
    padding-top: 10px;
    text-align: center;
    border-top: 1px solid #f0f0f0;
  }
}
</style>
