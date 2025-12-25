import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('@/views/Dashboard.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/projects',
      name: 'Projects',
      component: () => import('@/views/Projects.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/projects/:id',
      name: 'ProjectDetail',
      component: () => import('@/views/ProjectDetail.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/events',
      name: 'Events',
      component: () => import('@/views/SyncEvents.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/configuration',
      name: 'Configuration',
      component: () => import('@/views/Configuration.vue'),
      meta: { requiresAuth: true }
    }
  ]
})

// Global navigation guard
router.beforeEach(async (to, from, next) => {
  const { isAuthenticated, checkAuth } = useAuth()

  // Check authentication status if not already authenticated
  if (!isAuthenticated.value) {
    await checkAuth()
  }

  // Routes that require authentication
  if (to.meta.requiresAuth && !isAuthenticated.value) {
    // Redirect to login page
    next({
      path: '/login',
      query: { redirect: to.fullPath }
    })
  }
  // Already authenticated trying to access login page
  else if (to.path === '/login' && isAuthenticated.value) {
    // Redirect to dashboard or original destination
    const redirect = (to.query.redirect as string) || '/'
    next(redirect)
  }
  // Allow navigation
  else {
    next()
  }
})

export default router
