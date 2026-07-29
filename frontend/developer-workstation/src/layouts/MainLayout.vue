<template>
  <div class="main-layout">
    <!-- 左侧通高侧栏：品牌区 + 菜单 + 最近打开 + 底部折叠（与 admin-center / user-portal 同构） -->
    <AppSidebar />

    <!-- 右侧主体：红色顶栏（面包屑 + 工作区/用户）+ 内容画布 -->
    <div class="main-body">
      <header class="main-header">
        <nav class="breadcrumb">
          <router-link
            to="/function-units"
            class="crumb-home"
          >
            {{ t('functionUnit.title') }}
          </router-link>
          <template v-if="currentTitle">
            <span class="crumb-sep">/</span>
            <span class="crumb-current">{{ currentTitle }}</span>
          </template>
        </nav>
        <div class="header-right">
          <DevGroupContextBar @ready="groupContextReady = true" />
          <UserProfileDropdown />
        </div>
      </header>

      <main class="main-content">
        <!-- 按路径 key：从侧栏「最近打开」直接跳到另一个 FU 时路由参数变了但组件会被复用，
             不重建就会停在上一个 FU 的数据上。 -->
        <router-view
          v-if="groupContextReady"
          v-slot="{ Component }"
        >
          <component
            :is="Component"
            :key="route.path"
          />
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import UserProfileDropdown from '@/components/UserProfileDropdown.vue'
import DevGroupContextBar from '@/components/DevGroupContextBar.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import { getUser, getCurrentUser, saveUser, clearAuth } from '@/api/auth'
import { redirectToUnifiedLogin } from '@/utils/sso'

const { t } = useI18n()
const route = useRoute()
const groupContextReady = ref(false)

// 面包屑当前页：列表页本身就是首层，不再重复一次
const currentTitle = computed(() => {
  if (route.name === 'FunctionUnits') return ''
  const key = route.meta.titleKey
  return key ? t(key) : ''
})

// Get current user info
const currentUser = computed(() => getUser())

onMounted(async () => {
  // If user info doesn't exist, try to get from API
  if (!currentUser.value) {
    try {
      const user = await getCurrentUser()
      if (user) {
        saveUser(user)
      }
    } catch (error) {
      console.error('Failed to get current user:', error)
      // If failed, token may be invalid, clear auth info
      clearAuth()
      redirectToUnifiedLogin('developer-workstation')
    }
  }
})
</script>

<style lang="scss" scoped>
$header-height: 64px;
$primary-color: #db0011;
$primary-dark: #8b0000;

.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: var(--ws-canvas);
}

.main-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.main-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: $header-height;
  flex-shrink: 0;
  padding: 0 24px;
  background: linear-gradient(135deg, $primary-color 0%, $primary-dark 100%);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;

  .crumb-home {
    color: #fff;
    font-weight: 700;
    text-decoration: none;

    &:hover {
      opacity: 0.85;
    }
  }

  .crumb-sep {
    color: rgba(255, 255, 255, 0.65);
  }

  .crumb-current {
    color: #fff;
    font-weight: 700;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.main-content {
  flex: 1;
  min-height: 0;
  background-color: var(--ws-canvas);
  overflow-y: auto;
}
</style>
