/**
 * 通用 Task 节点属性面板（TaskProperties.vue）的共享类型。
 *
 * 这些字面量联合类型与拆分前 SFC 内联声明逐字保持一致，仅集中存放，
 * 不改变任何取值范围或行为。
 */

/** 用户任务分配方式 */
export type AssigneeType = 'user' | 'role' | 'expression'

/** 超时动作 */
export type TimeoutAction = 'remind' | 'approve' | 'reject'

/** 服务任务实现方式 */
export type ServiceType = 'http' | 'class' | 'expression' | 'delegateExpression'
