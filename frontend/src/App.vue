<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auth, logout, switchIdentity } from './stores/auth'
import { api } from './api/mock'
import { functionById } from './api/fixtures/catalog'
import { portalMenu, visibleAdminItems, visibleLaterStageItems } from './navigation'

const route = useRoute(); const router = useRouter()
const mobileNavOpen = ref(false)
const sidebarElement = ref()
const noticeElement = ref()
const noticeHeight = ref(72)
const iconOpacity = ref(0)
let noticeObserver
// 与 CSS 的 720/800px 断点配合：窄屏渐显图标栏，宽屏恢复时清掉抽屉展开状态。
function updateNavigationSize() {
  iconOpacity.value = Math.max(0, Math.min(1, (800 - window.innerWidth) / 80))
  if (window.innerWidth > 800) mobileNavOpen.value = false
}
const compactNav = computed(() => [
  { label: '首页', path: '/portal', icon: 'M3 10 12 3l9 7M5 9v12h5v-7h4v7h5V9' },
  { label: '我的权限', path: '/me/permissions', icon: 'M14 7a5 5 0 1 1-3 9l-5 5H3v-3l5-5a5 5 0 0 1 6-6ZM16 9h.01' },
  ...(menu.value.length ? [{ label: '业务系统', group: 'business', icon: 'M3 3h7v7H3ZM14 3h7v7h-7ZM3 14h7v7H3ZM14 14h7v7h-7Z' }] : []),
  ...(adminNav.value.length ? [{ label: '管理后台', group: 'admin', icon: 'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7ZM8 12l3 3 5-6' }] : []),
  ...(laterNav.value.length ? [{ label: '后续阶段（静态原型）', group: 'later', icon: 'M4 5h16v16H4ZM8 3v4M16 3v4M4 10h16M8 14h3M8 17h7' }] : [])
])
// 切组后等 Vue 完成渲染再计算标题位置，保证移动导航滚动定位到正确分组。
async function selectCompact(item) {
  if (item.path) { mobileNavOpen.value = false; router.push(item.path); return }
  if (item.group && mobileNavOpen.value && openGroup.value === item.group) { mobileNavOpen.value = false; openGroup.value = null; return }
  openGroup.value = item.group
  mobileNavOpen.value = true
  await nextTick()
  const sidebar = sidebarElement.value?.$el
  const heading = sidebar?.querySelector(`[data-nav-group="${item.group}"]`)
  if (heading) sidebar.scrollTop = Math.max(0, heading.offsetTop - sidebar.offsetTop - 16)
}
const openGroup = ref(null)
function compactActive(item) {
  if (item.path) return route.path === item.path
  const laterPaths = ['/admin/hierarchy', '/admin/constraints', '/admin/logs']
  if (item.group === 'later') return route.path.startsWith('/session/') || laterPaths.includes(route.path)
  if (item.group === 'admin') return route.path.startsWith('/admin') && !laterPaths.includes(route.path)
  if (item.group === 'business') return route.path === '/ledger' || route.path.startsWith('/portal/f/')
  return false
}
function closeOnEscape(event) { if (event.key === 'Escape') mobileNavOpen.value = false }
onMounted(() => {
  window.addEventListener('keydown', closeOnEscape)
  window.addEventListener('resize', updateNavigationSize)
  updateNavigationSize()
  // 顶部提示条会随换行改变高度；测量后写入 CSS 变量，防止固定导航覆盖提示或正文。
  noticeObserver = new ResizeObserver(() => { noticeHeight.value = noticeElement.value.getBoundingClientRect().height })
  noticeObserver.observe(noticeElement.value)
})
// 对称释放全局监听和观察器，避免热更新/重新挂载产生重复事件或保留旧 DOM。
onUnmounted(() => {
  window.removeEventListener('keydown', closeOnEscape)
  window.removeEventListener('resize', updateNavigationSize)
  noticeObserver?.disconnect()
})
const bare = computed(() => route.path === '/login' || !auth.isAuthenticated)
const forcedPassword = computed(() => auth.mustChangePassword)
// 菜单直接依赖当前 auth 授权，刷新会话/切换身份后统一重算，无额外“管理员全开放”分支。
const menu = computed(() => portalMenu(auth))
const adminNav = computed(() => visibleAdminItems(auth))
const laterNav = computed(() => visibleLaterStageItems(auth))
const pageTitle = computed(() => route.params.fid ? functionById.get(String(route.params.fid))?.name || '功能点' : route.meta.title || '门户')
const area = computed(() => route.meta.area === 'admin' ? 'ADMINISTRATION /' : 'PORTAL /')
const roleText = computed(() => auth.roles.length ? auth.roles.map(role => role.name).join('、') : '未分配角色')
watch(() => route.path, () => { mobileNavOpen.value = false; openGroup.value = null })

async function signOut() {
  await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { type: 'warning' })
  logout(); ElMessage.success('已退出登录'); router.push('/login')
}
// 仅切换前端 mock 身份；Swagger/真实后端的 Cookie 会话独立，不随这个控件切换。
async function onIdentity(username) {
  try {
    await switchIdentity(username)
    ElMessage.success(`已切换为 ${auth.user.username}（${roleText.value}）`)
    router.push(auth.mustChangePassword ? '/password' : '/portal')
  } catch (error) { ElMessage.error(error.message) }
}
</script>

<template>
  <div ref="noticeElement" class="stage1-notice" role="note">
    <span><strong>第一阶段 · 界面原型</strong> 合成数据演示，刷新后恢复</span>
    <nav aria-label="第一阶段验收入口">
      <a href="/swagger-ui/index.html?url=/api-design/openapi.yaml" target="_blank" rel="noopener">后端接口调试</a>
      <a href="/api-design/openapi.yaml" target="_blank" rel="noopener">API 设计</a>
      <a href="/stage1/" target="_blank" rel="noopener">验收说明</a>
    </nav>
  </div>
  <el-container v-if="!bare" class="app-shell" :style="{ '--notice-height': noticeHeight + 'px', '--icon-opacity': iconOpacity }">
    <nav class="compact-sidebar" aria-label="快捷导航">
      <span class="brand-mark">市</span>
      <template v-if="!forcedPassword">
        <el-tooltip v-for="item in compactNav" :key="item.label" :content="item.label" placement="right">
          <button type="button" :aria-label="item.label" :aria-expanded="item.group ? mobileNavOpen && openGroup === item.group : undefined" :class="{ active: compactActive(item) || (item.group === openGroup && mobileNavOpen) }" @click="selectCompact(item)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="item.icon" /></svg>
          </button>
        </el-tooltip>
      </template>
    </nav>
    <button v-if="mobileNavOpen" class="sidebar-backdrop" type="button" aria-label="关闭导航菜单" @click="mobileNavOpen = false"></button>
    <el-aside ref="sidebarElement" width="248px" class="sidebar" :class="{ 'sidebar-open': mobileNavOpen }">
      <div class="brand"><span class="brand-mark">市</span><span>市政权限管理<small>RBAC Permission System</small></span></div>
      <el-menu v-if="!forcedPassword" :default-active="route.path" router unique-opened background-color="transparent" text-color="#cbd5e1" active-text-color="#fff" @select="mobileNavOpen = false">
        <div class="menu-group">门户</div>
        <el-menu-item index="/portal">首页</el-menu-item>
        <el-menu-item index="/me/permissions">我的权限</el-menu-item>
        <template v-if="menu.length">
          <div class="menu-group" data-nav-group="business">业务系统 <small>{{ menu.length }}</small></div>
          <el-sub-menu v-for="system in menu" :key="system.code" :index="'sys-' + system.code">
            <template #title><span class="menu-ellipsis" :title="system.name">{{ system.name }}</span></template>
            <el-sub-menu v-for="mod in system.modules" :key="mod.name" :index="'mod-' + system.code + '-' + mod.name">
              <template #title><span class="menu-ellipsis">{{ mod.name }}</span></template>
              <el-menu-item v-for="fn in mod.functions" :key="fn.id" :index="fn.path"><span class="menu-ellipsis" :title="fn.name">{{ fn.name }}</span></el-menu-item>
            </el-sub-menu>
          </el-sub-menu>
        </template>
        <template v-if="adminNav.length">
          <div class="menu-group" data-nav-group="admin">管理后台</div>
          <el-menu-item v-for="item in adminNav" :key="item.path" :index="item.path">{{ item.label }}</el-menu-item>
        </template>
        <template v-if="laterNav.length">
          <div class="menu-group" data-nav-group="later">后续阶段（静态原型）</div>
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
