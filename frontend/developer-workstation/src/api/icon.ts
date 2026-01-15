import axios from 'axios'
import { TOKEN_KEY, getUser } from './auth'

const iconAxios = axios.create({
  baseURL: '',
  timeout: 30000
})

iconAxios.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  
  // 添加 X-User-Id 请求头，用于后端权限检查
  const user = getUser()
  if (user && user.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  
  return config
})

iconAxios.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
)

export interface Icon {
  id: number
  name: string
  category: string
  svgContent: string
  fileSize: number
  description?: string
  tags?: string
  createdAt: string
}

export interface IconPage {
  content: Icon[]
  totalElements: number
  totalPages: number
}

export const iconApi = {
  list: (params: { keyword?: string; category?: string; tag?: string; page?: number; size?: number }) =>
    iconAxios.get<any, { data: IconPage }>('/api/v1/icons', { params }),

  getById: (id: number) =>
    iconAxios.get<any, { data: Icon }>(`/api/v1/icons/${id}`),

  upload: (file: File, name: string, category: string, tags?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('name', name)
    formData.append('category', category)
    if (tags) formData.append('tags', tags)
    return iconAxios.post<any, { data: Icon }>('/api/v1/icons', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  delete: (id: number) =>
    iconAxios.delete(`/api/v1/icons/${id}`),

  checkUsage: (id: number) =>
    iconAxios.get<any, { data: boolean }>(`/api/v1/icons/${id}/usage`),

  getCategories: () =>
    iconAxios.get<any, { data: string[] }>('/api/v1/icons/categories'),

  getTags: () =>
    iconAxios.get<any, { data: string[] }>('/api/v1/icons/tags')
}
