<script setup>
// 功能点占位页（PORTAL-02）：6 个操作按钮按权限可用或置灰，并显示每个操作的数据范围。
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auth } from '../stores/auth'
import { permissionScope } from '../policy-adapter'
import { functionById, actions, scopeLabel } from '../api/fixtures/catalog'
import ScopeTag from '../components/ScopeTag.vue'
import NotFoundView from './NotFoundView.vue'

const route = useRoute()
const fn = computed(() => functionById.get(String(route.params.fid)))
const rows = computed(() => fn.value ? actions.map(action => {
  const code = `${fn.value.systemCode}:${fn.value.id}:${action.code}`
  return { ...action, code, scope: permissionScope(auth, code) }
}) : [])
const sources = code => auth.grants.find(grant => grant.code === code)?.sources || []
function run(row) {
  ElMessage.info(`占位：${row.label}。正式系统由后端检查 ${row.code}，数据范围为“${scopeLabel(row.scope)}”。`)
}
</script>

<template>
  <NotFoundView v-if="!fn" />
  <div v-else>
    <el-breadcrumb separator="/" class="block-gap"><el-breadcrumb-item>{{ fn.systemName }}</el-breadcrumb-item><el-breadcrumb-item>{{ fn.module }}</el-breadcrumb-item><el-breadcrumb-item>{{ fn.name }}</el-breadcrumb-item></el-breadcrumb>
    <div class="page-heading"><div><h2>{{ fn.name }}</h2><p>{{ fn.description }} · 功能点编号 <code>{{ fn.id }}</code></p></div><el-tag type="info" effect="plain">占位页</el-tag></div>
    <el-alert class="block-gap" type="info" :closable="false" show-icon
      :title="fn.systemCode === 'sys' ? '系统管理后台的这个模块与权限模型无关，只做占位页（需求规格第 8 章）。' : '业务系统的真实业务不在本项目范围内：本页只展示你对该功能点能做什么、数据范围是什么，按钮点击为占位。'" />
    <el-card>
      <div class="action-bar">
        <div v-for="row in rows" :key="row.code" class="action-slot">
          <el-tooltip :content="row.scope ? `范围：${scopeLabel(row.scope)}` : '没有此操作的权限'" placement="top">
            <span><el-button :type="row.code.endsWith(':delete') ? 'danger' : 'primary'" :plain="row.code.endsWith(':read') === false" :disabled="!row.scope" @click="run(row)">{{ row.label }}</el-button></span>
          </el-tooltip>
          <ScopeTag :scope="row.scope" />
        </div>
      </div>
      <el-table :data="rows" class="top-gap">
        <el-table-column prop="label" label="操作" width="90" />
        <el-table-column label="权限码" min-width="190"><template #default="{ row }"><code>{{ row.code }}</code></template></el-table-column>
        <el-table-column label="数据范围" width="110"><template #default="{ row }"><ScopeTag :scope="row.scope" /></template></el-table-column>
        <el-table-column label="来源角色" min-width="220"><template #default="{ row }">
          <span v-if="!row.scope" class="muted">—</span>
          <el-tag v-for="source in sources(row.code)" :key="source.roleId" class="tag-gap" size="small" effect="plain">{{ source.roleName }}：{{ scopeLabel(source.scope) }}</el-tag>
        </template></el-table-column>
      </el-table>
      <p class="muted small top-gap">按钮置灰而不是隐藏（需求规格 6.1）。多个角色授予同一权限码时取最大范围：全部 &gt; 本部门 &gt; 本人。</p>
    </el-card>
  </div>
</template>
