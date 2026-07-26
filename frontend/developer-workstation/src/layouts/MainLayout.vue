<template>
  <div class="main-layout">
    <!-- 左侧通高侧栏：品牌区 + 菜单 + 底部折叠（与 admin-center / user-portal 同构） -->
    <aside
      class="main-aside"
      :class="{ 'is-collapsed': isCollapsed }"
    >
      <div class="brand">
        <img
          class="brand-mark"
          :src="brandMarkUrl"
          alt=""
        >
        <span class="brand-name">{{ t('app.name') }}</span>
      </div>
      <el-scrollbar class="aside-scroll">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :collapse-transition="false"
          class="main-menu"
          router
        >
          <el-menu-item index="/function-units">
            <el-icon><Box /></el-icon>
            <template #title>
              {{ t('functionUnit.title') }}
            </template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
      <div
        class="collapse-btn"
        @click="toggleCollapse"
      >
        <el-icon :size="18">
          <Fold v-if="!isCollapsed" />
          <Expand v-else />
        </el-icon>
      </div>
    </aside>

    <!-- 右侧主体：红色顶栏（面包屑 + 开发组上下文/用户）+ 内容画布 -->
    <div class="main-body">
      <header class="header">
        <nav class="breadcrumb">
          <router-link
            to="/function-units"
            class="crumb-home"
          >
            {{ t('functionUnit.title') }}
          </router-link>
          <template v-if="currentTitle && route.path !== '/function-units'">
            <span class="crumb-sep">/</span>
            <span class="crumb-current">{{ currentTitle }}</span>
          </template>
        </nav>
        <div class="header-right">
          <DevGroupContextBar />
          <UserProfileDropdown />
        </div>
      </header>
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Box, Fold, Expand } from '@element-plus/icons-vue'
import UserProfileDropdown from '@/components/UserProfileDropdown.vue'
import DevGroupContextBar from '@/components/DevGroupContextBar.vue'
import { getUser, getCurrentUser, saveUser, clearAuth } from '@/api/auth'
import { redirectToUnifiedLogin } from '@/utils/sso'

const { t } = useI18n()
const route = useRoute()

const brandMarkUrl = `${import.meta.env.BASE_URL}hermes-mark.svg`

const isCollapsed = ref(false)

// 列表与设计器详情页共用同一菜单项高亮
const activeMenu = computed(() =>
  route.path.startsWith('/function-units') ? '/function-units' : route.path
)

// 顶栏面包屑：当前页标题取路由 meta.titleKey（与 admin-center / user-portal 同构）
const currentTitle = computed(() => {
  const key = route.meta.titleKey as string | undefined
  return key ? t(key) : ''
})

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

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
$header-gradient-start: #DB0011;
$header-gradient-end: #8B0000;
$header-height: 64px;
$aside-width: 248px;
$aside-collapsed-width: 64px;

.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: var(--ws-canvas);
}

// ==================== 白色通高侧栏 ====================
.main-aside {
  display: flex;
  flex-direction: column;
  width: $aside-width;
  flex-shrink: 0;
  background: #ffffff;
  border-right: 1px solid #e6e8eb;
  transition: width 0.3s;
  overflow: hidden;

  &.is-collapsed {
    width: $aside-collapsed-width;

    .brand {
      justify-content: center;
      padding: 0;
    }

    .brand-name {
      display: none;
    }
  }
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: $header-height;
  padding: 0 20px;
  flex-shrink: 0;
  border-bottom: 1px solid #f0f0f0;

  .brand-mark {
    display: block;
    width: 28px;
    height: 28px;
    flex-shrink: 0;
  }

  .brand-name {
    color: var(--ws-text);
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.3px;
    white-space: nowrap;
  }
}

.aside-scroll {
  flex: 1;
  min-height: 0;
}

.main-menu {
  border-right: none;
  padding: 8px;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #4a4a4a;
  --el-menu-active-color: #{$header-gradient-start};
  --el-menu-hover-bg-color: rgba(219, 0, 17, 0.06);
  --el-menu-hover-text-color: #{$header-gradient-start};

  &:not(.el-menu--collapse) {
    width: 100%;
  }

  :deep(.el-menu-item) {
    height: 44px;
    line-height: 44px;
    margin: 2px 0;
    border-radius: 10px;
  }

  :deep(.el-menu-item.is-active) {
    font-weight: 600;
    background-color: rgba(219, 0, 17, 0.1);

    &::before {
      content: '';
      position: absolute;
      left: -8px; // 贴到侧栏最左缘（抵消菜单容器 padding）
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 22px;
      background-color: $header-gradient-start;
      border-radius: 0 3px 3px 0;
    }
  }
}

.collapse-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 48px;
  flex-shrink: 0;
  cursor: pointer;
  color: var(--ws-text-secondary);
  border-top: 1px solid #e6e8eb;

  &:hover {
    color: $header-gradient-start;
    background-color: rgba(219, 0, 17, 0.06);
  }
}

// ==================== 右侧主体 ====================
.main-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, $header-gradient-start 0%, $header-gradient-end 100%);
  color: white;
  padding: 0 24px;
  height: $header-height;
  flex-shrink: 0;
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
  gap: 20px;
}

.main-content {
  background-color: var(--ws-canvas);
  padding: 0;
  overflow: auto;
  flex: 1;
  min-height: 0;
}
</style>
