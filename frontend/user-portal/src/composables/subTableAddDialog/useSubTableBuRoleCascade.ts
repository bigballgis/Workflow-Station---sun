import { ref, computed, type Ref } from 'vue'
import { permissionApi, type BusinessUnit, type RoleInfo } from '@/api/permission'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

/**
 * MI 子任务「按角色分派」的 BU→Role 级联 + 与 assignee 的行级互斥。
 *
 * 复用普通任务 FIXED_BU_ROLE 的数据源（admin-center，经 user-portal permissionApi）：
 * - BU 列表：getBusinessUnits（平台 BU 全量目录，含用户已加入的 BU）
 * - 某 BU 下的 role：getBusinessUnitRoles(buId)
 *
 * 配置指定的 BU / Role 列落库存的是 **code**（后端 role 解析用 code），
 * 但 getBusinessUnitRoles 入参是 BU **id**，所以内部维护 code→id 映射，
 * BU select 的 value 存 code、查 role 时转 id。
 *
 * 字段名完全来自 BPMN AssignmentConfig，不提供固定列名回退。
 */
export function useSubTableBuRoleCascade(
  formData: Ref<Record<string, any>>,
  assignmentConfig: Ref<AssignmentConfig | undefined>,
) {
  // BU 用级联树（el-cascader，与 admin-center Add Role Access 一致：父 BU 可展开子 BU）。
  const buTree = ref<BusinessUnit[]>([])
  // cascader value = BU **id**（emitPath:false 取单个 id）；checkStrictly 允许选父节点。
  const buCascaderProps = { value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }
  // cascader v-model stores id; the configured BU field stores code for backend resolution.
  const selectedBuId = ref<string>('')
  const roleOptions = ref<Array<{ label: string; value: string }>>([])
  const buLoading = ref(false)
  const roleLoading = ref(false)
  // id → code; when the directory omits code, preserve the id.
  const buIdToCode = ref<Record<string, string>>({})
  const buField = computed(() => assignmentConfig.value?.buField)
  const roleField = computed(() => assignmentConfig.value?.roleField)
  const assigneeField = computed(() => assignmentConfig.value?.assigneeField)
  const isConfiguredRoleCascade = computed(() =>
    assignmentConfig.value?.allowRole === true && !!buField.value && !!roleField.value)

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
    if (!isConfiguredRoleCascade.value || !buField.value || !roleField.value) return
    const id = buId ? String(buId) : ''
    selectedBuId.value = id
    formData.value[buField.value] = id ? (buIdToCode.value[id] || id) : ''
    formData.value[roleField.value] = ''
    if (id && assigneeField.value) {
      formData.value[assigneeField.value] = ''
    }
    await loadRolesForBu(id)
  }

  /** 选 role：清掉 assignee（互斥）。 */
  function onRoleChange(roleCode: string) {
    if (roleCode && assigneeField.value) {
      formData.value[assigneeField.value] = ''
    }
  }

  function isLookupSelected(val: unknown): boolean {
    if (val == null) return false
    if (typeof val === 'object') return Object.keys(val as object).length > 0
    return String(val).trim() !== ''
  }

  /** 该行已选了人（assignee）→ BU/Role 应禁用。 */
  const assigneeChosen = computed(() =>
    assigneeField.value ? isLookupSelected(formData.value[assigneeField.value]) : false)
  /** 该行已选了 BU 或 role → assignee 应禁用。 */
  const buOrRoleChosen = computed(() =>
    !!(buField.value && formData.value[buField.value] && String(formData.value[buField.value]).trim())
    || !!(roleField.value && formData.value[roleField.value] && String(formData.value[roleField.value]).trim()))

  /** Editing: resolve the configured BU code back to id and preload its roles. */
  async function primeFromExistingRow() {
    if (!isConfiguredRoleCascade.value || !buField.value) return
    await loadBusinessUnits()
    const code = formData.value[buField.value]
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
    isConfiguredRoleCascade,
    primeFromExistingRow,
  }
}
