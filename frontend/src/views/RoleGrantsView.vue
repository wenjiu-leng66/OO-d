<script setup>
// 角色授权（GRANT-02、UC-04）：在资源目录上勾选每个功能点的操作，每条选择数据范围；
// 勾选查看以外的操作时自动勾选查看；翻页不丢失勾选；整组替换并带版本号。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/mock'
import { auth } from '../stores/auth'
import { hasPermission } from '../policy-adapter'
import { functions, systems, modules, actions, scopeOptions, scopeLabel, adminMapping } from '../api/fixtures/catalog'

const route = useRoute()
const roles = ref([]); const roleId = ref(''); const loading = ref(true); const saving = ref(false)
const failed = ref(''); const rolesFailed = ref(false); const loadedRoleId = ref(''); const switching = ref(false)
// original 是最后一次成功读取/保存的快照；draft 覆盖完整授权集合，不随筛选或翻页裁剪。
// version 必须与该快照一起使用；不能只发送当前页，避免误撤销其他页授权。
const version = ref(0); const original = ref(new Map()); const draft = ref(new Map()); const roleReadOnly = ref(false)
const systemCode = ref(''); const moduleName = ref(''); const keyword = ref(''); const onlyChecked = ref(false)
const page = ref(1); const size = 12; const simulateFailure = ref(false)

const canGrant = computed(() => hasPermission(auth, 'rbac:role:grant'))
// 加载中、读取失败、身份无权或角色切换未完成时关闭编辑，不能在未知旧授权上继续保存。
const readOnly = computed(() => roleReadOnly.value || !canGrant.value || loading.value || Boolean(failed.value) || !roleId.value || loadedRoleId.value !== roleId.value)
const editingDisabled = computed(() => readOnly.value || saving.value)
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

// 请求号 + 发起身份 + 目标角色 + 组件存活四重条件抑制过期响应，避免快速切换覆盖新草稿。
let requestSequence = 0; let alive = true
const currentActor = () => auth.user?.id
const isCurrent = (request, actor, id = roleId.value) => alive && request === requestSequence && currentActor() === actor && roleId.value === id
function clearGrants() {
  loadedRoleId.value = ''; version.value = 0; roleReadOnly.value = false
  original.value = new Map(); draft.value = new Map()
}
function applyGrants(data) {
  loadedRoleId.value = data.roleId; version.value = data.version; roleReadOnly.value = data.readOnly
  original.value = new Map(data.grants.map(grant => [grant.code, grant.scope]))
  draft.value = new Map(original.value)
}
// 先清旧授权再异步读取，失败时保留显式失败态，不把空集合误当成“该角色没有授权”。
async function loadRole(id = roleId.value) {
  if (!id || saving.value) return
  const request = ++requestSequence; const actor = currentActor()
  loading.value = true; failed.value = ''; clearGrants()
  try {
    const data = await api.getRoleGrants(id)
    if (data.roleId !== id) throw new Error('角色授权返回对象不一致，请重新加载')
    if (isCurrent(request, actor, id)) applyGrants(data)
  } catch (error) {
    if (isCurrent(request, actor, id)) failed.value = error.message
  } finally { if (isCurrent(request, actor, id)) loading.value = false }
}
async function initialize() {
  const request = ++requestSequence; const actor = currentActor()
  loading.value = true; failed.value = ''; rolesFailed.value = false; clearGrants(); roles.value = []; roleId.value = ''
  try {
    const list = await api.listRoles()
    if (!isCurrent(request, actor, '')) return
    roles.value = list
    if (!list.length) { loading.value = false; return }
    roleId.value = typeof route.query.role === 'string' && route.query.role ? route.query.role : list.find(item => item.id === 'R05')?.id || list[0].id
    await loadRole()
  } catch (error) {
    if (isCurrent(request, actor, '')) { rolesFailed.value = true; failed.value = error.message; loading.value = false }
  }
}
// 路由离开、切换角色和刷新均保护未保存草稿；正在保存时等待明确结果，不能提前切走。
async function confirmDiscard(title = '离开角色授权') {
  if (saving.value) { ElMessage.warning('授权正在保存，请等待结果后再操作'); return false }
  if (!diff.value.total) return true
  try {
    await ElMessageBox.confirm('当前角色有未保存的更改，继续将丢弃。', title, { type: 'warning', confirmButtonText: '放弃更改', cancelButtonText: '继续编辑' })
    return true
  } catch { return false }
}
async function selectRole(id, { allowMissing = false } = {}) {
  if (id === roleId.value) return true
  if (switching.value || typeof id !== 'string' || (!allowMissing && !roles.value.some(item => item.id === id))) return false
  const actor = currentActor()
  switching.value = true
  try {
    if (!await confirmDiscard('切换角色') || !alive || actor !== currentActor()) return false
    roleId.value = id; page.value = 1; await loadRole(id)
    return true
  } finally { switching.value = false }
}
function beforeUnload(event) {
  if (diff.value.total || saving.value) { event.preventDefault(); event.returnValue = '' }
}
onBeforeRouteLeave(() => confirmDiscard())
onBeforeRouteUpdate(async to => {
  if (to.query.role && to.query.role !== roleId.value) {
    return selectRole(to.query.role, { allowMissing: true })
  }
  return true
})

function retry() {
  if (rolesFailed.value) initialize(); else loadRole()
}

// 写操作依赖同功能点 read：选中写自动补 read；取消 read 一并撤销该功能点全部动作。
// 每次复制 Map 再替换 ref，使响应式差异计算能可靠触发。
function toggle(fn, action, checked) {
  if (editingDisabled.value) return
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
function setScope(code, scope) { if (!editingDisabled.value) draft.value = new Map(draft.value).set(code, scope) }
function revert() { if (!editingDisabled.value) draft.value = new Map(original.value) }
function simulateOtherAdmin() {
  if (editingDisabled.value) return
  api.demo.bumpRoleGrantVersion(roleId.value)
  ElMessage.warning('已模拟：另一位管理员刚保存了这个角色的授权，服务端版本号 +1。现在点保存会收到 409。')
}
// 保存冻结目标/身份/请求号及旧快照，成功响应只应用于仍然相同的编辑上下文。
// 409 必须重新读取再复核；500 保留草稿并尝试回读服务器集合，不假装已经保存或自动重试覆盖。
async function save() {
  if (editingDisabled.value || !diff.value.total) return
  const id = roleId.value; const actor = currentActor(); const request = requestSequence
  const stillCurrent = () => isCurrent(request, actor, id)
  const grants = [...draft.value].map(([code, scope]) => ({ code, scope }))
  const savedOriginal = new Map(original.value)
  saving.value = true
  try {
    const result = await api.saveRoleGrants(id, grants, version.value, { simulateFailure: simulateFailure.value })
    if (!stillCurrent()) return
    applyGrants({ ...result, readOnly: roleReadOnly.value })
    roles.value = roles.value.map(item => item.id === id ? { ...item, permissionVersion: result.version, grantCount: result.grants.length } : item)
    ElMessage.success('授权已保存，持有该角色的用户下一次请求即按新授权鉴权')
  } catch (error) {
    if (!stillCurrent()) return
    if (error.code === 'VERSION_CONFLICT') {
      await ElMessageBox.alert('409 冲突：该角色的授权已被其他管理员修改，本次提交没有写入任何数据。点击确定重新加载并复核。', '保存冲突', { type: 'warning' }).catch(() => {})
      if (stillCurrent()) { saving.value = false; await loadRole(id) }
    } else if (error.status === 500) {
      let detail = '暂时无法读取保存结果，请重新加载并复核。你的勾选仍保留在页面上。'
      try {
        const server = await api.getRoleGrants(id)
        const same = server.grants.length === savedOriginal.size && server.grants.every(grant => savedOriginal.get(grant.code) === grant.scope)
        detail = `服务端授权与保存前${same ? '完全相同' : '不同'}（${server.grants.length} 条），你的勾选仍保留在页面上，${same ? '可以重试' : '请重新加载并复核'}。`
      } catch { /* 读取也失败时保留草稿，明确提示结果尚未核实。 */ }
      if (stillCurrent()) await ElMessageBox.alert(`${error.message}。${detail}`, '保存失败', { type: 'error' }).catch(() => {})
    } else {
      if ([401, 403, 404].includes(error.status)) failed.value = error.message
      ElMessage.error(error.message)
    }
  } finally { if (alive && currentActor() === actor && roleId.value === id) saving.value = false }
}
watch([systemCode, moduleName, keyword, onlyChecked], () => { page.value = 1 })
watch(systemCode, () => { moduleName.value = '' })

// 演示身份变化后立刻丢弃旧授权和在途响应；新身份不能继承前一身份打开的草稿。
watch(() => auth.user?.id, () => {
  ++requestSequence; clearGrants(); roles.value = []; roleId.value = ''; saving.value = false; failed.value = ''; loading.value = false
})
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  initialize()
})
onBeforeUnmount(() => { alive = false; ++requestSequence; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <div>
    <div class="page-heading">
      <div><h2>角色授权</h2><p>勾选功能点的操作并为每条选择数据范围（全部 / 本部门 / 本人）。保存为整组替换，要么全部成功，要么全部不变。</p></div>
    </div>
    <el-card class="filter-card">
      <div class="toolbar">
        <el-select :model-value="roleId" :disabled="saving || switching || !roles.length" filterable placeholder="选择角色" style="width: 240px" @change="selectRole">
          <el-option v-for="item in roles" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id"><span>{{ item.name }}</span><small class="muted"> {{ item.code }}{{ item.enabled ? '' : ' · 已停用' }}</small></el-option>
        </el-select>
        <span v-if="role && loadedRoleId === roleId" class="scope-summary"><el-tag effect="plain">版本 {{ version }}</el-tag><el-tag effect="plain" type="info">已授权 {{ draft.size }} 条</el-tag><el-tag v-if="!role.enabled" type="info">角色已停用</el-tag></span>
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
      <el-result v-else-if="failed" icon="error" title="角色授权加载失败" :sub-title="failed"><template #extra><el-button type="primary" @click="retry">重新加载</el-button></template></el-result>
      <el-empty v-else-if="!roles.length" description="暂无可配置的角色" />
      <template v-else>
        <el-table :data="paged" size="small" border>
          <el-table-column label="功能点" min-width="210" fixed="left"><template #default="{ row }">
            <b>{{ row.name }}</b> <code class="muted">{{ row.id }}</code>
            <div class="muted small">{{ row.systemName }} / {{ row.module }}</div>
            <el-tag v-if="adminMapping[row.id]" size="small" type="warning" effect="plain">映射为 rbac:*</el-tag>
          </template></el-table-column>
          <el-table-column v-for="action in actions" :key="action.code" :label="action.label" min-width="92" align="center"><template #default="{ row }">
            <div class="grant-cell">
              <el-checkbox :model-value="draft.has(codeOf(row, action.code))" :disabled="editingDisabled" @change="value => toggle(row, action.code, value)" />
              <el-select v-if="draft.has(codeOf(row, action.code))" :model-value="draft.get(codeOf(row, action.code))" size="small" :disabled="editingDisabled" class="scope-select" @change="value => setScope(codeOf(row, action.code), value)">
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
      <el-checkbox v-model="simulateFailure" :disabled="saving">模拟保存中途失败</el-checkbox>
      <el-button type="warning" link :disabled="saving" @click="simulateOtherAdmin">模拟另一管理员先保存</el-button>
      <el-button :disabled="!diff.total || saving" @click="revert">撤销更改</el-button>
      <el-button type="primary" :disabled="!diff.total || saving" :loading="saving" @click="save">保存授权</el-button>
    </div>
    <p class="muted small">范围示例：{{ scopeLabel('DEPT') }} = 用户所在部门及其全部下级部门。多个角色授予同一权限码时取最大范围。</p>
  </div>
</template>
