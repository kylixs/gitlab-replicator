import axios from 'axios'
import { ElMessage } from 'element-plus'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor - Add authentication token
client.interceptors.request.use(
  (config) => {
    // Get token from localStorage
    const token = localStorage.getItem('auth_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor - Handle errors and token expiration
client.interceptors.response.use(
  (response) => {
    // For auth APIs, return the full response (they handle ApiResponse unwrapping)
    if (response.config.url?.includes('/auth/')) {
      return response
    }

    // For other APIs, check success and unwrap data
    const data = response.data
    if (data.success === false) {
      return Promise.reject(new Error(data.message || 'Request failed'))
    }
    return data
  },
  (error) => {
    // Handle 401 Unauthorized - Token expired or invalid
    if (error.response?.status === 401) {
      // Only show message if not on login page
      if (!window.location.pathname.includes('/login')) {
        ElMessage.error('Session expired. Please login again.')

        // Clear token
        localStorage.removeItem('auth_token')

        // Redirect to login (only if we have access to router)
        // The router guard will handle the actual redirect
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      }
    }
    // Handle 429 Too Many Requests - Rate limiting
    else if (error.response?.status === 429) {
      const retryAfter = error.response.data?.error?.retryAfter
      const message = retryAfter
        ? `Too many requests. Please try again in ${retryAfter} seconds.`
        : 'Too many requests. Please try again later.'
      ElMessage.error(message)
    }
    // Handle 423 Locked - Account locked
    else if (error.response?.status === 423) {
      const retryAfter = error.response.data?.error?.retryAfter
      const message = retryAfter
        ? `Account locked. Please try again in ${retryAfter} seconds.`
        : 'Account locked due to too many failed attempts.'
      ElMessage.error(message)
    }

    return Promise.reject(error)
  }
)

export default client
