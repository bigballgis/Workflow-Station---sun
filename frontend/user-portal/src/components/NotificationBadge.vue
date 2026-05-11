<template>
  <div
    class="notification-badge"
    @click="goToNotifications"
  >
    <el-badge
      :value="badgeValue"
      :hidden="store.unreadCount === 0"
      :max="99"
    >
      <el-icon
        :size="20"
        color="white"
      >
        <Bell />
      </el-icon>
    </el-badge>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const store = useNotificationStore()

const badgeValue = computed(() => {
  if (store.unreadCount > 99) return '99+'
  return store.unreadCount
})

let cleanup: (() => void) | null = null

onMounted(() => {
  store.fetchUnreadCount()
  cleanup = store.initWebSocket()
})

onUnmounted(() => {
  cleanup?.()
})

const goToNotifications = () => {
  router.push('/notifications')
}
</script>

<style lang="scss" scoped>
.notification-badge {
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 4px 8px;
  
  &:hover {
    opacity: 0.8;
  }
}
</style>
