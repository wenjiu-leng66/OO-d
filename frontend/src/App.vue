<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auth, logout, switchIdentity } from './stores/auth'
import { api } from './api/mock'
import { functionById } from './api/fixtures/catalog'
import { portalMenu, visibleAdminItems, visibleLaterStageItems } from './navigation'

const route = useRoute(); const router = useRouter()
const bare = computed(() => route.path === '/login' || !auth.isAuthenticated)
const forcedPassword = computed(() => auth.mustChangePassword)
const menu = computed(() => portalMenu(auth))
const adminNav = computed(() => visibleAdminItems(auth))
const laterNav = computed(() => visibleLaterStageItems(auth))
const pageTitle = computed(() => route.params.fid ? functionById.get(String(route.params.fid))?.name || '功能点' : route.meta.title || '门户')
const area = computed(() => route.meta.area === 'admin' ? 'ADMINISTRATION /' : 'PORTAL /')
const roleText = computed(() => auth.roles.length ? auth.roles.map(role => role.name).join('、') : '未分配角色')

async function signOut() {
  await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { type: 'warning' })
  logout(); ElMessage.success('已退出登录'); router.push('/login')
}
async function onIdentity(username) {
  try {
    await switchIdentity(username)
    ElMessage.success(`已切换为 ${auth.user.username}（${roleText.value}）`)
    router.push(auth.mustChangePassword ? '/password' : '/portal')
  } catch (error) { ElMessage.error(error.message) }
}
</script>

<template>
  <el-container v-if="!bare" class="app-shell">
    <el-aside width="248px" class="sidebar">
      <div class="brand"><span class="brand-mark">市</span><span>市政权限管理<small>RBAC Permission System</small></span></div>
      <el-menu v-if="!forcedPassword" :default-active="route.path" router unique-opened background-color="transparent" text-color="#cbd5e1" active-text-color="#fff">
        <div class="menu-group">门户</div>
        <el-menu-item index="/portal">首页</el-menu-item>
        <el-menu-item index="/me/permissions">我的权限</el-menu-item>
        <template v-if="menu.length">
          <div class="menu-group">业务系统 <small>{{ menu.length }}</small></div>
          <el-sub-menu v-for="system in menu" :key="system.code" :index="'sys-' + system.code">
            <template #title><span class="menu-ellipsis" :title="system.name">{{ system.name }}</span></template>
            <el-sub-menu v-for="mod in system.modules" :key="mod.name" :index="'mod-' + system.code + '-' + mod.name">
              <template #title><span class="menu-ellipsis">{{ mod.name }}</span></template>
              <el-menu-item v-for="fn in mod.functions" :key="fn.id" :index="fn.path"><span class="menu-ellipsis" :title="fn.name">{{ fn.name }}</span></el-menu-item>
            </el-sub-menu>
          </el-sub-menu>
        </template>
        <template v-if="adminNav.length">
          <div class="menu-group">管理后台</div>
          <el-menu-item v-for="item in adminNav" :key="item.path" :index="item.path">{{ item.label }}</el-menu-item>
        </template>
        <template v-if="laterNav.length">
          <div class="menu-group">后续阶段（静态原型）</div>
          <el-menu-item v-for="item in laterNav" :key="item.path" :index="item.path">{{ item.label }}<span class="stage-chip">阶段{{ item.stage === 2 ? '二' : '三' }}</span></el-menu-item>
        </template>
      </el-menu>
      <p v-else class="sidebar-note">首次登录或密码被重置，请先修改密码。</p>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="topbar-title"><span class="topbar-kicker">{{ area }}</span><strong>{{ pageTitle }}</strong></div>
        <div class="topbar-user">
          <el-dropdown trigger="click" @command="onIdentity">
            <el-button size="small" plain>切换演示身份 ▾</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="item in api.demo.identities" :key="item.username" :command="item.username" :disabled="item.username === auth.user?.username">
                  <b class="identity-label">{{ item.label }}</b><code>{{ item.username }}</code>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span class="avatar">{{ auth.user?.name?.slice(-1) }}</span>
          <span class="user-name"><b>{{ auth.user?.name }}</b><small>{{ auth.user?.username }} · {{ roleText }}</small></span>
          <el-button link @click="router.push('/password')">修改密码</el-button>
          <el-button link @click="signOut">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="page-content"><router-view :key="route.path" /></el-main>
    </el-container>
  </el-container>
  <router-view v-else />
</template>
