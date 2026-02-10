import axios from 'axios'

/**
 * 历史数据API接口
 */

/**
 * 已执行活动响应数据
 */
export interface ExecutedActivitiesResponse {
  success: boolean
  data: string[]
  message?: string
}

// 创建专门用于调用 workflow-engine 的 axios 实例
const historyRequest = axios.create({
  baseURL: '/api/v1/history',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 添加请求拦截器，携带 token
historyRequest.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/**
 * 历史API客户端
 */
export const historyApi = {
  /**
   * 获取流程实例已执行的活动ID列表
   * @param processInstanceId 流程实例ID
   * @returns 已执行的活动ID列表
   */
  async getExecutedActivityIds(processInstanceId: string): Promise<string[]> {
    try {
      const response = await historyRequest.get<ExecutedActivitiesResponse>(
        '/executed-activities',
        {
          params: { processInstanceId }
        }
      )
      return response.data?.data || []
    } catch (error) {
      console.error('Failed to load executed activities:', error)
      // 返回空数组作为降级处理
      return []
    }
  }
}
