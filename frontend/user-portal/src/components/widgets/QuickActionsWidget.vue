<template>
  <div class="quick-actions-widget">
    <div class="actions-grid">
      <div
        v-for="action in actions"
        :key="action.key"
        class="action-item"
        @click="handleAction(action)"
      >
        <div class="action-icon" :style="{ background: action.bgColor }">
          <el-icon :size="20" :color="action.color">
            <component :is="action.icon" />
          </el-icon>
        </div>
        <span class="action-name">{{ action.name }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { markRaw } from 'vue'
import { useRouter } from 'vue-router'
import {
  Plus,
  Document,
  Setting,
  Bell,
  User,
  Share
} from '@element-plus/icons-vue'

interface QuickAction {
  key: string
  name: string
  icon: any
  color: string
  bgColor: string
  route?: string
  action?: () => void
}

const router = useRouter()

const actions: QuickAction[] = [
  {
    key: 'newProcess',
    name: '发起流程',
    icon: markRaw(Plus),
    color: '#DB0011',
    bgColor: '#fff5f5',
    route: '/processes'
  },
  {
    key: 'myApplications',
    name: '我的申请',
    icon: markRaw(Document),
    color: '#1890ff',
    bgColor: '#e6f7ff',
    route: '/applications'
  },
  {
    key: 'delegation',
    name: '委托管理',
    icon: markRaw(Share),
    color: '#FF6600',
    bgColor: '#fff7e6',
    route: '/delegations'
  },
  {
    key: 'permissions',
    name: '权限申请',
    icon: markRaw(User),
    color: '#00A651',
    bgColor: '#f6ffed',
    route: '/permissions'
  },
  {
    key: 'notifications',
    name: '消息中心',
    icon: markRaw(Bell),
    color: '#722ed1',
    bgColor: '#f9f0ff',
    route: '/notifications'
  },
  {
    key: 'settings',
    name: '个人设置',
    icon: markRaw(Setting),
    color: '#909399',
    bgColor: '#f5f7fa',
    route: '/settings'
  }
]

const handleAction = (action: QuickAction) => {
  if (action.route) {
    router.push(action.route)
  } else if (action.action) {
    action.action()
  }
}
</script>

<style scoped lang="scss">
.quick-actions-widget {
  height: 100%;

  .actions-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;

    .action-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 12px 8px;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        background: #f5f7fa;
        transform: translateY(-2px);
      }

      .action-icon {
        width: 40px;
        height: 40px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: 8px;
      }

      .action-name {
        font-size: 12px;
        color: #606266;
        text-align: center;
      }
    }
  }
}
</style>
