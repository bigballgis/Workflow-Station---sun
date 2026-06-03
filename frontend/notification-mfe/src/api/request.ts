import axios, { AxiosInstance } from 'axios'

const service: AxiosInstance = axios.create({
  baseURL: '/api/portal',
  timeout: 600000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

service.interceptors.response.use(
  (response) => response.data,
  (error) => {
    // Auth handled by host — 401 redirects to login
    console.error('[notification-mfe] API error:', error.config?.url, error.message)
    return Promise.reject(error)
  }
)

export default service
