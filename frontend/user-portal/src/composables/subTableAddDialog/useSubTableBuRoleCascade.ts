import { ref, computed, type Ref } from 'vue'
import { permissionApi, type BusinessUnit, type RoleInfo } from '@/api/permission'

/**
 * MI 子任务「按角色分派」的 BU→Role 级联 + 与 assignee 的行级互斥。
 *
 * 复用普通任务 FIXED_BU_ROLE 的数据源（admin-center，经 user-portal permissionApi）：
 * - BU 列表：getBusinessUnits（平台 BU 全量目录，含用户已加入的 BU）
 * - 某 BU 下的 role：getBusinessUnitRoles(buId)
 *
 * 子表列 bu_code / role_code 落库存的是 **code**（后端 role 解析用 code），
 * 但 getBusinessUnitRoles 入参是 BU **id**，所以内部维护 code→id 映射，
 * BU select 的 value 存 code、查 role 时转 id。
 *
 * 只服务字段名恰为 `bu_code` / `role_code` 的子表列，对其它 FU 零影响。
 */
export const BU_FIELD = 'bu_code'
export const ROLE_FIELD = 'role_code'
export const ASSIGNEE_FIELD = 'assignee'

export function useSubTableBuRoleCascade(formData: Ref<Record<string, any>>) {
  // BU 用级联树（el-cascader，与 admin-center Add Role Access 一致：父 BU 可展开子 BU）。
  const buTree = ref<BusinessUnit[]>([])
  // cascader value = BU **id**（emitPath:false 取单个 id）；checkStrictly 允许选父节点。
  const buCascaderProps = { value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }
  // cascader v-model 绑这个（存 id）；formData[BU_FIELD] 存的是 **code**（后端解析用 code）。
  const selectedBuId = ref<string>('')
  const roleOptions = ref<Array<{ label: string; value: string }>>([])
  const buLoading = ref(false)
  const roleLoading = ref(false)
  // id → code（选中后把 code 存进 formData[BU_FIELD]）；缺 code 回退 id。
  const buIdToCode = ref<Record<string, string>>({})

  function indexBuTree(list: BusinessUnit[]) {
    for (const bu of list || []) {
      const id = String(bu.id)
      buIdToCode.value[id] = (bu.code && bu.code.trim()) ? bu.code.trim() : id
      if (bu.children && bu.children.length) indexBuTree(bu.children)
    }
  }

  async function loadBusinessUnits() {
    if (buTree.value.length > 0) return
    buLoading.value = true
    try {
      // 用 BU 树（保留层级，级联展示）+ 全量目录（不排除已加入）。
      // axios 拦截器返回 ApiResponse { data:[...] }，真实数组在 .data 里。
      const resp = await permissionApi.getBusinessUnitsTree() as any
      const tree = (resp?.data ?? resp) as BusinessUnit[]
      buTree.value = Array.isArray(tree) ? tree : []
      buIdToCode.value = {}
      indexBuTree(buTree.value)
    } catch {
      buTree.value = []
      buIdToCode.value = {}
    } finally {
      buLoading.value = false
    }
  }

  async function loadRolesForBu(buId: string) {
    roleOptions.value = []
    if (!buId) return
    roleLoading.value = true
    try {
      const roleResp = await permissionApi.getBusinessUnitRoles(buId) as any
      const roles = (roleResp?.data ?? roleResp) as RoleInfo[]
      roleOptions.value = (Array.isArray(roles) ? roles : [])
        .filter(r => r && r.code)
        .map(r => ({ label: r.name || r.code, value: r.code }))
    } catch {
      roleOptions.value = []
    } finally {
      roleLoading.value = false
    }
  }

  /** 选 BU（cascader value=id）：存 code 到 formData、清 role/assignee（互斥）、按 id 查 role。 */
  async function onBuChange(buId: string | null | undefined) {
    const id = buId ? String(buId) : ''
    selectedBuId.value = id
    formData.value[BU_FIELD] = id ? (buIdToCode.value[id] || id) : ''
    formData.value[ROLE_FIELD] = ''
    if (id) {
      formData.value[ASSIGNEE_FIELD] = ''
    }
    await loadRolesForBu(id)
  }

  /** 选 role：清掉 assignee（互斥）。 */
  function onRoleChange(roleCode: string) {
    if (roleCode) {
      formData.value[ASSIGNEE_FIELD] = ''
    }
  }

  function isLookupSelected(val: unknown): boolean {
    if (val == null) return false
    if (typeof val === 'object') return Object.keys(val as object).length > 0
    return String(val).trim() !== ''
  }

  /** 该行已选了人（assignee）→ BU/Role 应禁用。 */
  const assigneeChosen = computed(() => isLookupSelected(formData.value[ASSIGNEE_FIELD]))
  /** 该行已选了 BU 或 role → assignee 应禁用。 */
  const buOrRoleChosen = computed(() =>
    !!(formData.value[BU_FIELD] && String(formData.value[BU_FIELD]).trim())
    || !!(formData.value[ROLE_FIELD] && String(formData.value[ROLE_FIELD]).trim()))

  /** 编辑态打开时：formData[BU_FIELD] 存的是 code，反查 id 设回 cascader，并预加载其 role。 */
  async function primeFromExistingRow() {
    await loadBusinessUnits()
    const code = formData.value[BU_FIELD]
    if (code && String(code).trim()) {
      const c = String(code).trim()
      // code → id（反查 buIdToCode）；找不到就当它本身是 id。
      const id = Object.keys(buIdToCode.value).find(k => buIdToCode.value[k] === c) || c
      selectedBuId.value = id
      await loadRolesForBu(id)
    }
  }

  return {
    buTree,
    buCascaderProps,
    selectedBuId,
    roleOptions,
    buLoading,
    roleLoading,
    loadBusinessUnits,
    loadRolesForBu,
    onBuChange,
    onRoleChange,
    assigneeChosen,
    buOrRoleChosen,
    primeFromExistingRow,
  }
}
