<template>
  <div class="notifications-page">
    <div class="page-header">
      <h1>{{ t('notification.title') }}</h1>
      <el-button @click="markAllAsRead">{{ t('notification.markAllAsRead') }}</el-button>
    </div>

    <div class="portal-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="`${t('notification.unread')} (${unreadCount})`" name="unread" />
        <el-tab-pane :label="t('notification.all')" name="all" />
        <el-tab-pane :label="t('notification.system')" name="system" />
        <el-tab-pane :label="t('notification.task')" name="task" />
        <el-tab-pane :label="t('notification.process')" name="process" />
      </el-tabs>

      <div class="notification-list">
        <div
          v-for="item in filteredNotifications"
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
            <div class="notification-time">{{ item.time }}</div>
          </div>
          <div class="notification-actions">
            <el-button v-if="!item.isRead" type="primary" link size="small" @click.stop="markAsRead(item)">
              {{ t('notification.markAsRead') }}
            </el-button>
            <el-button type="danger" link size="small" @click.stop="deleteNotification(item)">
              {{ t('notification.delete') }}
            </el-button>
          </div>
        </div>
        <el-empty v-if="filteredNotifications.length === 0" :description="t('notification.noNotifications')" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Bell, Document, Setting, Warning } from '@element-plus/icons-vue'

const { t } = useI18n()

const activeTab = ref('unread')

const notifications = ref([
  { id: 1, type: 'task', title: '新任务分配', content: '您有一个新的请假申请需要审批', time: '5分钟前', isRead: false },
  { id: 2, type: 'process', title: '流程审批通过', content: '您的报销申请已通过审批', time: '1小时前', isRead: false },
  { id: 3, type: 'system', title: '系统维护通知', content: '系统将于今晚22:00进行维护', time: '2小时前', isRead: true },
  { id: 4, type: 'task', title: '任务即将到期', content: '采购申请审批任务将于明天到期', time: '3小时前', isRead: true }
])

const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length)

const filteredNotifications = computed(() => {
  if (activeTab.value === 'unread') {
    return notifications.value.filter(n => !n.isRead)
  }
  if (activeTab.value === 'all') {
    return notifications.value
  }
  return notifications.value.filter(n => n.type === activeTab.value)
})

const getIcon = (type: string) => {
  const map: Record<string, any> = {
    task: Document,
    process: Bell,
    system: Setting,
    warning: Warning
  }
  return map[type] || Bell
}

const getIconColor = (type: string) => {
  const map: Record<string, string> = {
    task: 'var(--success-green)',
    process: 'var(--warning-orange)',
    system: 'var(--info-blue)',
    warning: 'var(--error-red)'
  }
  return map[type] || 'var(--text-secondary)'
}

const handleClick = (item: any) => {
  if (!item.isRead) {
    item.isRead = true
  }
}

const markAsRead = (item: any) => {
  item.isRead = true
  ElMessage.success('已标记为已读')
}

const markAllAsRead = () => {
  notifications.value.forEach(n => n.isRead = true)
  ElMessage.success('已全部标记为已读')
}

const deleteNotification = (item: any) => {
  const index = notifications.value.findIndex(n => n.id === item.id)
  if (index > -1) {
    notifications.value.splice(index, 1)
    ElMessage.success('删除成功')
  }
}
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
}
</style>
