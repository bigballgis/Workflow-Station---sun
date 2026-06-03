import axios, { AxiosInstance } from 'axios'

const service: AxiosInstance = axios.create({
  baseURL: '/api/portal',
  timeout: 600000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

service.interceptors.response.use(
  (r) => r.data,
  (e) => {
    console.error('[delegation-mfe] API error:', e.config?.url, e.message)
    return Promise.reject(e)
  }
)

export default service
