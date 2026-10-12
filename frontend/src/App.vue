<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auth, logout } from './stores/auth'
import { visibleNavigation } from './policy-adapter'

const route = useRoute(); const router = useRouter()
const isLogin = computed(() => route.path === '/login')
const navItems = [
  { path: '/dashboard', label: '管理首页', sessionOnly: true },
  { path: '/users', label: '用户管理', permission: 'rbac:user:read' },
  { path: '/departments', label: '部门架构', permission: 'rbac:department:read' },
  { path: '/roles', label: '角色权限', permission: 'rbac:role:read' }
]
const visibleNav = computed(() => visibleNavigation(auth, navItems))
async function signOut() {
  await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { type: 'warning' })
  logout(); ElMessage.success('已退出登录'); router.push('/login')
}
</script>

<template>
  <el-container v-if="!isLogin" class="app-shell">
    <el-aside width="220px" class="sidebar">
      <div class="brand"><span class="brand-mark">市</span><span>市政权限管理<small>RBAC Permission System</small></span></div>
      <el-menu :default-active="route.path" router background-color="#182230" text-color="#cbd5e1" active-text-color="#fff">
        <el-menu-item v-for="item in visibleNav" :key="item.path" :index="item.path">{{ item.label }}</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar"><div><span class="topbar-kicker">ADMINISTRATION /</span><strong>{{ route.meta.title || '工作台' }}</strong></div><div class="topbar-user"><span class="avatar">{{ auth.user?.name?.slice(0, 1) }}</span><span class="user-name">{{ auth.user?.name }}</span><el-button link @click="signOut">退出登录</el-button></div></el-header>
      <el-main class="page-content"><router-view /></el-main>
    </el-container>
  </el-container>
  <router-view v-else />
</template>
