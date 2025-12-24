<template>
  <el-card class="stat-card" :class="`stat-card-${type}`" shadow="hover" @click="handleClick">
    <div class="stat-content">
      <div class="stat-icon">
        <el-icon :size="40"><component :is="icon" /></el-icon>
      </div>
      <div class="stat-info">
        <div class="stat-title">{{ title }}</div>
        <div class="stat-value">{{ value }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
interface Props {
  title: string
  value: number
  icon: string
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

const props = defineProps<Props>()
const emit = defineEmits<{
  click: []
}>()

const handleClick = () => {
  emit('click')
}
</script>

<style scoped>
.stat-card {
  cursor: pointer;
  transition: all 0.3s;
  border-radius: 8px;
}

.stat-card:hover {
  transform: translateY(-4px);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-info {
  flex: 1;
}

.stat-title {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

/* Type colors */
.stat-card-primary .stat-icon {
  background-color: #e6f7ff;
  color: #1890ff;
}

.stat-card-primary .stat-value {
  color: #1890ff;
}

.stat-card-success .stat-icon {
  background-color: #f6ffed;
  color: #52c41a;
}

.stat-card-success .stat-value {
  color: #52c41a;
}

.stat-card-warning .stat-icon {
  background-color: #fffbe6;
  color: #faad14;
}

.stat-card-warning .stat-value {
  color: #faad14;
}

.stat-card-danger .stat-icon {
  background-color: #fff1f0;
  color: #f5222d;
}

.stat-card-danger .stat-value {
  color: #f5222d;
}

.stat-card-info .stat-icon {
  background-color: #f5f5f5;
  color: #8c8c8c;
}

.stat-card-info .stat-value {
  color: #8c8c8c;
}
</style>
