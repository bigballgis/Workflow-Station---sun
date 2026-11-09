import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import { canAccessRoute } from "@/utils/permission";
import { ElMessage } from "element-plus";
import i18n from "@/i18n";
import { redirectToUnifiedLogin, setSsoReturnPath } from "@/utils/sso";
import { getUser, getCurrentUser, clearAuth, type AdminFeature } from "@/api/auth";

// Extend route meta type
declare module "vue-router" {
  interface RouteMeta {
    title?: string;
    titleKey?: string;
    icon?: string;
    hidden?: boolean;
    requiresAuth?: boolean;
    /** Admin page the route belongs to; the user's capability for it decides access (utils/permission). */
    feature?: AdminFeature;
    /** The route is a write entry (import, create): needs write access to the feature. */
    write?: boolean;
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: "/sso/callback",
    name: "SsoCallback",
    component: () => import("@/views/sso/SsoCallback.vue"),
    meta: { titleKey: "login.title", hidden: true },
  },
  {
    path: "/403",
    name: "Forbidden",
    component: () => import("@/views/error/403.vue"),
    meta: { titleKey: "error.forbidden", hidden: true },
  },
  {
    path: "/",
    component: () => import("@/layouts/AdminLayout.vue"),
    redirect: "/dashboard",
    meta: { requiresAuth: true },
    children: [
      {
        path: "dashboard",
        name: "Dashboard",
        component: () => import("@/views/dashboard/index.vue"),
        meta: { titleKey: "menu.dashboard", icon: "Odometer" },
      },
      {
        path: "user/list",
        name: "UserList",
        component: () => import("@/views/user/UserList.vue"),
        meta: {
          titleKey: "menu.userManagement",
          icon: "User",
          feature: "users",
        },
      },
      {
        path: "user/import",
        name: "UserImport",
        component: () => import("@/views/user/UserImport.vue"),
        meta: {
          titleKey: "menu.userImport",
          hidden: true,
          feature: "users",
          write: true,
        },
      },
      {
        path: "organization",
        name: "Organization",
        component: () => import("@/views/organization/BusinessUnitTree.vue"),
        meta: {
          titleKey: "menu.organization",
          icon: "OfficeBuilding",
          feature: "organization",
        },
      },
      {
        path: "organization/department",
        redirect: "/organization",
      },
      {
        path: "virtual-group",
        name: "VirtualGroup",
        component: () => import("@/views/virtual-group/index.vue"),
        meta: {
          titleKey: "menu.virtualGroup",
          icon: "Connection",
          feature: "virtualGroups",
        },
      },
      {
        path: "role",
        name: "RoleManagement",
        component: () => import("@/views/role/RoleList.vue"),
        meta: {
          titleKey: "menu.roleManagement",
          icon: "Key",
          feature: "roles",
        },
      },
      {
        path: "role/list",
        redirect: "/role",
      },
      {
        path: "function-unit",
        name: "FunctionUnit",
        component: () => import("@/views/function-unit/index.vue"),
        meta: {
          titleKey: "menu.functionUnit",
          icon: "Box",
          feature: "functionUnits",
        },
      },
      {
        path: "automation-pieces",
        name: "AutomationPieces",
        component: () => import("@/views/automation-piece/index.vue"),
        meta: {
          titleKey: "menu.automationPieces",
          icon: "Cpu",
          feature: "automationPieces",
        },
      },
      {
        path: "automation-flows",
        name: "AutomationFlows",
        component: () => import("@/views/automation-flow/index.vue"),
        meta: {
          titleKey: "menu.automationFlows",
          icon: "Share",
          feature: "automationFlows",
        },
      },
      {
        path: "automation-runs",
        name: "AutomationRuns",
        component: () => import("@/views/automation-run/index.vue"),
        meta: {
          titleKey: "menu.automationRuns",
          icon: "Tickets",
          feature: "automationRuns",
        },
      },
      {
        path: "audit",
        name: "Audit",
        redirect: "/audit/admin-center",
        children: [
          {
            path: "admin-center",
            name: "AuditAdminCenter",
            component: () => import("@/views/audit/index.vue"),
            meta: {
              titleKey: "menu.auditAdminCenter",
              icon: "Document",
              feature: "auditAdminCenter",
            },
          },
          {
            path: "user-portal",
            name: "AuditUserPortal",
            component: () => import("@/views/audit/user-portal/index.vue"),
            meta: {
              titleKey: "menu.auditUserPortal",
              icon: "Document",
              feature: "auditUserPortal",
            },
          },
          {
            path: "ldap-sync",
            name: "AuditLdapSync",
            component: () => import("@/views/ldap-sync/index.vue"),
            meta: {
              titleKey: "menu.ldapSync",
              icon: "Document",
              feature: "ldapSync",
            },
          },
        ],
      },
      {
        path: "config",
        name: "Config",
        component: () => import("@/views/config/index.vue"),
        meta: {
          titleKey: "menu.config",
          icon: "Setting",
          feature: "systemConfig",
        },
      },
      {
        path: "profile",
        name: "Profile",
        component: () => import("@/views/profile/index.vue"),
        meta: {
          titleKey: "profile.title",
          icon: "User",
          hidden: true,
        },
      },
      {
        path: "bi-management/dashboard-registry",
        name: "BiDashboardRegistry",
        component: () => import("@/views/bi-management/DashboardRegistry.vue"),
        meta: {
          titleKey: "menu.biDashboardRegistry",
          icon: "DataAnalysis",
          requiresAuth: true,
          feature: "biDashboardRegistry",
        },
      },
      {
        path: "bi-management/audience-assignment",
        alias: "/bi-management/dashboard-assignment",
        name: "BiDashboardAssignment",
        component: () =>
          import("@/views/bi-management/DashboardAssignment.vue"),
        meta: {
          titleKey: "menu.biAudienceAssignment",
          icon: "Share",
          requiresAuth: true,
          feature: "biAudienceAssignment",
        },
      },
      {
        path: "bi-management/data-view-assignment",
        name: "BiDataViewAssignment",
        component: () =>
          import("@/views/bi-management/DataViewAssignment.vue"),
        meta: {
          titleKey: "menu.biDataViewAssignment",
          icon: "View",
          requiresAuth: true,
          feature: "biDataViewAssignment",
        },
      },
      {
        path: "bi-management/rbac-mapping",
        name: "BiRbacMapping",
        component: () => import("@/views/bi-management/RbacMapping.vue"),
        meta: {
          titleKey: "menu.biRbacMapping",
          icon: "Connection",
          requiresAuth: true,
          feature: "biRbacMapping",
        },
      },
      // ==================== Relation Tables ====================
      {
        path: "relation-tables/structure",
        name: "RelationTableStructure",
        component: () => import("@/views/relation-table/structure/index.vue"),
        meta: {
          titleKey: "menu.relationTables",
          icon: "Grid",
          feature: "relationTables",
        },
      },
      {
        path: "relation-tables/structure/create",
        name: "RelationTableStructureCreate",
        component: () => import("@/views/relation-table/structure/form.vue"),
        meta: {
          titleKey: "menu.tableStructure",
          hidden: true,
          feature: "relationTables",
          write: true,
        },
      },
      {
        path: "relation-tables/structure/:id/edit",
        name: "RelationTableStructureEdit",
        component: () => import("@/views/relation-table/structure/form.vue"),
        meta: {
          titleKey: "menu.tableStructure",
          hidden: true,
          feature: "relationTables",
        },
      },
      {
        path: "relation-tables/structure/er-diagram",
        name: "RelationTableERDiagramAll",
        component: () => import("@/views/relation-table/structure/er-diagram.vue"),
        meta: {
          titleKey: "erDiagram.title",
          hidden: true,
          feature: "relationTables",
        },
      },
      {
        path: "relation-tables/structure/:id/er-diagram",
        name: "RelationTableERDiagram",
        component: () => import("@/views/relation-table/structure/er-diagram.vue"),
        meta: {
          titleKey: "erDiagram.title",
          hidden: true,
          feature: "relationTables",
        },
      },
      {
        path: "environment-variables",
        name: "EnvironmentVariables",
        component: () => import("@/views/relation-table/environment-variables/index.vue"),
        meta: {
          titleKey: "menu.environmentVariables",
          icon: "Key",
          feature: "environmentVariables",
        },
      },
      {
        path: "relation-tables/environment-variables",
        redirect: "/environment-variables",
      },
      {
        path: "sla-policies",
        name: "SlaPolicies",
        component: () => import("@/views/sla-policy/index.vue"),
        meta: {
          titleKey: "menu.slaPolicies",
          icon: "Timer",
          feature: "slaPolicies",
        },
      },
      {
        path: "relation-tables/data/:functionUnitCode?",
        name: "RelationTableData",
        component: () => import("@/views/relation-table/data/index.vue"),
        meta: {
          titleKey: "menu.tableData",
          icon: "Coin",
          feature: "relationTables",
        },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

router.beforeEach(async (to, _from, next) => {
  const t = i18n.global.t;
  const pageTitle = to.meta.titleKey
    ? t(to.meta.titleKey)
    : to.meta.title || t("app.name");
  document.title = `${pageTitle} - ${t("app.title")}`;

  if (to.path === "/login") {
    const r = to.query.redirect;
    setSsoReturnPath(typeof r === "string" ? r : "/dashboard");
    redirectToUnifiedLogin('admin', { autoSso: true });
    return next(false);
  }

  if (to.path === "/sso/callback" || to.path === "/403") {
    next();
    return;
  }

  // For routes that require authentication, always validate session with /auth/me
  if (to.matched.some((r) => r.meta && r.meta.requiresAuth)) {
    const stored = getUser();
    if (!stored) {
      setSsoReturnPath(to.fullPath);
      redirectToUnifiedLogin('admin', { autoSso: true });
      return next(false);
    }

    try {
      await getCurrentUser();
    } catch {
      clearAuth();
      setSsoReturnPath(to.fullPath);
      redirectToUnifiedLogin('admin', { autoSso: true });
      return next(false);
    }
  }

  // The capability table decides which pages the user may open
  if (!canAccessRoute(to)) {
    ElMessage.warning(t("error.noPermission"));
    next("/403");
    return;
  }

  next();
});

export default router;
