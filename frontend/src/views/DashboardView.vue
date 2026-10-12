<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { api } from '../api/mock'
import { hasPermission } from '../policy-adapter'

const router = useRouter()
const overview = ref(null)
const loading = ref(false)
const failed = ref(false)
const canReadOverview = computed(() => hasPermission(auth, 'rbac:user:read'))
async function load() {
  if (!canReadOverview.value) { overview.value = null; return }
  loading.value = true; failed.value = false
  try { overview.value = await api.getOverview() }
  catch { failed.value = true }
  finally { loading.value = false }
}
const metrics = [
  { key: 'totalUsers', label: auth.user?.roleIds.includes('role-admin') ? '系统总用户数' : '本部门用户数', note: '当前管理范围内已登记的账号', symbol: '人', tone: 'blue' },
  { key: 'temporaryUsers', label: '临时账号数', note: '临时运维人员账号', symbol: '临', tone: 'amber' },
  { key: 'totalRoles', label: auth.user?.roleIds.includes('role-admin') ? '系统角色数' : '本部门已分配角色数', note: auth.user?.roleIds.includes('role-admin') ? '当前角色目录中的角色' : '本部门用户已分配的角色，去重统计', symbol: '角', tone: 'violet' }
]
const shortcuts = [
  { title: '添加员工账号', description: '录入基本信息，选择组织和角色', symbol: '＋', permissions: ['rbac:user:read', 'rbac:user:create'], path: '/users?create=employee' },
  { title: '添加临时运维账号', description: '按需创建账号，手动分配角色', symbol: '临', permissions: ['rbac:user:read', 'rbac:user:create'], path: '/users?create=temporary-ops' },
  { title: '查询用户权限', description: '打开用户详情，查看角色及权限', symbol: '查', permissions: ['rbac:user:read'], path: '/users' },
  { title: '查看组织架构', description: '浏览总公司、分公司和部门', symbol: '部', permissions: ['rbac:department:read'], path: '/departments' }
]
const visibleShortcuts = computed(() => shortcuts.filter(item => item.permissions.every(code => hasPermission(auth, code))))
function openShortcut(shortcut) {
  if (shortcut.permissions.every(code => hasPermission(auth, code))) router.push(shortcut.path)
}
onMounted(load)
</script>

<template>
  <div class="management-home">
    <section class="home-welcome">
      <div><p class="home-eyebrow">管理首页 / OVERVIEW</p><h2>你好，{{ auth.user?.name || '管理员' }}</h2><p class="home-description">从账号到授权，让日常管理更清晰。</p><div class="home-identity"><span>当前身份：{{ auth.user?.roleIds.includes("role-admin") ? "超级管理员" : "部门管理员" }}</span><span>管理范围：{{ auth.user?.roleIds.includes("role-admin") ? "全部组织" : auth.user?.branch + " / " + auth.user?.departmentName }}</span></div></div>
      <div class="home-emblem" aria-hidden="true"><span>市</span><small>统一权限管理</small></div>
    </section>
    <template v-if="canReadOverview"><div class="home-section-heading"><h3>系统概览</h3><el-button link type="primary" :loading="loading" @click="load">刷新数据</el-button></div>
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-result v-else-if="failed" icon="error" title="概览加载失败"><template #extra><el-button @click="load">重新加载</el-button></template></el-result>
    <div v-else class="overview-grid"><el-card v-for="metric in metrics" :key="metric.key" :class="['overview-card', metric.tone]" shadow="never"><div class="metric-top"><span>{{ metric.label }}</span><span class="metric-symbol">{{ metric.symbol }}</span></div><div class="metric-value">{{ overview?.[metric.key] ?? 0 }}<small>个</small></div><p>{{ metric.note }}</p></el-card></div>
    </template><template v-if="visibleShortcuts.length"><div class="home-section-heading"><h3>常用操作</h3><span>直接进入管理流程</span></div>
    <div class="shortcut-grid"><button v-for="shortcut in visibleShortcuts" :key="shortcut.path" class="shortcut-card" @click="openShortcut(shortcut)"><span class="shortcut-symbol">{{ shortcut.symbol }}</span><span class="shortcut-copy"><strong>{{ shortcut.title }}</strong><small>{{ shortcut.description }}</small></span><span class="shortcut-arrow">→</span></button></div>
    </template><div class="home-reminder"><span>操作提醒</span><p>临时运维结束后，请手动撤销权限或停用账号。</p><small>当前概览使用合成演示数据</small></div>
  </div>
</template>
