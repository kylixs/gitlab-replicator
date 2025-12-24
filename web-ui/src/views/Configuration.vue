<template>
  <div class="configuration">
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <h2>System Configuration</h2>
          <div class="header-actions">
            <el-button @click="loadConfig" :loading="loading">
              <el-icon><Refresh /></el-icon>
              Refresh
            </el-button>
            <el-button type="primary" @click="saveConfig" :loading="saving">
              <el-icon><Check /></el-icon>
              Save Configuration
            </el-button>
          </div>
        </div>
      </template>

      <el-form :model="config" label-width="200px">
        <!-- GitLab Instances -->
        <div class="config-section">
          <h3>GitLab Instances</h3>
          <el-divider />

          <!-- Source GitLab -->
          <div class="instance-config">
            <h4>Source GitLab</h4>
            <el-form-item label="URL">
              <el-input v-model="config.gitlab.source.url" placeholder="http://localhost:8000" />
            </el-form-item>
            <el-form-item label="Access Token">
              <el-input
                v-model="config.gitlab.source.token"
                type="password"
                show-password
                placeholder="glpat-****"
              />
            </el-form-item>
            <el-form-item>
              <el-button @click="testConnection('source')" :loading="testingSource">
                Test Connection
              </el-button>
              <span v-if="sourceTestResult" class="test-result">
                <el-tag v-if="sourceTestResult.connected" type="success">
                  Connected - {{ sourceTestResult.version }} ({{ sourceTestResult.latencyMs }}ms)
                </el-tag>
                <el-tag v-else type="danger">
                  Failed: {{ sourceTestResult.error }}
                </el-tag>
              </span>
            </el-form-item>
          </div>

          <!-- Target GitLab -->
          <div class="instance-config">
            <h4>Target GitLab</h4>
            <el-form-item label="URL">
              <el-input v-model="config.gitlab.target.url" placeholder="http://localhost:9000" />
            </el-form-item>
            <el-form-item label="Access Token">
              <el-input
                v-model="config.gitlab.target.token"
                type="password"
                show-password
                placeholder="glpat-****"
              />
            </el-form-item>
            <el-form-item>
              <el-button @click="testConnection('target')" :loading="testingTarget">
                Test Connection
              </el-button>
              <span v-if="targetTestResult" class="test-result">
                <el-tag v-if="targetTestResult.connected" type="success">
                  Connected - {{ targetTestResult.version }} ({{ targetTestResult.latencyMs }}ms)
                </el-tag>
                <el-tag v-else type="danger">
                  Failed: {{ targetTestResult.error }}
                </el-tag>
              </span>
            </el-form-item>
          </div>
        </div>

        <!-- Scheduled Scan Settings -->
        <div class="config-section">
          <h3>Scheduled Scan Settings</h3>
          <el-divider />

          <el-form-item label="Enable Scheduled Scan">
            <el-switch v-model="config.scanSettings.enabled" />
          </el-form-item>

          <el-form-item label="Incremental Scan Interval">
            <el-input-number
              v-model="config.scanSettings.incrementalInterval"
              :min="60000"
              :max="3600000"
              :step="60000"
            />
            <span class="input-hint">milliseconds ({{ formatInterval(config.scanSettings.incrementalInterval) }})</span>
          </el-form-item>

          <el-form-item label="Full Scan Cron">
            <el-input v-model="config.scanSettings.fullScanCron" placeholder="0 0 2 * * ?" />
            <span class="input-hint">Cron expression (e.g., 0 0 2 * * ? for daily at 2:00 AM)</span>
          </el-form-item>
        </div>

        <!-- Sync Settings -->
        <div class="config-section">
          <h3>Sync Settings</h3>
          <el-divider />

          <el-form-item label="Sync Interval">
            <el-input-number v-model="config.syncSettings.syncInterval" :min="60" :max="3600" :step="60" />
            <span class="input-hint">seconds ({{ formatSeconds(config.syncSettings.syncInterval) }})</span>
          </el-form-item>

          <el-form-item label="Concurrency">
            <el-input-number v-model="config.syncSettings.concurrency" :min="1" :max="20" />
            <span class="input-hint">Number of concurrent sync tasks</span>
          </el-form-item>
        </div>

        <!-- Default Sync Rules -->
        <div class="config-section">
          <h3>Default Sync Rules</h3>
          <el-divider />

          <el-form-item label="Sync Method">
            <el-select v-model="config.defaultSyncRules.method">
              <el-option label="Pull Sync" value="pull_sync" />
              <el-option label="Push Mirror" value="push_mirror" />
            </el-select>
          </el-form-item>

          <el-form-item label="Exclude Archived">
            <el-switch v-model="config.defaultSyncRules.excludeArchived" />
            <span class="input-hint">Exclude archived projects from sync</span>
          </el-form-item>

          <el-form-item label="Exclude Empty">
            <el-switch v-model="config.defaultSyncRules.excludeEmpty" />
            <span class="input-hint">Exclude empty repositories from sync</span>
          </el-form-item>

          <el-form-item label="Exclude Pattern">
            <el-input
              v-model="config.defaultSyncRules.excludePattern"
              placeholder="^temp/.*"
            />
            <span class="input-hint">Regular expression to exclude projects by path</span>
          </el-form-item>
        </div>

        <!-- Thresholds -->
        <div class="config-section">
          <h3>Thresholds</h3>
          <el-divider />

          <el-form-item label="Delay Warning">
            <el-input-number v-model="config.thresholds.delayWarningHours" :min="1" :max="168" />
            <span class="input-hint">hours</span>
          </el-form-item>

          <el-form-item label="Delay Critical">
            <el-input-number v-model="config.thresholds.delayCriticalHours" :min="1" :max="720" />
            <span class="input-hint">hours</span>
          </el-form-item>

          <el-form-item label="Max Retry Attempts">
            <el-input-number v-model="config.thresholds.maxRetryAttempts" :min="1" :max="10" />
          </el-form-item>

          <el-form-item label="Timeout">
            <el-input-number v-model="config.thresholds.timeoutSeconds" :min="60" :max="1800" />
            <span class="input-hint">seconds ({{ formatSeconds(config.thresholds.timeoutSeconds) }})</span>
          </el-form-item>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { configApi } from '@/api/config'
import type { SystemConfig, ConnectionTestResult } from '@/types'

const loading = ref(false)
const saving = ref(false)
const testingSource = ref(false)
const testingTarget = ref(false)

const sourceTestResult = ref<ConnectionTestResult | null>(null)
const targetTestResult = ref<ConnectionTestResult | null>(null)

const config = reactive<SystemConfig>({
  gitlab: {
    source: {
      url: '',
      token: ''
    },
    target: {
      url: '',
      token: ''
    }
  },
  scanSettings: {
    incrementalInterval: 300000,
    fullScanCron: '0 0 2 * * ?',
    enabled: true
  },
  syncSettings: {
    syncInterval: 300,
    concurrency: 5
  },
  defaultSyncRules: {
    method: 'pull_sync',
    excludeArchived: true,
    excludeEmpty: true,
    excludePattern: '^temp/.*'
  },
  thresholds: {
    delayWarningHours: 1,
    delayCriticalHours: 24,
    maxRetryAttempts: 3,
    timeoutSeconds: 300
  }
})

const loadConfig = async () => {
  loading.value = true
  try {
    const response = await configApi.getConfig()
    Object.assign(config, response.data)
  } catch (error) {
    ElMessage.error('Failed to load configuration')
    console.error('Load config failed:', error)
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  saving.value = true
  try {
    await configApi.updateConfig(config)
    ElMessage.success('Configuration saved successfully')
  } catch (error) {
    ElMessage.error('Failed to save configuration')
    console.error('Save config failed:', error)
  } finally {
    saving.value = false
  }
}

const testConnection = async (type: 'source' | 'target') => {
  if (type === 'source') {
    testingSource.value = true
    sourceTestResult.value = null
  } else {
    testingTarget.value = true
    targetTestResult.value = null
  }

  try {
    const response = await configApi.testConnection(type)
    if (type === 'source') {
      sourceTestResult.value = response.data
    } else {
      targetTestResult.value = response.data
    }

    if (response.data.connected) {
      ElMessage.success(`${type} GitLab connection successful`)
    } else {
      ElMessage.error(`${type} GitLab connection failed`)
    }
  } catch (error) {
    if (type === 'source') {
      sourceTestResult.value = { connected: false, error: 'Connection test failed' }
    } else {
      targetTestResult.value = { connected: false, error: 'Connection test failed' }
    }
    ElMessage.error(`Failed to test ${type} connection`)
    console.error('Test connection failed:', error)
  } finally {
    if (type === 'source') {
      testingSource.value = false
    } else {
      testingTarget.value = false
    }
  }
}

const formatInterval = (ms: number) => {
  const seconds = ms / 1000
  if (seconds < 60) return `${seconds}s`
  const minutes = seconds / 60
  if (minutes < 60) return `${minutes}m`
  const hours = minutes / 60
  return `${hours}h`
}

const formatSeconds = (seconds: number) => {
  if (seconds < 60) return `${seconds}s`
  const minutes = seconds / 60
  if (minutes < 60) return `${minutes}m`
  const hours = minutes / 60
  return `${hours}h`
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.configuration {
  padding: 20px;
}

.config-card {
  max-width: 1200px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.config-section {
  margin-bottom: 40px;
}

.config-section h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.instance-config {
  margin-bottom: 30px;
  padding: 20px;
  background-color: #f9f9f9;
  border-radius: 4px;
}

.instance-config h4 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px 0;
  color: #606266;
}

.input-hint {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}

.test-result {
  margin-left: 12px;
}
</style>
