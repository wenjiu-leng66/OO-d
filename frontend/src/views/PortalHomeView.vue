<script setup>
// 门户首页（PORTAL-01）：按“查看”权限列出可进入的系统；没有任何权限时提示联系管理员（UC-01 3a）。
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { hasPermission } from '../policy-adapter'
import { portalMenu, visibleAdminItems } from '../navigation'

const router = useRouter()
const menu = computed(() => portalMenu(auth))
const functionCount = computed(() => menu.value.reduce((sum, system) => sum + system.count, 0))
const adminNav = computed(() => visibleAdminItems(auth))
const enabledRoles = computed(() => auth.roles.filter(role => role.enabled))
const disabledRoles = computed(() => auth.roles.filter(role => !role.enabled))
const firstPath = system => system.modules[0].functions[0].path
</script>

<template>
  <div>
    <section class="home-welcome">
      <div>
        <p class="home-eyebrow">门户首页 / PORTAL</p>
        <h2>你好，{{ auth.user?.name }}</h2>
        <p class="home-description">左侧菜单只列出你有“查看”权限的功能点，按 系统 &gt; 模块 &gt; 功能点 组织。</p>
        <div class="home-identity">
          <span>账号：{{ auth.user?.username }}</span>
          <span>部门：{{ auth.user?.departmentPath }}</span>
          <span>岗位：{{ auth.user?.position || '—' }}</span>
          <span>角色：{{ enabledRoles.map(role => role.name).join('、') || '无' }}</span>
        </div>
      </div>
      <div class="home-emblem" aria-hidden="true"><span>市</span><small>统一权限管理</small></div>
    </section>

    <el-alert v-if="disabledRoles.length" class="block-gap top-gap" type="info" :closable="false" show-icon
      :title="`角色“${disabledRoles.map(role => role.name).join('、')}”已被停用，它的权限对你不生效。`" />

    <template v-if="menu.length || adminNav.length">
      <div class="home-section-heading"><h3>常用入口</h3><span>我的权限共 {{ auth.permissions.length }} 条</span></div>
      <div class="shortcut-grid">
        <button class="shortcut-card" @click="router.push('/me/permissions')"><span class="shortcut-symbol">权</span><span class="shortcut-copy"><strong>我的权限</strong><small>每条权限的数据范围和来源角色</small></span><span class="shortcut-arrow">→</span></button>
        <button v-if="hasPermission(auth, 'hr:f046:read')" class="shortcut-card" @click="router.push('/ledger')"><span class="shortcut-symbol">台</span><span class="shortcut-copy"><strong>员工台账管理</strong><small>真实业务：按数据范围查看和编辑</small></span><span class="shortcut-arrow">→</span></button>
        <button v-if="adminNav.length" class="shortcut-card" @click="router.push(adminNav[0].path)"><span class="shortcut-symbol">管</span><span class="shortcut-copy"><strong>管理后台</strong><small>{{ adminNav.map(item => item.label).join(' · ') }}</small></span><span class="shortcut-arrow">→</span></button>
      </div>

      <div class="home-section-heading"><h3>可用系统</h3><span>{{ menu.length }} 个系统 · {{ functionCount }} 个功能点</span></div>
      <div class="system-grid">
        <button v-for="system in menu" :key="system.code" class="system-card" @click="router.push(firstPath(system))">
          <span class="system-code">{{ system.code }}</span>
          <strong>{{ system.name }}</strong>
          <small>{{ system.modules.length }} 个模块 · {{ system.count }} 个功能点</small>
        </button>
      </div>
    </template>
    <el-empty v-else class="top-gap" description="暂无权限，请联系管理员">
      <el-button @click="router.push('/me/permissions')">查看我的权限</el-button>
    </el-empty>
  </div>
</template>
