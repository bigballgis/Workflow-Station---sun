/**
 * Binding 分类的**配置判据** —— 一个 sub-table binding 在 MI 语义下是哪一类，全部读设计器配置，
 * 不看列名、不看表名、不看 FK 列叫什么。
 *
 * <p><b>三类的定义与判据</b>（现场 FU 50005 实测，见下表）：
 * <ul>
 *   <li><b>collection</b> —— MI 子任务集合，每行 = 一个参与者 = 一个子任务。
 *       判据：{@code bindingLinkMode === 'miParticipantRow'}，这是设计器 Manage Table Bindings
 *       里 Link Mode 选 "MI Participant Row" 的**显式声明**。</li>
 *   <li><b>participant-child</b> —— 挂在某个参与者名下的明细（People 式）。
 *       判据：字段级 FK（{@code fieldDefinitions[].isForeignKey}）的 {@code refTableId}
 *       **指向 collection 的 tableId**。</li>
 *   <li><b>shared</b> —— 流程级共享表（附件式），所有子任务看同一份，不按参与者分片。
 *       判据：有字段级 FK，但 {@code refTableId} 指向**主表**而非 collection。</li>
 * </ul>
 *
 * <p><b>为什么不能只看 {@code bindingLinkMode}。</b>实测 attachment 和 people **都是**
 * {@code structuralFk}：
 * <pre>
 *   attachment  structuralFk  FK main_idv     -> 50332 main      => shared
 *   people      structuralFk  FK sub_task_idq -> 50331 subtable  => participant-child
 * </pre>
 * 区分二者的**唯一**信息是 FK 指向哪张表。把 shared 写成「其余情况」的 else 兜底是错的：
 * 那样一个 FK 元数据缺失的 child 会被静默判成 shared，导致跨参与者串数据。
 *
 * <p><b>为什么这些判据不是"又一种猜"。</b>它们全部来自用户在 Developer Workstation 里的显式配置
 * （{@code dw_form_table_bindings.binding_link_mode} + {@code dw_field_definitions} 的
 * {@code is_foreign_key}/{@code ref_table_id}），改名字段、改表名都不影响 —— 而旧启发式在
 * demo FU 把 {@code sub_task_id} 改成 {@code sub_task_idq} 后两个方向都答错了。
 *
 * <p><b>解析不出时返回 {@code null}</b>（"我不知道"），由调用方决定退回旧启发式还是报错，
 * 本模块**绝不猜**。
 */

import { getActiveMiKindTableIds, setActiveMiKindTableIds } from './useMiConfig'

/** MI 语义下 binding 的种类。 */
export type MiBindingKind = 'collection' | 'participant-child' | 'shared'

/** 设计器字段定义中与 FK 判定相关的部分（{@code dw_field_definitions}）。 */
export interface MiKindFieldDef {
  fieldName?: string
  isForeignKey?: boolean
  /** 设计器列类型（{@code data_type}）：VARCHAR / TEXT / FILE / …。`FILE` 表示这一列存上传文件。 */
  dataType?: string | null
  /** FK 指向的表 id（{@code dw_table_definitions.id}）。 */
  refTableId?: number | null
}

/** 分类所需的最小 binding 形状。 */
export interface MiKindBindingLike {
  tableId?: number | null
  /** {@code dw_form_table_bindings.binding_link_mode}。 */
  bindingLinkMode?: string | null
  fieldDefinitions?: MiKindFieldDef[] | null
}

/**
 * 设计器把这张表的某一列声明成了文件列（{@code data_type = 'FILE'}）。
 *
 * <p>这是「这张表装的是上传文件」的**权威判据**。曾经靠列名 `file` 或表名 `attachment` 猜 ——
 * 前者会把普通 `file_path:VARCHAR` 列的表误判成附件表，后者改个表名就失效。
 */
export function bindingHasDesignerFileColumn(binding: MiKindBindingLike | null | undefined): boolean {
  return (binding?.fieldDefinitions ?? []).some(
    f => String(f?.dataType ?? '').trim().toUpperCase() === 'FILE',
  )
}

/** 分类的上下文：谁是 collection、谁是主表。两者都来自配置。 */
export interface MiKindContext {
  /** MI collection 的 tableId。null = 本流程没有 MI 子流程，或尚未解析出来。 */
  miCollectionTableId?: number | null
  /** PRIMARY binding 的 tableId（主表）。 */
  primaryTableId?: number | null
}

const MI_PARTICIPANT_ROW_LINK_MODE = 'miParticipantRow'

/**
 * 解析分类上下文：**显式传入优先，其次读活动注册表**（详情页解析完 BPMN 后注册）。
 *
 * <p>39 个调用点里绝大多数是深层纯函数，拿不到 binding 列表也拿不到 BPMN。注册表让它们不必
 * 逐层传参就能拿到权威表 id —— 这是删掉列名启发式的**前提**：没有它，漏传的调用点只能回去猜。
 */
export function resolveMiKindContext(explicit?: MiKindContext | null): MiKindContext {
  const active = getActiveMiKindTableIds()
  return {
    miCollectionTableId: explicit?.miCollectionTableId ?? active.miCollectionTableId,
    primaryTableId: explicit?.primaryTableId ?? active.primaryTableId,
  }
}

/** 设计器是否把这个 binding 显式声明为 MI 参与者行（= MI collection）。 */
export function bindingDeclaresMiParticipantRow(binding: MiKindBindingLike | null | undefined): boolean {
  return String(binding?.bindingLinkMode ?? '').trim() === MI_PARTICIPANT_ROW_LINK_MODE
}

/**
 * 从 binding 列表里认出 collection，连同主表 tableId 一起注册到活动上下文。
 *
 * <p>由 To Do / My Request 两条链路在 binding 构建完成后各调一次 —— 两处**必须都调**，
 * 否则漏掉的那条链路会因拿不到 collection tableId 而判不出 participant-child。
 */
export function registerMiKindTableIdsFromBindings(
  bindings: readonly MiKindBindingLike[] | null | undefined,
  primaryTableId: number | null | undefined,
): void {
  const collection = (bindings ?? []).find(b => bindingDeclaresMiParticipantRow(b))
  const tid = collection?.tableId
  setActiveMiKindTableIds({
    miCollectionTableId: tid != null && Number.isFinite(Number(tid)) ? Number(tid) : null,
    primaryTableId:
      primaryTableId != null && Number.isFinite(Number(primaryTableId)) ? Number(primaryTableId) : null,
  })
}

/** 这个 binding 的字段级 FK 里，有没有指向 `targetTableId` 的。 */
function hasFieldFkTo(
  binding: MiKindBindingLike | null | undefined,
  targetTableId: number | null | undefined,
): boolean {
  if (targetTableId == null || !Number.isFinite(Number(targetTableId))) return false
  const want = Number(targetTableId)
  for (const f of binding?.fieldDefinitions ?? []) {
    if (!f?.isForeignKey) continue
    const ref = f.refTableId != null ? Number(f.refTableId) : null
    if (ref != null && Number.isFinite(ref) && ref === want) return true
  }
  return false
}

/**
 * 按配置判定 binding 的 MI 种类。
 *
 * @returns 三类之一；配置不足以判定时返回 {@code null}（调用方可退回旧启发式，**不要**在这里猜）
 */
export function resolveMiBindingKindFromConfig(
  binding: MiKindBindingLike | null | undefined,
  ctx: MiKindContext | null | undefined,
): MiBindingKind | null {
  if (!binding) return null

  // 1) collection —— 设计器显式声明，优先于任何其它判据。
  if (bindingDeclaresMiParticipantRow(binding)) return 'collection'

  const resolved = resolveMiKindContext(ctx)
  const collectionTid = resolved.miCollectionTableId ?? null
  // collection 就是它自己（同一张表被另一个 binding 以 miParticipantRow 声明过）。
  if (
    collectionTid != null
    && binding.tableId != null
    && Number(binding.tableId) === Number(collectionTid)
  ) {
    return 'collection'
  }

  // 2) participant-child —— 字段级 FK 指向 collection。
  if (collectionTid != null && hasFieldFkTo(binding, collectionTid)) return 'participant-child'

  // 3) shared —— 字段级 FK 指向主表。
  //    注意这是**正面判据**，不是 else 兜底：FK 元数据缺失时必须返回 null 让调用方处理，
  //    静默判成 shared 会让一个 child 失去参与者隔离（跨子任务串数据）。
  if (resolved.primaryTableId != null && hasFieldFkTo(binding, resolved.primaryTableId)) return 'shared'

  // 走到这里 = 判不了：
  //   - 调用方给不出 collection tableId（collectionTid == null），无从判断 FK 是否指向它；
  //   - 或者给得出，但这个 binding 的 FK 既不指 collection 也不指主表（例如 FK 元数据
  //     只标了 isForeignKey 而没有 refTableId —— 存量 binding 常见）。
  // **绝不能**在这里返回 'shared'：一个真 child 被静默降级成 shared 就失去参与者隔离，
  // 跨子任务串数据。返回 null 让调用方退回启发式，是安全的一侧。
  return null
}
