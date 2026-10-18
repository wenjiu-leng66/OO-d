<script setup>
// 组织架构（DEPT-01 ~ 02，只读，由导入维护）：部门树显示类型、负责人、上级、员工数；可查看某部门及全部下级的人员。
import { computed, onMounted, ref } from 'vue'
import { api } from '../api/mock'
import { auth } from '../stores/auth'
import { permissionScope } from '../policy-adapter'
import { scopeLabel } from '../api/fixtures/catalog'

const loading = ref(false); const failed = ref(false); const data = ref([]); const selected = ref(null)
const members = ref([]); const membersLoading = ref(false)
function flatten(nodes, result = []) { nodes.forEach(node => { result.push(node); flatten(node.children || [], result) }); return result }
const allNodes = computed(() => flatten(data.value))
const typeCounts = computed(() => Object.entries(allNodes.value.reduce((acc, node) => ({ ...acc, [node.typeLabel]: (acc[node.typeLabel] || 0) + 1 }), {})))
const scope = computed(() => permissionScope(auth, 'rbac:department:read'))
const typeTag = type => ({ HQ: 'danger', HQ_DEPT: 'primary', BRANCH: 'warning', BRANCH_DEPT: 'success', PROJECT: 'info' })[type]
async function load() {
  loading.value = true; failed.value = false
  try { data.value = await api.listDepartments(); if (data.value[0]) await choose(data.value[0]) }
  catch { failed.value = true } finally { loading.value = false }
}
async function choose(node) {
  selected.value = node; membersLoading.value = true
  try { members.value = await api.listDepartmentMembers(node.id) } finally { membersLoading.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading"><div><h2>组织架构</h2><p>只读，由部门信息表导入维护。查看范围“{{ scopeLabel(scope) }}”{{ scope === 'DEPT' ? '：只显示本部门及下级' : '' }}。</p></div><el-button @click="load">刷新</el-button></div>
    <el-card class="department-card">
      <el-skeleton v-if="loading" :rows="7" animated />
      <el-result v-else-if="failed" icon="error" title="部门加载失败" sub-title="请检查网络后重试"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
      <el-empty v-else-if="!data.length" description="暂无部门数据" />
      <div v-else class="department-layout">
        <div class="department-tree">
          <div class="tree-title"><b>组织目录 · {{ allNodes.length }} 个单元</b></div>
          <div class="type-legend"><span v-for="[label, count] in typeCounts" :key="label">{{ label }} {{ count }}</span></div>
          <el-tree :data="data" node-key="id" default-expand-all highlight-current :expand-on-click-node="false" @node-click="choose">
            <template #default="{ data: item }"><div class="tree-node"><span class="tree-node-name" :title="item.name">{{ item.name }}</span><span class="tree-node-meta"><el-tag size="small" :type="typeTag(item.type)" effect="plain">{{ item.typeLabel }}</el-tag><small>{{ item.totalCount }}人</small></span></div></template>
          </el-tree>
        </div>
        <div v-if="selected" class="department-detail">
          <div class="department-detail-head"><div class="department-icon">{{ selected.typeLabel.slice(0, 1) }}</div><div><h3>{{ selected.name }}</h3><p>{{ selected.path }}</p></div><el-tag :type="typeTag(selected.type)" effect="plain">{{ selected.typeLabel }}</el-tag></div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="负责人">{{ selected.managerName ? `${selected.managerName}（${selected.managerUsername}）` : '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="上级">{{ selected.parentName || '无' }}</el-descriptions-item>
            <el-descriptions-item label="本部门员工">{{ selected.employeeCount }} 人</el-descriptions-item>
            <el-descriptions-item label="含下级合计">{{ selected.totalCount }} 人</el-descriptions-item>
          </el-descriptions>
          <div class="table-caption top-gap"><div><b>部门人员（含全部下级部门）</b><span>{{ members.length }} 人</span></div></div>
          <el-table v-loading="membersLoading" :data="members" max-height="360" size="small">
            <el-table-column prop="username" label="工号" min-width="110" />
            <el-table-column prop="name" label="姓名" min-width="100" />
            <el-table-column prop="departmentName" label="部门" min-width="190" />
            <el-table-column prop="position" label="岗位" min-width="110" />
            <el-table-column label="状态" width="70"><template #default="{ row }">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</template></el-table-column>
            <template #empty><span class="muted">该部门暂无人员</span></template>
          </el-table>
        </div>
      </div>
    </el-card>
  </div>
</template>
