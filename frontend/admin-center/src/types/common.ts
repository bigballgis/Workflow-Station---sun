/**
 * 跨模块共享的通用类型
 *
 * 所有 API 模块从这里导入，而非各自重复定义。
 */

/** 分页响应 */
export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}
