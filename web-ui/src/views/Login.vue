<template>
  <div class="login-container">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="card-header">
          <h2>GitLab Mirror</h2>
          <p>Sign in to your account</p>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="Username" prop="username">
          <el-input
            v-model="form.username"
            placeholder="Enter your username"
            size="large"
            :disabled="isLocked || isLoading"
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="Password" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="Enter your password"
            size="large"
            :disabled="isLocked || isLoading"
            show-password
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <!-- Account Locked Alert -->
        <el-alert
          v-if="isLocked"
          type="error"
          :closable="false"
          show-icon
          class="alert-box"
        >
          <template #title>
            Account Locked
          </template>
          <div>
            Too many failed login attempts. Please try again in {{ lockoutSeconds }} seconds.
          </div>
        </el-alert>

        <!-- Failure Count Warning -->
        <el-alert
          v-else-if="failureCount >= 3"
          type="warning"
          :closable="false"
          show-icon
          class="alert-box"
        >
          <template #title>
            Warning
          </template>
          <div>
            {{ failureCount }} failed login attempts. Your account will be locked after {{ 10 - failureCount }} more failed attempts.
          </div>
        </el-alert>

        <!-- Error Message -->
        <el-alert
          v-if="error && !isLocked"
          type="error"
          :closable="true"
          show-icon
          class="alert-box"
          @close="clearError"
        >
          {{ error }}
        </el-alert>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-button"
            :loading="isLoading"
            :disabled="isLocked"
            @click="handleLogin"
          >
            <span v-if="!isLoading">Sign In</span>
            <span v-else>Signing In...</span>
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <p>Default credentials: admin / Admin@123</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuth } from '@/composables/useAuth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const { login, isLoading, error, failureCount, lockoutSeconds, isLocked, clearError } = useAuth()

const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: 'Please enter username', trigger: 'blur' },
    { min: 3, max: 50, message: 'Username should be 3-50 characters', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please enter password', trigger: 'blur' },
    { min: 6, message: 'Password should be at least 6 characters', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return

  // Validate form
  try {
    await formRef.value.validate()
  } catch (err) {
    return
  }

  // Attempt login
  const success = await login(form.username, form.password)

  if (success) {
    ElMessage.success('Login successful')
    // Redirect to dashboard
    router.push('/')
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  border-radius: 12px;
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0 0 8px 0;
  font-size: 28px;
  color: #303133;
}

.card-header p {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

.login-button {
  width: 100%;
  margin-top: 8px;
}

.alert-box {
  margin-bottom: 16px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #dcdfe6;
}

.login-footer p {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}
</style>
