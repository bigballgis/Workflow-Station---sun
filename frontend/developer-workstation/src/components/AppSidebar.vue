<template>
  <aside
    class="dw-aside"
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
        class="dw-menu"
        router
      >
        <el-menu-item index="/function-units">
          <el-icon class="nav-anim nav-anim--pop"><Folder /></el-icon>
          <template #title>
            {{ t('functionUnit.title') }}
          </template>
        </el-menu-item>
      </el-menu>

      <!-- 最近打开：整条是缩小版 Launchpad 磁贴（同一套图标造型），
           收起后只剩图标列 —— 侧栏变成一排 FU 的 dock。 -->
      <section class="recent">
        <header
          v-if="!isCollapsed"
          class="recent-head"
        >
          <span class="recent-label">{{ t('sidebar.recent') }}</span>
          <button
            v-if="recent.length > 0"
            type="button"
            class="recent-clear"
            @click="clearRecent"
          >
            {{ t('sidebar.clearRecent') }}
          </button>
        </header>

        <p
          v-if="recent.length === 0 && !isCollapsed"
          class="recent-empty"
        >
          {{ t('sidebar.recentEmpty') }}
        </p>

        <el-tooltip
          v-for="fu in recent"
          :key="fu.id"
          placement="right"
          :show-after="300"
          :content="fu.name"
        >
          <button
            type="button"
            class="recent-item"
            :class="{ 'is-active': fu.id === openFunctionUnitId }"
            :aria-current="fu.id === openFunctionUnitId ? 'page' : undefined"
            @click="openFunctionUnit(fu.id)"
          >
            <span class="recent-chip">
              <IconPreview
                :icon-id="fu.iconId"
                size="small"
              />
              <span
                class="status-dot"
                :class="`status-dot--${fu.status.toLowerCase()}`"
              />
            </span>
            <span class="recent-name">{{ fu.name }}</span>
          </button>
        </el-tooltip>
      </section>
    </el-scrollbar>

    <button
      type="button"
      class="collapse-btn"
      :title="isCollapsed ? t('sidebar.expand') : t('sidebar.collapse')"
      :aria-label="isCollapsed ? t('sidebar.expand') : t('sidebar.collapse')"
      @click="toggleSidebar"
    >
      <el-icon :size="18">
        <Expand v-if="isCollapsed" />
        <Fold v-else />
      </el-icon>
    </button>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Folder, Fold, Expand } from '@element-plus/icons-vue'
import IconPreview from '@/components/icon/IconPreview.vue'
import { useSidebarState } from '@/composables/useSidebarState'
import { useRecentFunctionUnits } from '@/composables/useRecentFunctionUnits'
import { useFunctionUnitStore } from '@/stores/functionUnit'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const store = useFunctionUnitStore()

const brandMarkUrl = `${import.meta.env.BASE_URL}hermes-mark.svg`

const { isCollapsed, toggleSidebar, initSidebarState } = useSidebarState()
const { recent, recordVisit, clearRecent, syncMetadata } = useRecentFunctionUnits()

// 打开某个 FU 时导航项**不**标记为当前位置：此时「最近打开」里那一条才是更准确的所在，
// 两处同时点亮会出现两条红色定位条，读起来是两个当前位置。
const activeMenu = computed(() => (route.name === 'FunctionUnits' ? '/function-units' : ''))

/** 当前正在设计器里打开的 FU（用于把「最近打开」里对应的一条标为当前位置） */
const openFunctionUnitId = computed(() => {
  if (route.name !== 'FunctionUnitEdit') return null
  const id = Number(route.params.id)
  return Number.isFinite(id) ? id : null
})

function openFunctionUnit(id: number): void {
  if (id === openFunctionUnitId.value) return
  router.push(`/function-units/${id}`)
}

// 详情取到之后才记录：此时名称 / 图标 / 状态都是真实值，
// 且能天然跳过「id 无效、打不开」的地址。
watch(
  () => store.current,
  (fu) => {
    if (!fu || fu.id !== openFunctionUnitId.value) return
    recordVisit({ id: fu.id, name: fu.name, iconId: fu.icon?.id, status: fu.status })
  },
  { immediate: true }
)

// 列表每次加载后回填最新名称 / 图标 / 状态（改名、换图标、归档后侧栏不会停在旧值）
watch(
  () => store.list,
  (list) => {
    if (list.length > 0) syncMetadata(list)
  },
  { immediate: true }
)

onMounted(initSidebarState)
</script>

<script lang="ts">
export default {
  name: 'AppSidebar'
}
</script>

<style scoped lang="scss">
$header-height: 64px;
$aside-width: 280px; // 与 admin-center / user-portal 侧栏同宽
$aside-collapsed-width: 64px;
$primary-color: #db0011;
// 「最近打开」区内容的左起点：与上方菜单项图标框左缘同列（菜单容器 8px + EP 菜单项 20px）
$recent-inset: 20px;

.dw-aside {
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

  // 侧栏放宽到 280px 后「Developer Workstation」整行放得下 16px/0.5px 标准档
  // （与 admin-center / user-portal 同款），仍设省略号兜住更长的译名。
  .brand-name {
    min-width: 0;
    color: var(--ws-text);
    font-size: 16px;
    font-weight: 700;
    letter-spacing: 0.5px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.aside-scroll {
  flex: 1;
  min-height: 0;
}

// ==================== 导航（与 admin-center / user-portal 同一套） ====================
.dw-menu {
  border-right: none;
  padding: 8px;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #4a4a4a;
  --el-menu-active-color: #{$primary-color};
  --el-menu-hover-bg-color: rgba(219, 0, 17, 0.06);
  --el-menu-hover-text-color: #{$primary-color};

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
    color: $primary-color;
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
      background-color: $primary-color;
      border-radius: 0 3px 3px 0;
    }
  }

  // 收起态：菜单容器 8px padding 把条目压到 48px 宽，而 EP 仍按「64px 宽 + 20px 左 padding」
  // 摆图标（el-menu-item 的内容还包在绝对定位的 .el-menu-tooltip__trigger 里，自带同款 padding），
  // 图标中心整体右偏 8px —— 收起时一律 flex 居中（与 admin-center / user-portal 同款修复）。
  &.el-menu--collapse {
    :deep(.el-menu-item),
    :deep(.el-menu-item .el-menu-tooltip__trigger) {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 !important;
    }

    :deep(.el-icon) {
      margin-right: 0;
    }
  }

  // Nav icon micro-animation（与 admin-center / user-portal 同款，AP builder 风格）：
  // hover 菜单行时图标播一次性弹性缩放；完整变体集见另两端 Layout，这里只用到 pop。
  // 「最近打开」磁贴已有自己的上浮微交互，不叠加此动画。
  :deep(.el-menu-item:hover .nav-anim--pop svg) {
    animation: nav-icon-pop 0.45s ease-out;
  }

  @media (prefers-reduced-motion: reduce) {
    :deep(.el-menu-item:hover .nav-anim svg) {
      animation: none;
    }
  }
}

@keyframes nav-icon-pop {
  0% { transform: scale(1); }
  35% { transform: scale(0.8); }
  70% { transform: scale(1.15); }
  100% { transform: scale(1); }
}

// ==================== 最近打开 ====================
.recent {
  padding: 8px;
  margin-top: 4px;
  border-top: 1px solid var(--ws-line); // 收起后这条细线就是「导航 / 我的 FU」的分界
}

.recent-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 10px 12px 6px $recent-inset;
}

// 小号大写字距标签：沿用设计系统里表头的那套 utility 字体
.recent-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #8f8f8a;
}

.recent-clear {
  border: none;
  background: none;
  padding: 0;
  font-size: 11px;
  color: var(--ws-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease;

  &:hover {
    color: $primary-color;
  }
}

.recent:hover .recent-clear,
.recent-clear:focus-visible {
  opacity: 1;
}

.recent-empty {
  margin: 0;
  padding: 2px 12px 8px $recent-inset;
  font-size: 12px;
  line-height: 1.5;
  color: var(--ws-text-muted);
}

.recent-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 6px 12px 6px $recent-inset;
  margin: 2px 0;
  border: none;
  border-radius: 10px;
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover {
    background-color: #f7f8fa;

    .recent-chip {
      transform: translateY(-1px);
      box-shadow: 0 4px 10px rgba(20, 20, 20, 0.12);
    }
  }

  &:focus-visible {
    outline: 2px solid $primary-color;
    outline-offset: -2px;
  }

  &.is-active {
    background-color: rgba(219, 0, 17, 0.08);

    .recent-name {
      color: $primary-color;
      font-weight: 600;
    }

    &::before {
      content: '';
      position: absolute;
      left: -8px;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 20px;
      background-color: $primary-color;
      border-radius: 0 3px 3px 0;
    }
  }
}

// 缩小版 Launchpad 磁贴：与列表页 84px 磁贴同一套造型（渐变面 + 发丝边 + 圆角方）
.recent-chip {
  position: relative;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(145deg, #ffffff 0%, #eceef1 100%);
  border: 1px solid rgba(20, 20, 20, 0.06);
  box-shadow: 0 1px 3px rgba(20, 20, 20, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.18s ease, box-shadow 0.18s ease;

  :deep(.icon-preview) {
    width: 20px;
    height: 20px;
    background: transparent;

    svg {
      width: 100%;
      height: 100%;
    }
  }
}

// 状态点压在磁贴右上角上（与列表页磁贴同样是贴在图标上的徽标，不是飘在旁边的圆点）
.status-dot {
  position: absolute;
  top: 0;
  right: 0;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  box-shadow: 0 0 0 2px #fff;

  &--published { background: #22a35a; }
  &--draft { background: #9c9c9c; }
  &--archived { background: #d9962c; }
}

.recent-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.3;
  color: var(--ws-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

// 收起态：标签与名称退场，只留一列图标 —— dock
.dw-aside.is-collapsed {
  .recent {
    padding: 8px 0;
  }

  .recent-item {
    justify-content: center;
    padding: 6px 0;

    &::before {
      left: 0;
    }
  }

  .recent-name {
    display: none;
  }
}

// ==================== 收起按钮 ====================
.collapse-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 48px;
  flex-shrink: 0;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ws-text-secondary);
  border-top: 1px solid #e6e8eb;

  &:hover {
    color: $primary-color;
    background-color: rgba(219, 0, 17, 0.06);
  }

  &:focus-visible {
    outline: 2px solid $primary-color;
    outline-offset: -2px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dw-aside,
  .recent-item,
  .recent-chip,
  .recent-clear {
    transition: none;
  }

  .recent-item:hover .recent-chip {
    transform: none;
  }
}
</style>
