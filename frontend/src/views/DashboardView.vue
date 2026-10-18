<script setup>
// 管理概览（成员 2 的管理首页）：统计按当前用户的管理范围计算；快捷入口按权限显示。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { api } from '../api/mock'
import { hasPermission, permissionScope } from '../policy-adapter'
import { scopeLabel } from '../api/fixtures/catalog'

const router = useRouter()
const overview = ref(null)
const loading = ref(false)
const failed = ref(false)
const userScope = computed(() => permissionScope(auth, 'rbac:user:read'))
async function load() {
  loading.value = true; failed.value = false
  try { overview.value = await api.getOverview() }
  catch { failed.value = true }
  finally { loading.value = false }
}
const metrics = computed(() => [
  { key: 'totalUsers', label: userScope.value === 'ALL' ? '系统总用户数' : '管理范围内用户数', note: '当前管理范围内已登记的账号', symbol: '人', tone: 'blue' },
  { key: 'disabledUsers', label: '停用账号数', note: '停用后不能登录，已登录会话下一次请求失效', symbol: '停', tone: 'amber' },
  { key: 'pendingPassword', label: '待修改初始密码', note: '新建或被重置密码、尚未修改的账号', symbol: '密', tone: 'violet' }
])
const shortcuts = [
  { title: '新增用户', description: '录入工号、姓名、部门，使用初始密码', symbol: '＋', permissions: ['rbac:user:read', 'rbac:user:create'], path: '/admin/users?create=1' },
  { title: '分配角色', description: '打开用户详情，整组替换角色', symbol: '角', permissions: ['rbac:user:read', 'rbac:user:assign-role'], path: '/admin/users' },
  { title: '角色授权', description: '勾选功能点操作并选择数据范围', symbol: '权', permissions: ['rbac:role:read', 'rbac:permission:read'], path: '/admin/grants' },
  { title: '查看组织架构', description: '浏览部门树和部门人员', symbol: '部', permissions: ['rbac:department:read'], path: '/admin/departments' }
]
const visibleShortcuts = computed(() => shortcuts.filter(item => item.permissions.every(code => hasPermission(auth, code))))
onMounted(load)
</script>

<template>
  <div class="management-home">
    <section class="home-welcome">
      <div><p class="home-eyebrow">管理概览 / OVERVIEW</p><h2>你好，{{ auth.user?.name }}</h2><p class="home-description">从账号到授权，让日常管理更清晰。</p><div class="home-identity"><span>角色：{{ auth.roles.map(role => role.name).join('、') }}</span><span>用户管理范围：{{ scopeLabel(userScope) }}{{ userScope === 'DEPT' ? `（${auth.user?.departmentPath} 及下级）` : '' }}</span></div></div>
      <div class="home-emblem" aria-hidden="true"><span>市</span><small>统一权限管理</small></div>
    </section>
    <div class="home-section-heading"><h3>系统概览</h3><el-button link type="primary" :loading="loading" @click="load">刷新数据</el-button></div>
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-result v-else-if="failed" icon="error" title="概览加载失败"><template #extra><el-button @click="load">重新加载</el-button></template></el-result>
    <div v-else class="overview-grid"><el-card v-for="metric in metrics" :key="metric.key" :class="['overview-card', metric.tone]" shadow="never"><div class="metric-top"><span>{{ metric.label }}</span><span class="metric-symbol">{{ metric.symbol }}</span></div><div class="metric-value">{{ overview?.[metric.key] ?? 0 }}<small>个</small></div><p>{{ metric.note }}</p></el-card></div>
    <template v-if="visibleShortcuts.length"><div class="home-section-heading"><h3>常用操作</h3><span>直接进入管理流程</span></div>
    <div class="shortcut-grid"><button v-for="shortcut in visibleShortcuts" :key="shortcut.path" class="shortcut-card" @click="router.push(shortcut.path)"><span class="shortcut-symbol">{{ shortcut.symbol }}</span><span class="shortcut-copy"><strong>{{ shortcut.title }}</strong><small>{{ shortcut.description }}</small></span><span class="shortcut-arrow">→</span></button></div>
    </template>
    <div class="home-reminder"><span>操作提醒</span><p>没有权限的按钮置灰；删除和撤销需要二次确认；后端对每次请求重新检查权限。</p><small>当前概览使用合成演示数据</small></div>
  </div>
</template>
