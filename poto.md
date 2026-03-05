# Workflow Station 1.0 — Feature Blueprint (Tree for 2.0)

Treat the system as a **house**: **admin-center**, **user-portal**, and **developer-workstation** are three floors; each floor has "rooms" (modules) and "furniture" (concrete features).

---

## Floor 1: Admin Center (Management Hub)

**Role:** System and permission management, organization and users, function-unit operations.

| Room (Module) | Furniture (Features / Sub-pages) |
|---------------|----------------------------------|
| **Entrance · Login** | Login page, logout, token auth |
| **Lobby · Dashboard** | Stat cards (total users, business units, roles, online users); user trend chart; recent activity timeline |
| **User Management** | User list (keyword, status filter); create user (UserFormDialog); edit user; user detail (UserDetailDialog); batch import (UserImport / UserImportDialog); entity manager / function manager display |
| **Organization (Business Unit)** | Business unit tree (search, drag reorder); selected node detail (code, parent, member count); create/edit/delete unit (BusinessUnitFormDialog); members (BusinessUnitMembersDialog); eligible roles (BusinessUnitRolesDialog); approvers (BusinessUnitApproversDialog) |
| **Virtual Group** | Virtual group list (name, code, type, bound role, AD group, member count, status); create/edit/delete (VirtualGroupFormDialog); members (VirtualGroupMembersDialog); bind roles (VirtualGroupRolesDialog); approvers (VirtualGroupApproversDialog) |
| **Role Management** | Role list (type filter: BU-bounded / unbounded / Admin / Developer); create/edit/delete (RoleFormDialog); members (RoleMembersDialog); **Permission Config (PermissionConfig)**: resource × action matrix (read/write, etc.) per role |
| **Function Unit** | **List tab:** table, enable/disable switch, access config, deploy, version history, rollback, delete; **Deployment records tab:** environment, strategy, status, deployed at/by; import package (ZIP upload); deploy dialog (target env, strategy) |
| **Profile & System Entry** | **Profile:** current user, BU/virtual group/role tags; **System Config (Config):** entry from avatar dropdown — system params (session timeout, file upload limit, mail server, etc.), business params (process timeout, task assign rule); **Audit Log (Audit):** filter by action type, operator, result, date range; list columns and detail dialog; export; **Data Dictionary (Dictionary):** dict list/tree, create/edit dict (DictionaryFormDialog), dict items (DictionaryItemDialog, i18n); **Permission Request:** request list and handling (if backend exists) |
| **Common** | Top bar (logo, sidebar collapse, UserProfileDropdown); sidebar menu (permission-based); 403 forbidden page |

---

## Floor 2: User Portal (User-facing)

**Role:** End users handle tasks, start processes, view applications and permissions, personal settings.

| Room (Module) | Furniture (Features / Sub-pages) |
|---------------|----------------------------------|
| **Entrance · Login** | Login page, logout, token auth |
| **Lobby · Dashboard** | Task overview (pending, overdue, completed today, urgent/high-priority counts); process overview (initiated, in progress, completed this month, approval rate); quick actions (processes, tasks, my applications, delegations, permissions); performance (efficiency/quality/collaboration scores, monthly rank); recent tasks list |
| **Tasks (Todo)** | Task list (filters: assignment type USER/VIRTUAL_GROUP/DEPT_ROLE/DELEGATED, priority, keyword); columns (task name, process name, assignment type, initiator, priority, create time, due date, overdue tag); batch actions (approve, transfer, urge); link to task detail |
| **Completed Tasks** | Completed task list and filters |
| **Task Detail** | Task info, process info, form renderer (FormRenderer), attachments (FileUploader), action buttons (ActionButtons), process diagram (ProcessDiagram), process history (ProcessHistory) |
| **Processes** | List of startable processes (by function unit/process); start entry |
| **Start Process** | Process diagram (ProcessDiagram); dynamic form (FormRenderer); sub-table (SubTableField); submit / save draft; disabled / access denied / load error states |
| **My Applications** | List of process instances I started; application detail (applications/detail) |
| **Application Detail** | Single application detail, process status, history |
| **Delegations** | Delegation rules list and management (menu can be hidden) |
| **Permissions** | **Pending tab:** permission requests awaiting approval, cancel request; **History tab:** request history (type, target, reason, status, approver comment, times); **Apply permission** entry/dialog |
| **My Requests** | List of permission requests I submitted (route my-requests) |
| **Approvals** | Approver view: list of permission requests to approve and approve actions (menu shown when isApprover) |
| **Member Management** | Member management for role/virtual group (member-management) |
| **Exit Role** | User-initiated exit from a role (exit-role) |
| **Profile & Settings** | **Profile:** current user, BU/virtual group/role; **Settings:** entry from avatar dropdown — **Appearance:** theme (light/dark), theme color, font size; **Notifications:** task assigned / overdue / process completed (email, browser, in-app); quiet hours; **Dashboard layout:** draggable widgets (DraggableDashboard), e.g. TaskOverviewWidget, ProcessStatsWidget, PerformanceWidget, CalendarWidget, NotificationsWidget, QuickActionsWidget |
| **Common** | Top bar, sidebar (Dashboard, Tasks, Completed, Processes, My Applications, Delegations, Permissions, Approvals); 404 page; BU-bounded role reminder (BuBoundedRoleReminder) |

---

## Floor 3: Developer Workstation

**Role:** Design and publish function units (process, tables, forms, actions, versions, icons).

| Room (Module) | Furniture (Features / Sub-pages) |
|---------------|----------------------------------|
| **Entrance · Login** | Login page, logout, token auth |
| **Function Unit List** | Card/list (FunctionUnitCard); enter edit page for a unit |
| **Function Unit Edit** | Header: back, icon (IconPreview), name, status, version; **Settings** (icon, name, description, IconSelector); **Export**; **Validate** (result dialog); **Deploy** (dialog: environment, auto-enable, etc.); **Tab: Process** → **Process Designer (ProcessDesigner):** BPMN canvas (bpmn-js Modeler), zoom/fit/undo/redo, validate, export SVG/XML, import XML; **Properties panel (NodePropertiesPanel):** process (ProcessProperties), user task (UserTaskProperties), service task (ServiceTaskProperties), other tasks (TaskProperties), gateway (GatewayProperties), sequence flow (SequenceFlowProperties), events (EventProperties); **Debug:** ProcessDebugPanel (VariableMonitor, ExecutionLogViewer, etc.); **Tab: Tables** → **Table Designer (TableDesigner):** table definitions, fields, table–form binding (TableBindingManager); SubTableField; **Tab: Forms** → **Form Designer (FormDesigner):** form layout, controls, bindings; **Tab: Actions** → **Action Designer (ActionDesigner):** action definitions; **Tab: Versions** → **Version Manager (VersionManager):** history, compare, rollback |
| **Icon Library** | Icon list, selector (IconSelector), preview (IconPreview); used when choosing function unit icon |
| **Profile** | Current user (entry from avatar dropdown) |
| **Common** | Top bar, sidebar (function units, icon library), collapse button |

---

## Cross-cutting / Shared (for 2.0)

- **Auth:** Login, token, logout, current user.
- **Permissions:** admin (user:read/write, role:read/write, system:admin, audit:read, system:config, etc.); portal (isApprover, function-unit access); developer (developer permission and interceptors).
- **i18n:** zh-CN, en, zh-TW.
- **Workflow engine:** Process deployment, instance/task APIs, start/complete (2.0 must keep equivalent behavior with different stack).