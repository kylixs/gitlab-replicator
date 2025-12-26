<template>
  <!-- Login page without sidebar -->
  <router-view v-if="isLoginPage" />

  <!-- Main app layout with sidebar -->
  <el-container v-else class="app-container">
    <el-aside width="200px" class="sidebar">
      <div class="logo">
        <h2>GitLab Mirror</h2>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#ffffff"
        active-text-color="#1890ff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>Dashboard</span>
        </el-menu-item>
        <el-menu-item index="/projects">
          <el-icon><FolderOpened /></el-icon>
          <span>Projects</span>
        </el-menu-item>
        <el-menu-item index="/events">
          <el-icon><List /></el-icon>
          <span>Sync Events</span>
        </el-menu-item>
        <el-menu-item index="/results">
          <el-icon><Document /></el-icon>
          <span>Sync Results</span>
        </el-menu-item>
        <el-menu-item index="/configuration">
          <el-icon><Setting /></el-icon>
          <span>Configuration</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-content">
          <h3>{{ pageTitle }}</h3>
        </div>
        <div class="header-actions">
          <el-space :size="16">
            <!-- Auto Refresh Settings -->
            <el-popover placement="bottom" :width="300" trigger="click">
              <template #reference>
                <el-button
                  circle
                  :type="isAutoRefreshEnabled ? 'primary' : ''"
                  :class="{ 'refresh-active': isAutoRefreshEnabled }"
                >
                  <el-icon :class="{ 'is-loading': isAutoRefreshEnabled }">
                    <Refresh />
                  </el-icon>
                </el-button>
              </template>
              <div class="refresh-settings">
                <h4>Auto Refresh Settings</h4>
                <el-form label-position="top">
                  <el-form-item label="Enable Auto Refresh">
                    <el-switch v-model="isAutoRefreshEnabled" />
                  </el-form-item>
                  <el-form-item label="Refresh Interval (seconds)">
                    <el-input-number
                      v-model="refreshInterval"
                      :min="5"
                      :max="300"
                      :step="5"
                      :disabled="!isAutoRefreshEnabled"
                    />
                  </el-form-item>
                </el-form>
              </div>
            </el-popover>

            <!-- User Info & Logout -->
            <el-dropdown @command="handleUserCommand">
              <el-button circle>
                <el-icon><User /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item disabled>
                    <strong>{{ currentUser?.username || 'User' }}</strong>
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">
                    <el-icon><SwitchButton /></el-icon>
                    Logout
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-space>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuth } from '@/composables/useAuth'
import { useAutoRefresh } from '@/composables/useAutoRefresh'

const route = useRoute()
const router = useRouter()
const { logout, currentUser } = useAuth()
const { isAutoRefreshEnabled, refreshInterval } = useAutoRefresh()

const isLoginPage = computed(() => route.path === '/login')

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/projects/')) return '/projects'
  return path
})

const pageTitle = computed(() => {
  const routeName = route.name as string
  const titles: Record<string, string> = {
    'Dashboard': 'Dashboard',
    'Projects': 'Projects',
    'ProjectDetail': 'Project Detail',
    'Events': 'Sync Events',
    'Results': 'Sync Results',
    'Configuration': 'Configuration'
  }
  return titles[routeName] || 'GitLab Mirror'
})

const handleUserCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm(
        'Are you sure you want to logout?',
        'Confirm Logout',
        {
          confirmButtonText: 'Logout',
          cancelButtonText: 'Cancel',
          type: 'warning'
        }
      )
      await logout()
      router.push('/login')
    } catch (error) {
      // User cancelled
    }
  }
}
</script>

<style scoped>
.app-container {
  height: 100vh;
}

.sidebar {
  background-color: #001529;
  overflow-y: auto;
}

.logo {
  padding: 20px;
  color: #ffffff;
  text-align: center;
  border-bottom: 1px solid #1890ff;
}

.logo h2 {
  margin: 0;
  font-size: 18px;
}

.header {
  background-color: #ffffff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-content {
  flex: 0;
}

.header-content h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  align-items: center;
}

.refresh-settings h4 {
  margin-top: 0;
  margin-bottom: 16px;
  font-size: 16px;
}

.main-content {
  background-color: #f0f2f5;
  padding: 24px;
  overflow-y: auto;
}
</style>
