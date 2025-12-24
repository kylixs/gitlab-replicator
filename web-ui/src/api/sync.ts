import client from './client'
import type { ApiResponse } from '@/types'

export interface ScanResult {
  type: string
  startTime: string
  endTime: string
  scannedCount: number
  addedCount: number
  updatedCount: number
  removedCount: number
  failedCount: number
}

export const syncApi = {
  /**
   * Trigger manual scan
   */
  triggerScan(type: 'incremental' | 'full' = 'incremental'): Promise<ApiResponse<ScanResult>> {
    return client.post('/sync/scan', null, {
      params: { type }
    })
  }
}
