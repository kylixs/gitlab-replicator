import client from './client'
import type { ApiResponse, SystemConfig, ConnectionTestResult } from '@/types'

export const configApi = {
  /**
   * Get all configuration
   */
  getConfig(): Promise<ApiResponse<SystemConfig>> {
    return client.get('/config/all')
  },

  /**
   * Update configuration
   */
  updateConfig(config: SystemConfig): Promise<ApiResponse<string>> {
    return client.post('/config/all', config)
  },

  /**
   * Test GitLab connection
   */
  testConnection(type: 'source' | 'target'): Promise<ApiResponse<ConnectionTestResult>> {
    return client.post('/config/test-connection', null, {
      params: { type }
    })
  }
}
