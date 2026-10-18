<script setup>
// 角色授权（GRANT-02、UC-04）：在资源目录上勾选每个功能点的操作，每条选择数据范围；
// 勾选查看以外的操作时自动勾选查看；翻页不丢失勾选；整组替换并带版本号。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/mock'
import { auth } from '../stores/auth'
import { hasPermission } from '../policy-adapter'
import { functions, systems, modules, actions, scopeOptions, scopeLabel, adminMapping } from '../api/fixtures/catalog'

const route = useRoute()
const roles = ref([]); const roleId = ref(''); const loading = ref(false); const saving = ref(false)
const version = ref(0); const original = ref(new Map()); const draft = ref(new Map()); const roleReadOnly = ref(false)
const systemCode = ref(''); const moduleName = ref(''); const keyword = ref(''); const onlyChecked = ref(false)
const page = ref(1); const size = 12; const simulateFailure = ref(false)

const canGrant = computed(() => hasPermission(auth, 'rbac:role:grant'))
const readOnly = computed(() => roleReadOnly.value || !canGrant.value)
const role = computed(() => roles.value.find(item => item.id === roleId.value))
const moduleOptions = computed(() => modules.filter(item => !systemCode.value || item.systemCode === systemCode.value))
const codeOf = (fn, action) => `${fn.systemCode}:${fn.id}:${action}`
const rows = computed(() => functions.filter(fn =>
  (!systemCode.value || fn.systemCode === systemCode.value) &&
  (!moduleName.value || fn.module === moduleName.value) &&
  (!keyword.value || `${fn.id}${fn.name}${fn.module}`.includes(keyword.value.trim())) &&
  (!onlyChecked.value || actions.some(action => draft.value.has(codeOf(fn, action.code))))))
const paged = computed(() => rows.value.slice((page.value - 1) * size, page.value * size))
const diff = computed(() => {
  const added = [...draft.value.keys()].filter(code => !original.value.has(code))
  const removed = [...original.value.keys()].filter(code => !draft.value.has(code))
  const changed = [...draft.value.keys()].filter(code => original.value.has(code) && original.value.get(code) !== draft.value.get(code))
  return { added, removed, changed, total: added.length + removed.length + changed.length }
})

async function loadRole() {
  if (!roleId.value) return
  loading.value = true
  try {
    const data = await api.getRoleGrants(roleId.value)
    version.value = data.version; roleReadOnly.value = data.readOnly
    original.value = new Map(data.grants.map(grant => [grant.code, grant.scope]))
    draft.value = new Map(original.value)
  } catch (error) { ElMessage.error(error.message) } finally { loading.value = false }
}
async function selectRole(id) {
  if (diff.value.total) {
    try { await ElMessageBox.confirm('当前角色有未保存的更改，切换后将丢弃。', '切换角色', { type: 'warning' }) } catch { roleId.value = role.value?.id; return }
  }
  roleId.value = id; page.value = 1; await loadRole()
}

function toggle(fn, action, checked) {
  const next = new Map(draft.value)
  const code = codeOf(fn, action)
  const readCode = codeOf(fn, 'read')
  if (checked) {
    next.set(code, next.get(code) || 'ALL')
    if (action !== 'read' && !next.has(readCode)) { next.set(readCode, 'ALL'); ElMessage.info(`已自动勾选“${fn.name}”的查看`) }
  } else if (action === 'read' && actions.some(item => item.code !== 'read' && next.has(codeOf(fn, item.code)))) {
    actions.forEach(item => next.delete(codeOf(fn, item.code)))
    ElMessage.info(`其他操作都需要“查看”，已一并取消“${fn.name}”的全部操作`)
  } else next.delete(code)
  draft.value = next
}
function setScope(code, scope) { draft.value = new Map(draft.value).set(code, scope) }
function revert() { draft.value = new Map(original.value) }
function simulateOtherAdmin() {
  api.demo.bumpRoleGrantVersion(roleId.value)
  ElMessage.warning('已模拟：另一位管理员刚保存了这个角色的授权，服务端版本号 +1。现在点保存会收到 409。')
}
async function save() {
  saving.value = true
  try {
    const grants = [...draft.value].map(([code, scope]) => ({ code, scope }))
    const result = await api.saveRoleGrants(roleId.value, grants, version.value, { simulateFailure: simulateFailure.value })
    version.value = result.version
    original.value = new Map(result.grants.map(grant => [grant.code, grant.scope])); draft.value = new Map(original.value)
    ElMessage.success('授权已保存，持有该角色的用户下一次请求即按新授权鉴权')
    roles.value = await api.listRoles()
  } catch (error) {
    if (error.code === 'VERSION_CONFLICT') {
      await ElMessageBox.alert('409 冲突：该角色的授权已被其他管理员修改，本次提交没有写入任何数据。点击确定重新加载。', '保存冲突', { type: 'warning' })
      await loadRole()
    } else if (error.status === 500) {
      const server = await api.getRoleGrants(roleId.value)
      const same = server.grants.length === original.value.size && server.grants.every(grant => original.value.get(grant.code) === grant.scope)
      ElMessageBox.alert(`${error.message}。服务端授权与保存前${same ? '完全相同' : '不同'}（${server.grants.length} 条），你的勾选仍保留在页面上，可以重试。`, '保存失败', { type: 'error' })
    } else ElMessage.error(error.message)
  } finally { saving.value = false }
}
watch([systemCode, moduleName, keyword, onlyChecked], () => { page.value = 1 })
watch(systemCode, () => { moduleName.value = '' })

onMounted(async () => {
  roles.value = await api.listRoles()
  roleId.value = roles.value.some(item => item.id === route.query.role) ? route.query.role : 'R05'
  await loadRole()
})
</script>

<template>
  <div>
    <div class="page-heading">
      <div><h2>角色授权</h2><p>勾选功能点的操作并为每条选择数据范围（全部 / 本部门 / 本人）。保存为整组替换，要么全部成功，要么全部不变。</p></div>
    </div>
    <el-card class="filter-card">
      <div class="toolbar">
        <el-select :model-value="roleId" filterable placeholder="选择角色" style="width: 240px" @change="selectRole">
          <el-option v-for="item in roles" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id"><span>{{ item.name }}</span><small class="muted"> {{ item.code }}{{ item.enabled ? '' : ' · 已停用' }}</small></el-option>
        </el-select>
        <span v-if="role" class="scope-summary"><el-tag effect="plain">版本 {{ version }}</el-tag><el-tag effect="plain" type="info">已授权 {{ draft.size }} 条</el-tag><el-tag v-if="!role.enabled" type="info">角色已停用</el-tag></span>
      </div>
      <el-alert v-if="roleReadOnly" class="block-gap" type="warning" :closable="false" show-icon title="超级管理员角色的授权不能修改（ROLE-03），本页只读。" />
      <el-alert v-else-if="!canGrant" class="block-gap" type="info" :closable="false" show-icon title="你没有修改角色授权的权限（rbac:role:grant），本页只读；直接调用保存接口返回 403。" />
      <div class="toolbar">
        <el-select v-model="systemCode" placeholder="全部系统" clearable filterable><el-option v-for="item in systems" :key="item.code" :label="item.name" :value="item.code" /></el-select>
        <el-select v-model="moduleName" placeholder="全部模块" clearable filterable><el-option v-for="item in moduleOptions" :key="item.systemCode + item.name" :label="item.name" :value="item.name" /></el-select>
        <el-input v-model="keyword" placeholder="功能点名称或编号" clearable />
        <el-checkbox v-model="onlyChecked">只看已勾选</el-checkbox>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-skeleton v-if="loading" :rows="8" animated />
      <template v-else>
        <el-table :data="paged" size="small" border>
          <el-table-column label="功能点" min-width="210" fixed="left"><template #default="{ row }">
            <b>{{ row.name }}</b> <code class="muted">{{ row.id }}</code>
            <div class="muted small">{{ row.systemName }} / {{ row.module }}</div>
            <el-tag v-if="adminMapping[row.id]" size="small" type="warning" effect="plain">映射为 rbac:*</el-tag>
          </template></el-table-column>
          <el-table-column v-for="action in actions" :key="action.code" :label="action.label" min-width="92" align="center"><template #default="{ row }">
            <div class="grant-cell">
              <el-checkbox :model-value="draft.has(codeOf(row, action.code))" :disabled="readOnly" @change="value => toggle(row, action.code, value)" />
              <el-select v-if="draft.has(codeOf(row, action.code))" :model-value="draft.get(codeOf(row, action.code))" size="small" :disabled="readOnly" class="scope-select" @change="value => setScope(codeOf(row, action.code), value)">
                <el-option v-for="item in scopeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </div>
          </template></el-table-column>
          <template #empty><span class="muted">暂无匹配功能点</span></template>
        </el-table>
        <div class="pagination"><el-pagination v-model:current-page="page" :page-size="size" :total="rows.length" layout="total, prev, pager, next" /></div>
      </template>
    </el-card>

    <div v-if="!readOnly" class="save-bar">
      <span>未保存的更改：新增 <b>{{ diff.added.length }}</b> · 撤销 <b>{{ diff.removed.length }}</b> · 改范围 <b>{{ diff.changed.length }}</b></span>
      <el-checkbox v-model="simulateFailure">模拟保存中途失败</el-checkbox>
      <el-button type="warning" link @click="simulateOtherAdmin">模拟另一管理员先保存</el-button>
      <el-button :disabled="!diff.total" @click="revert">撤销更改</el-button>
      <el-button type="primary" :disabled="!diff.total" :loading="saving" @click="save">保存授权</el-button>
    </div>
    <p class="muted small">范围示例：{{ scopeLabel('DEPT') }} = 用户所在部门及其全部下级部门。多个角色授予同一权限码时取最大范围。</p>
  </div>
</template>
