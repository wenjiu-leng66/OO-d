<script setup>
// 我的权限（PORTAL-03）：当前用户的全部有效权限、数据范围和来源角色。
import { computed, ref } from 'vue'
import { auth } from '../stores/auth'
import { describePermission, actionLabel, scopeLabel, systems } from '../api/fixtures/catalog'
import ScopeTag from '../components/ScopeTag.vue'

const keyword = ref(''); const systemCode = ref(''); const page = ref(1); const size = 15
const rows = computed(() => auth.grants.map(grant => {
  const info = describePermission(grant.code) || {}
  return { ...grant, ...info, scope: grant.scope, actionLabel: info.systemCode === 'rbac' ? info.name : actionLabel(info.action) }
}))
const systemOptions = computed(() => [...systems.filter(item => rows.value.some(row => row.systemCode === item.code)),
  ...(rows.value.some(row => row.systemCode === 'rbac') ? [{ code: 'rbac', name: '管理后台（rbac:*）' }] : [])])
const filtered = computed(() => rows.value.filter(row =>
  (!systemCode.value || row.systemCode === systemCode.value) &&
  (!keyword.value || `${row.code}${row.name}${row.module}`.includes(keyword.value.trim()))))
const paged = computed(() => filtered.value.slice((page.value - 1) * size, page.value * size))
const scopeCounts = computed(() => ['ALL', 'DEPT', 'SELF'].map(scope => ({ scope, count: rows.value.filter(row => row.scope === scope).length })))
</script>

<template>
  <div>
    <div class="page-heading"><div><h2>我的权限</h2><p>有效权限 = 已分配且启用的角色授权的并集；同一权限码被多个角色授予时取最大范围。</p></div></div>
    <div class="stat-row">
      <el-card><b>{{ rows.length }}</b><span>条有效权限</span></el-card>
      <el-card v-for="item in scopeCounts" :key="item.scope"><b>{{ item.count }}</b><span>范围为“{{ scopeLabel(item.scope) }}”</span></el-card>
      <el-card class="role-card"><span>我的角色</span><div class="role-chips"><el-tag v-for="role in auth.roles" :key="role.id" :type="role.enabled ? 'primary' : 'info'">{{ role.name }}{{ role.enabled ? '' : '（已停用，不生效）' }}</el-tag><span v-if="!auth.roles.length" class="muted">未分配角色</span></div></el-card>
    </div>
    <el-card class="filter-card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索权限码、功能点或模块" clearable @input="page = 1" />
        <el-select v-model="systemCode" placeholder="全部系统" clearable @change="page = 1"><el-option v-for="item in systemOptions" :key="item.code" :label="item.name" :value="item.code" /></el-select>
      </div>
    </el-card>
    <el-card class="table-card">
      <el-empty v-if="!filtered.length" :description="rows.length ? '暂无匹配权限' : '暂无权限，请联系管理员'" />
      <template v-else>
        <el-table :data="paged" stripe>
          <el-table-column label="权限码" min-width="170"><template #default="{ row }"><code>{{ row.code }}</code></template></el-table-column>
          <el-table-column prop="systemName" label="系统" min-width="150" />
          <el-table-column prop="module" label="模块" min-width="130" />
          <el-table-column prop="name" label="功能点" min-width="150" />
          <el-table-column prop="actionLabel" label="操作" min-width="90" />
          <el-table-column label="范围" width="90"><template #default="{ row }"><ScopeTag :scope="row.scope" /></template></el-table-column>
          <el-table-column label="来源角色" min-width="200"><template #default="{ row }">
            <el-tag v-for="source in row.sources" :key="source.roleId + source.scope" class="tag-gap" size="small" effect="plain">{{ source.roleName }}：{{ scopeLabel(source.scope) }}</el-tag>
            <div v-if="row.via" class="muted small">映射自 {{ row.via.join('、') }}</div>
          </template></el-table-column>
        </el-table>
        <div class="pagination"><el-pagination v-model:current-page="page" :page-size="size" :total="filtered.length" layout="total, prev, pager, next" /></div>
      </template>
      <p class="muted small">阶段二起，继承来的权限会标明“继承自哪个角色”（ROLE-07）。</p>
    </el-card>
  </div>
</template>
