<template>
  <div class="portal-layout">
    <!-- 左侧通高侧栏：品牌区 + 菜单 + 底部折叠（与 admin-center 同构） -->
    <aside
      class="portal-aside"
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
          :router="true"
          class="portal-menu"
        >
          <el-menu-item
            v-if="showFullPortal"
            index="/dashboard"
          >
            <el-icon class="nav-anim nav-anim--bounce"><HomeFilled /></el-icon>
            <template #title>
              {{ t('menu.dashboard') }}
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal && hasBiDashboards"
            index="/bi-dashboard"
          >
            <el-icon class="nav-anim nav-anim--rise"><DataAnalysis /></el-icon>
            <template #title>
              BI Dashboard
            </template>
          </el-menu-item>
          <!-- Task 区：认领池 + 待办 + 已处理任务 -->
          <li
            v-if="showFullPortal"
            class="menu-section-label"
          >
            {{ t('menu.sectionTask') }}
          </li>
          <el-menu-item
            v-if="showFullPortal"
            index="/tasks/to-claim"
            class="menu-item-tasks-to-claim"
          >
            <el-icon class="nav-anim nav-anim--wobble"><Pointer /></el-icon>
            <template #title>
              {{ t('menu.tasksToClaim') }}
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal"
            index="/tasks"
            class="menu-item-tasks"
          >
            <el-icon class="nav-anim nav-anim--wobble"><List /></el-icon>
            <template #title>
              <span class="task-menu-title-with-badge">
                <span class="task-menu-title-text">{{ t('menu.tasks') }}</span>
                <el-badge
                  :value="pendingTaskCount"
                  :max="99"
                  :hidden="pendingTaskCount === 0"
                  type="danger"
                  class="task-menu-badge-text"
                />
              </span>
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal"
            index="/tasks/completed"
          >
            <el-icon class="nav-anim nav-anim--rise"><List /></el-icon>
            <template #title>
              {{ t('menu.completedTasks') }}
            </template>
          </el-menu-item>

          <!-- Request 区：新建申请 + 我的请求 + 全部申请（原 Audit） -->
          <li
            v-if="showFullPortal"
            class="menu-section-label"
          >
            {{ t('menu.sectionRequest') }}
          </li>
          <el-menu-item
            v-if="showFullPortal"
            index="/processes"
          >
            <el-icon class="nav-anim nav-anim--pop"><Document /></el-icon>
            <template #title>
              {{ t('menu.processes') }}
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal"
            index="/my-applications"
          >
            <el-icon class="nav-anim nav-anim--bounce"><Document /></el-icon>
            <template #title>
              {{ t('menu.myApplications') }}
            </template>
          </el-menu-item>

          <!-- Audit 区：全部申请（原 Audit，仅在有权限的功能单元时出现） -->
          <!-- Rendered only once a grant is known, so users without one never see
               this appear and then vanish. -->
          <template v-if="showFullPortal && auditFunctionUnits.length > 0">
            <li class="menu-section-label">
              {{ t('menu.sectionAudit') }}
            </li>
            <el-sub-menu index="audit-group">
              <template #title>
                <el-icon class="nav-anim nav-anim--wobble"><Document /></el-icon>
                <span>{{ t('menu.audit') }}</span>
              </template>
              <el-menu-item
                v-for="fu in auditFunctionUnits"
                :key="fu.functionUnitCode"
                :index="`/audit/${fu.functionUnitCode}`"
              >
                {{ fu.functionUnitName }}
              </el-menu-item>
            </el-sub-menu>
          </template>

          <!-- Data 区：关联表 + 视图 -->
          <li
            v-if="showFullPortal"
            class="menu-section-label"
          >
            {{ t('menu.sectionData') }}
          </li>
          <el-sub-menu
            v-if="showFullPortal"
            index="relation-tables-group"
          >
            <template #title>
              <el-icon class="nav-anim nav-anim--blink"><Grid /></el-icon>
              <span>{{ t('menu.relationTables') }}</span>
            </template>
            <el-menu-item index="/relation-tables">
              {{ t('menu.allFunctionUnits') }}
            </el-menu-item>
            <el-menu-item
              v-for="fu in relationTableFunctionUnits"
              :key="fu.functionUnitCode"
              :index="`/relation-tables/${fu.functionUnitCode}`"
            >
              {{ fu.functionUnitName }}
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu
            v-if="showFullPortal"
            index="views-group"
          >
            <template #title>
              <el-icon class="nav-anim nav-anim--pop"><Grid /></el-icon>
              <span>{{ t('menu.views') }}</span>
            </template>
            <el-menu-item
              v-for="fu in viewFunctionUnits"
              :key="fu.functionUnitCode"
              :index="`/views/${fu.functionUnitCode}`"
            >
              <FunctionUnitMenuIcon :icon-svg="fu.iconSvg" />
              <span>{{ fu.functionUnitName }}</span>
            </el-menu-item>
            <el-menu-item
              v-if="!viewFuLoading && viewFunctionUnits.length === 0"
              index="/views"
              disabled
            >
              {{ t('mainTableView.noPublishedFu') }}
            </el-menu-item>
          </el-sub-menu>

          <!-- Setup 区：委托管理 + 权限审批 + 用户档案设置（放到菜单最后） -->
          <li class="menu-section-label">
            {{ t('menu.sectionSetup') }}
          </li>
          <el-menu-item
            v-if="showFullPortal"
            index="/delegations"
          >
            <el-icon class="nav-anim nav-anim--nudge"><Share /></el-icon>
            <template #title>
              {{ t('menu.delegations') }}
            </template>
          </el-menu-item>
          <el-menu-item
            index="/permissions"
            class="menu-item-permissions"
          >
            <el-icon class="nav-anim nav-anim--blink"><Share /></el-icon>
            <template #title>
              <span class="perm-menu-title-with-badge">
                <span class="perm-menu-title-text">{{ t('menu.permissions') }}</span>
                <el-badge
                  :value="pendingApprovalCount"
                  :max="99"
                  :hidden="pendingApprovalCount === 0"
                  type="danger"
                  class="perm-menu-badge-text"
                />
              </span>
            </template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
      <div
        class="collapse-btn"
        @click="toggleCollapse"
      >
        <el-icon :size="20">
          <Fold v-if="!isCollapsed" />
          <Expand v-else />
        </el-icon>
      </div>
    </aside>

    <!-- 右侧主体：红色顶栏（面包屑 + 通知/工作台/用户）+ 内容画布 -->
    <div class="portal-body">
      <header class="portal-header">
        <nav class="breadcrumb">
          <router-link
            to="/dashboard"
            class="crumb-home"
          >
            {{ t('menu.dashboard') }}
          </router-link>
          <template v-if="currentTitle && route.path !== '/dashboard'">
            <span class="crumb-sep">/</span>
            <span class="crumb-current">{{ currentTitle }}</span>
          </template>
        </nav>
        <div class="header-right">
          <NotificationBadge />
          <WorkspaceContextBar />
          <UserProfileDropdown />
        </div>
      </header>

      <SelfServiceBanner
        :portal-access-mode="portalAccessMode"
        :workspace-context-count="workspaceContextCount"
      />

      <main class="portal-content">
        <router-view v-slot="{ Component }">
          <transition
            name="fade"
            mode="out-in"
          >
            <keep-alive :include="cachedViews">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>
    <FilePreviewDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  HomeFilled, List, Document, Share,
  Fold, Expand, DataAnalysis, Grid, Pointer
} from '@element-plus/icons-vue'
import SelfServiceBanner from '@/components/SelfServiceBanner.vue'
import WorkspaceContextBar from '@/components/WorkspaceContextBar.vue'
import {
  applyWorkspaceAwarePortalAccess,
  getCurrentUser,
  getStoredUser,
  listWorkspaceContexts,
  reconcilePortalWorkspaceSession,
  saveUser,
  USER_ID_KEY
} from '@/api/auth'
import UserProfileDropdown from '@/components/UserProfileDropdown.vue'
import NotificationBadge from '@/components/NotificationBadge.vue'
import { biDashboardApi } from '@/api/biDashboard'
import { usePendingApprovalStore } from '@/stores/pendingApproval'
import { usePendingTaskStore } from '@/stores/pendingTask'
import { mainTableViewApi, type FunctionUnitViewMenuItem } from '@/api/mainTableView'
import { processApi, type AuditFunctionUnit } from '@/api/process'
import { relationTableApi } from '@/api/relationTable'
import FunctionUnitMenuIcon from '@/components/mainTableView/FunctionUnitMenuIcon.vue'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const pendingApprovalStore = usePendingApprovalStore()
const { count: pendingApprovalCount } = storeToRefs(pendingApprovalStore)
const pendingTaskStore = usePendingTaskStore()
const { count: pendingTaskCount } = storeToRefs(pendingTaskStore)

const brandMarkUrl = `${import.meta.env.BASE_URL}hermes-mark.svg`

const isCollapsed = ref(false)
const cachedViews = ref(['Dashboard', 'Tasks', 'MyApplications'])
const hasBiDashboards = ref(false)
const viewFunctionUnits = ref<FunctionUnitViewMenuItem[]>([])
const viewFuLoading = ref(false)
const auditFunctionUnits = ref<AuditFunctionUnit[]>([])
const relationTableFunctionUnits = ref<{ functionUnitCode: string; functionUnitName: string }[]>([])

const activeMenu = computed(() => route.path)

// 顶栏面包屑：当前页标题取路由 meta.titleKey（与 admin-center 同构）；
// ApplicationDetail 被 My Requests 和 All Requests(Audit) 共用同一路由，
// 靠 ?from=audit 区分入口，否则面包屑永远显示「My Requests」。
const currentTitle = computed(() => {
  if (route.name === 'ApplicationDetail' && route.query.from === 'audit') {
    return t('menu.audit')
  }
  const key = route.meta.titleKey as string | undefined
  return key ? t(key) : ''
})

/** 与 localStorage 解耦：路由守卫 saveUser 后 computed(getStoredUser) 不会重算，需 ref + /me 显式同步 */
const portalAccessMode = ref<string | undefined>(getStoredUser()?.portalAccessMode)
/** null=未拉取；与 /workspace-contexts 一致，用于横幅：有 UBR 时不应仅因 portalAccessMode 滞后仍显示「无工作台」 */
const workspaceContextCount = ref<number | null>(null)
const showFullPortal = computed(
  () => portalAccessMode.value !== 'PERMISSION_SELF_SERVICE_ONLY'
)

// Check if user has BI dashboards assigned
const checkBiDashboards = async () => {
  try {
    const storedUser = getStoredUser()
    const userId = storedUser?.userId || localStorage.getItem(USER_ID_KEY)
    if (userId) {
      const dashboards = await biDashboardApi.getUserDashboards(
        userId,
        storedUser?.activeBusinessUnitId
      )
      hasBiDashboards.value = Array.isArray(dashboards) && dashboards.length > 0
    }
  } catch (e) {
    console.error('Failed to check BI dashboards:', e)
    hasBiDashboards.value = false
  }
}

async function syncPortalAccessFromServer() {
  await reconcilePortalWorkspaceSession()
  try {
    const contexts = await listWorkspaceContexts()
    workspaceContextCount.value = Array.isArray(contexts) ? contexts.length : 0
  } catch {
    workspaceContextCount.value = 0
  }
  void pendingApprovalStore.fetchPendingCount()
  // 待办列表、首页概览会各自用一次 query/overview 更新角标，避免 layout 再打满 queryTasks。
  if (route.path !== '/tasks' && route.path !== '/dashboard') {
    void pendingTaskStore.fetchPendingCount()
  }
  try {
    const u = await getCurrentUser()
    const hasCtx = (workspaceContextCount.value ?? 0) > 0
    const merged = applyWorkspaceAwarePortalAccess(u, hasCtx)
    saveUser(merged)
    localStorage.setItem(USER_ID_KEY, merged.userId)
    portalAccessMode.value = merged.portalAccessMode
  } catch {
    portalAccessMode.value = getStoredUser()?.portalAccessMode
  }
}

async function loadViewFunctionUnits() {
  viewFuLoading.value = true
  try {
    const res = await mainTableViewApi.listFunctionUnits()
    viewFunctionUnits.value = res.data || []
    if (route.path === '/views' && viewFunctionUnits.value.length) {
      await router.replace(`/views/${viewFunctionUnits.value[0].functionUnitCode}`)
    }
  } catch {
    viewFunctionUnits.value = []
  } finally {
    viewFuLoading.value = false
  }
}

async function loadAuditFunctionUnits() {
  try {
    const res = await processApi.getAuditFunctionUnits()
    auditFunctionUnits.value = res.data || []
  } catch {
    // A failure here silently removes the audit entry, which looks identical to
    // having no grant — say so instead of leaving reviewers guessing.
    auditFunctionUnits.value = []
    ElMessage.error(t('menu.auditLoadFailed'))
  }
}

// Built-in system tables (e.g. the read-only User table) have no Function Unit of their own;
// the sidebar files them under a fixed "Common" entry using this synthetic code — kept in sync
// with the same constant in relation-tables/index.vue (COMMON_FU_CODE) and its table-name set.
const COMMON_FU_CODE = '__common__'
const COMMON_TABLE_NAMES = new Set(['sys_users'])

/** Distinct Function Units among the user's visible Relation Tables, for the nav sub-menu. */
async function loadRelationTableFunctionUnits() {
  try {
    const res = await relationTableApi.getVisibleTables()
    const tables = res.data || []
    const byCode = new Map<string, string>()
    let hasCommon = false
    for (const t of tables) {
      if (COMMON_TABLE_NAMES.has(t.tableName)) {
        hasCommon = true
      } else if (t.functionUnitCode) {
        byCode.set(t.functionUnitCode, t.functionUnitName || t.functionUnitCode)
      }
    }
    const groups = [...byCode.entries()]
      .map(([functionUnitCode, functionUnitName]) => ({ functionUnitCode, functionUnitName }))
      .sort((a, b) => a.functionUnitName.localeCompare(b.functionUnitName))
    relationTableFunctionUnits.value = hasCommon
      ? [{ functionUnitCode: COMMON_FU_CODE, functionUnitName: t('menu.commonTables') }, ...groups]
      : groups
  } catch {
    relationTableFunctionUnits.value = []
  }
}

onMounted(() => {
  void (async () => {
    await syncPortalAccessFromServer()
    if (showFullPortal.value) {
      // Parallel: a third serial round-trip would delay first paint of the menu.
      await Promise.all([checkBiDashboards(), loadViewFunctionUnits(), loadAuditFunctionUnits(), loadRelationTableFunctionUnits()])
    }
  })()
})

watch(
  () => route.path,
  () => {
    void pendingApprovalStore.fetchPendingCount()
  }
)

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}
</script>

<style lang="scss" scoped>
$aside-width: 280px; // 与 admin-center / developer-workstation 侧栏同宽（admin 长菜单名不压箭头的最小整档）
$aside-collapsed-width: 64px;

.portal-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: var(--ws-canvas);
}

// ==================== 右侧主体 ====================
.portal-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.portal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, var(--hsbc-red) 0%, #8B0000 100%);
  color: white;
  padding: 0 24px;
  height: var(--header-height);
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
  }
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

.portal-content {
  flex: 1;
  min-height: 0;
  min-width: 0;
  background-color: var(--background-light);
  padding: 20px;
  overflow-y: auto;
}

// ==================== 白色通高侧栏 ====================
.portal-aside {
  display: flex;
  flex-direction: column;
  width: $aside-width;
  flex-shrink: 0;
  background: white;
  border-right: 1px solid var(--border-color);
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

  .brand {
    display: flex;
    align-items: center;
    gap: 12px;
    height: var(--header-height);
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
      font-size: 16px;
      font-weight: 700;
      letter-spacing: 0.5px;
      white-space: nowrap;
    }
  }

  .aside-scroll {
    flex: 1;
    min-height: 0;
  }

  .portal-menu {
    flex: 1;
    border-right: none;
    padding: 8px;

    /* 与 admin-center 一致：圆角菜单条目 + 红色激活左条 */
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      height: 44px;
      line-height: 44px;
      margin: 2px 0;
      border-radius: 10px;
    }

    :deep(.el-menu-item.is-active) {
      font-weight: 600;
      background-color: rgba(219, 0, 17, 0.1);
      color: var(--hsbc-red);

      &::before {
        content: '';
        position: absolute;
        left: -8px; // 贴到侧栏最左缘（抵消菜单容器 padding）
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 22px;
        background-color: var(--hsbc-red);
        border-radius: 0 3px 3px 0;
      }
    }

    /* el-menu-item 全局有 * { vertical-align: bottom }，会把徽标压到偏下；此处拉回垂直居中 */
    .menu-item-tasks .task-menu-title-with-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      min-width: 0;

      &,
      :deep(*) {
        vertical-align: middle !important;
      }
    }
    .menu-item-tasks .task-menu-title-text {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .menu-item-permissions .perm-menu-title-with-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      min-width: 0;
    }
    .menu-item-permissions .perm-menu-title-text {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    // 纯文字分区标题：不可点击/不可折叠，与 admin-center 的 group label 同构；
    // 中性灰 + 细分割线：与正文菜单项的品牌红（激活态/badge）区分开，避免两种红混在一起显乱。
    .menu-section-label {
      position: relative;
      height: auto;
      line-height: 1;
      padding: 12px 16px 6px;
      margin: 8px 0 0;
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.8px;
      color: #A8ABB2;
      text-transform: uppercase;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      user-select: none;
      border-top: 1px solid #F0F0F2;

      &:first-child {
        margin-top: 0;
        padding-top: 4px;
        border-top: none;
      }
    }

    // 收起态：分区标题占位没有意义，隐藏
    &.el-menu--collapse .menu-section-label {
      display: none;
    }

    // 收起态：菜单容器 8px padding 把条目压到 48px 宽，而 EP 仍按「64px 宽 + 20px 左 padding」
    // 摆图标（el-menu-item 的内容还包在绝对定位的 .el-menu-tooltip__trigger 里，自带同款 padding），
    // 图标中心整体右偏 8px —— 收起时一律 flex 居中（badge 包装层随触发层一起居中）。
    &.el-menu--collapse {
      :deep(.el-menu-item),
      :deep(.el-sub-menu__title),
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

    // Nav icon micro-animations（与 admin-center AdminLayout 同款，AP builder 风格）：
    // hover 菜单行时图标播一次性弹性动画，按图标语义分动作；关键帧首尾均为原始形态。
    :deep(.el-menu-item:hover .nav-anim--wobble svg),
    :deep(.el-sub-menu__title:hover .nav-anim--wobble svg) {
      animation: nav-icon-wobble 0.55s ease-in-out;
    }

    :deep(.el-menu-item:hover .nav-anim--pop svg),
    :deep(.el-sub-menu__title:hover .nav-anim--pop svg) {
      animation: nav-icon-pop 0.45s ease-out;
    }

    :deep(.el-menu-item:hover .nav-anim--rise svg),
    :deep(.el-sub-menu__title:hover .nav-anim--rise svg) {
      transform-origin: 50% 85%;
      animation: nav-icon-rise 0.5s ease-out;
    }

    // blink：与 rise 同一组关键帧，但以中心为原点，对"眼睛"图标呈眨眼效果
    :deep(.el-menu-item:hover .nav-anim--blink svg),
    :deep(.el-sub-menu__title:hover .nav-anim--blink svg) {
      transform-origin: 50% 50%;
      animation: nav-icon-rise 0.5s ease-out;
    }

    :deep(.el-menu-item:hover .nav-anim--bounce svg),
    :deep(.el-sub-menu__title:hover .nav-anim--bounce svg) {
      animation: nav-icon-bounce 0.5s ease-out;
    }

    :deep(.el-menu-item:hover .nav-anim--nudge svg),
    :deep(.el-sub-menu__title:hover .nav-anim--nudge svg) {
      animation: nav-icon-nudge 0.5s ease-in-out;
    }

    @media (prefers-reduced-motion: reduce) {
      :deep(.el-menu-item:hover .nav-anim svg),
      :deep(.el-sub-menu__title:hover .nav-anim svg) {
        animation: none;
      }
    }
  }

  .collapse-btn {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 48px;
    cursor: pointer;
    border-top: 1px solid var(--border-color);

    &:hover {
      background-color: var(--background-light);
    }
  }
}

@keyframes nav-icon-wobble {
  0% { transform: rotate(0deg); }
  30% { transform: rotate(-14deg); }
  60% { transform: rotate(10deg); }
  80% { transform: rotate(-4deg); }
  100% { transform: rotate(0deg); }
}

@keyframes nav-icon-pop {
  0% { transform: scale(1); }
  35% { transform: scale(0.8); }
  70% { transform: scale(1.15); }
  100% { transform: scale(1); }
}

@keyframes nav-icon-rise {
  0% { transform: scaleY(1); }
  35% { transform: scaleY(0.7); }
  70% { transform: scaleY(1.12); }
  100% { transform: scaleY(1); }
}

@keyframes nav-icon-bounce {
  0% { transform: translateY(0); }
  35% { transform: translateY(-3px); }
  65% { transform: translateY(1px); }
  100% { transform: translateY(0); }
}

@keyframes nav-icon-nudge {
  0% { transform: translateX(0); }
  25% { transform: translateX(-2px); }
  55% { transform: translateX(2.5px); }
  80% { transform: translateX(-1px); }
  100% { transform: translateX(0); }
}
</style>
