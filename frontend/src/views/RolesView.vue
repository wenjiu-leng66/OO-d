<script setup>
// 角色管理（ROLE-01 ~ 04）：列表、新增、编辑、启停用、删除、查看角色下的用户。继承、约束为后续阶段页签。
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/mock'
import { auth } from '../stores/auth'
import { hasPermission } from '../policy-adapter'
import HierarchyView from './HierarchyView.vue'
import ConstraintsView from './ConstraintsView.vue'

const route = useRoute(); const router = useRouter()
const tabOf = query => ['hierarchy', 'constraints'].includes(query) ? query : 'list'
const tab = ref(tabOf(route.query.tab))
watch(() => route.query.tab, value => { tab.value = tabOf(value) })
const loading = ref(false); const failed = ref(false); const saving = ref(false); const roles = ref([])
const keyword = ref('')
const can = code => hasPermission(auth, code)
const filtered = computed(() => roles.value.filter(role => !keyword.value || `${role.code}${role.name}`.includes(keyword.value.trim().toUpperCase()) || role.name.includes(keyword.value.trim())))
// 角色列表与成员抽屉各有请求序号；异步结果还须匹配当前身份并确保组件尚未卸载。
let alive = true; let listRequest = 0; let usersRequest = 0
const currentActor = () => auth.user?.id
async function load() {
  const request = ++listRequest; const actor = currentActor()
  loading.value = true; failed.value = false
  try {
    const list = await api.listRoles()
    if (alive && request === listRequest && actor === currentActor()) roles.value = list
  } catch {
    if (alive && request === listRequest && actor === currentActor()) failed.value = true
  } finally { if (alive && request === listRequest && actor === currentActor()) loading.value = false }
}

const formVisible = ref(false); const formRef = ref(); const editing = ref(null)
const form = reactive({ code: '', name: '', description: '' })
// 记录打开表单时的值，关闭/换页时才能区分“真正修改”与仅打开查看。
const originalForm = ref('')
const formDirty = computed(() => formVisible.value && JSON.stringify(form) !== originalForm.value)
const rules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }, { pattern: /^[A-Za-z][A-Za-z0-9_]{1,31}$/, message: '字母开头，2–32 位字母、数字或下划线', trigger: 'blur' }],
  name: [{ required: true, whitespace: true, message: '请输入角色名称', trigger: 'blur' }, { max: 32, message: '角色名称不能超过 32 位', trigger: 'blur' }],
  description: [{ max: 255, message: '角色说明不能超过 255 位', trigger: 'blur' }]
}
function openForm(role = null) {
  if (saving.value) return
  editing.value = role
  Object.assign(form, role ? { code: role.code, name: role.name, description: role.description || '' } : { code: '', name: '', description: '' })
  originalForm.value = JSON.stringify(form)
  formVisible.value = true
}
async function confirmDiscard() {
  if (saving.value) { ElMessage.warning('角色操作正在提交，请等待结果后再操作'); return false }
  if (!formDirty.value) return true
  try {
    await ElMessageBox.confirm('角色信息有未保存的更改，继续将丢弃。', '放弃编辑', { type: 'warning', confirmButtonText: '放弃更改', cancelButtonText: '继续编辑' })
    return true
  } catch { return false }
}
async function closeForm(done) {
  if (await confirmDiscard()) { formVisible.value = false; if (typeof done === 'function') done() }
}
// 在 await 校验之后再次核对身份；角色编码只在新增时使用，编辑只改名称和说明。
async function submit() {
  if (saving.value) return
  const actor = currentActor(); const id = editing.value?.id
  saving.value = true
  try {
    if (!await formRef.value.validate().catch(() => false) || actor !== currentActor()) return
    const payload = { ...form, name: form.name.trim() }
    if (id) await api.updateRole(id, payload); else await api.createRole(payload)
    if (!alive || actor !== currentActor()) return
    ElMessage.success(id ? '角色已保存' : '角色已创建，可到“角色授权”配置权限'); formVisible.value = false; await load()
  } catch (error) { if (alive && actor === currentActor()) ElMessage.error(error.message) }
  finally { if (alive && actor === currentActor()) saving.value = false }
}
async function toggle(role) {
  if (saving.value) return
  const actor = currentActor(); const enabled = role.enabled
  saving.value = true
  try {
    if (enabled) { try { await ElMessageBox.confirm(`停用“${role.name}”后，它的权限对所有 ${role.userCount} 个持有者都不生效。`, '停用角色', { type: 'warning' }) } catch { return } }
    if (!alive || actor !== currentActor()) return
    await api.setRoleEnabled(role.id, !enabled)
    if (!alive || actor !== currentActor()) return
    ElMessage.success(enabled ? '角色已停用' : '角色已启用'); await load()
  } catch (error) { if (alive && actor === currentActor()) ElMessage.error(error.message) }
  finally { if (alive && actor === currentActor()) saving.value = false }
}
// 自建角色若仍有用户，接口先拒绝，再要求明确二次确认；取消不会触发 confirmed=true 写入。
async function remove(role) {
  if (saving.value) return
  const actor = currentActor()
  saving.value = true
  try {
    try { await ElMessageBox.confirm(`删除角色“${role.name}”？其授权和用户关系一并删除。`, '删除角色', { type: 'warning' }) } catch { return }
    if (!alive || actor !== currentActor()) return
    try { await api.deleteRole(role.id) }
    catch (error) {
      if (error.code !== 'ROLE_HAS_USERS') throw error
      try { await ElMessageBox.confirm(`${error.message}。删除后这些用户将失去该角色，确定继续？`, '二次确认', { type: 'error', confirmButtonText: '仍然删除' }) } catch { return }
      if (!alive || actor !== currentActor()) return
      await api.deleteRole(role.id, { confirmed: true })
    }
    if (!alive || actor !== currentActor()) return
    ElMessage.success('角色已删除'); await load()
  } catch (error) { if (alive && actor === currentActor()) ElMessage.error(error.message) }
  finally { if (alive && actor === currentActor()) saving.value = false }
}

const usersVisible = ref(false); const roleUsers = ref([]); const usersOf = ref(null)
const usersLoading = ref(false); const usersFailed = ref('')
async function showUsers(role) {
  const request = ++usersRequest; const actor = currentActor()
  usersOf.value = role; roleUsers.value = []; usersVisible.value = true; usersLoading.value = true; usersFailed.value = ''
  try {
    const users = await api.listRoleUsers(role.id)
    if (alive && request === usersRequest && actor === currentActor()) roleUsers.value = users
  } catch (error) {
    if (alive && request === usersRequest && actor === currentActor()) usersFailed.value = error.message
  } finally { if (alive && request === usersRequest && actor === currentActor()) usersLoading.value = false }
}
function beforeUnload(event) {
  if (formDirty.value || saving.value) { event.preventDefault(); event.returnValue = '' }
}
function changeTab(name) { router.replace({ query: name === 'list' ? {} : { tab: name } }) }
onBeforeRouteLeave(() => confirmDiscard())
onBeforeRouteUpdate(async () => {
  const allowed = await confirmDiscard()
  if (allowed) formVisible.value = false; else tab.value = tabOf(route.query.tab)
  return allowed
})
// 清空旧身份的列表/弹窗并让在途请求失效，避免身份切换后回显旧数据。
watch(() => auth.user?.id, () => {
  ++listRequest; ++usersRequest; roles.value = []; roleUsers.value = []; formVisible.value = false; usersVisible.value = false; saving.value = false
})
onMounted(() => { window.addEventListener('beforeunload', beforeUnload); load() })
onBeforeUnmount(() => { alive = false; ++listRequest; ++usersRequest; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <div>
    <div class="page-heading"><div><h2>角色管理</h2><p>6 个内置角色不能删除；超级管理员角色不能停用，也不能修改授权。</p></div>
      <div class="heading-actions"><el-button :disabled="saving || loading" @click="load">刷新</el-button>
        <el-tooltip :disabled="can('rbac:role:create')" content="没有新增角色的权限"><span><el-button type="primary" :disabled="!can('rbac:role:create') || saving || loading" @click="openForm()">＋ 新增角色</el-button></span></el-tooltip>
      </div>
    </div>
    <el-tabs v-model="tab" @tab-change="changeTab">
      <el-tab-pane label="角色列表" name="list">
        <el-card class="table-card">
          <div class="toolbar"><el-input v-model="keyword" placeholder="角色编码或名称" clearable /></div>
          <el-skeleton v-if="loading" :rows="6" animated />
          <el-result v-else-if="failed" icon="error" title="角色加载失败"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
          <el-empty v-else-if="!filtered.length" description="暂无匹配角色" />
          <el-table v-else :data="filtered" stripe>
            <el-table-column prop="code" label="编码" min-width="150"><template #default="{ row }"><code>{{ row.code }}</code></template></el-table-column>
            <el-table-column label="名称" min-width="150"><template #default="{ row }"><b>{{ row.name }}</b><div class="muted small">{{ row.description }}</div></template></el-table-column>
            <el-table-column label="类型" width="130"><template #default="{ row }"><el-tag size="small" :type="row.builtin ? 'warning' : 'info'" effect="plain">{{ row.builtin ? '内置' : '自建' }}</el-tag> <el-tag size="small" effect="plain">{{ row.kind === 'admin' ? '管理类' : '业务' }}</el-tag></template></el-table-column>
            <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="用户数" width="80"><template #default="{ row }"><el-button link type="primary" @click="showUsers(row)">{{ row.userCount }}</el-button></template></el-table-column>
            <el-table-column prop="grantCount" label="授权条数" width="90" />
            <el-table-column label="操作" width="270" fixed="right"><template #default="{ row }">
              <el-button link type="primary" :disabled="saving || !can('rbac:permission:read')" @click="router.push({ path: '/admin/grants', query: { role: row.id } })">授权</el-button>
              <el-button link :disabled="!can('rbac:role:update') || saving" @click="openForm(row)">编辑</el-button>
              <el-button link :disabled="!can('rbac:role:update') || row.id === 'R01' || saving" @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
              <el-tooltip :disabled="!row.builtin" content="内置角色不能删除"><span><el-button link type="danger" :disabled="!can('rbac:role:delete') || row.builtin || saving" @click="remove(row)">删除</el-button></span></el-tooltip>
            </template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane name="hierarchy"><template #label>继承关系 <span class="stage-chip">阶段二</span></template><HierarchyView embedded /></el-tab-pane>
      <el-tab-pane name="constraints"><template #label>约束 <span class="stage-chip">阶段三</span></template><ConstraintsView embedded /></el-tab-pane>
    </el-tabs>

    <el-dialog v-model="formVisible" :title="editing ? '编辑角色' : '新增角色'" width="520px" :before-close="closeForm" :close-on-click-modal="!saving" :close-on-press-escape="!saving" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" :disabled="saving">
        <el-form-item label="角色编码" prop="code"><el-input v-model="form.code" :disabled="Boolean(editing)" maxlength="32" placeholder="如 LEDGER_KEEPER，保存后不可修改" /></el-form-item>
        <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" maxlength="32" show-word-limit /></el-form-item>
        <el-form-item label="说明" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="closeForm">取消</el-button><el-button type="primary" :disabled="saving" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="usersVisible" :title="`“${usersOf?.name}”下的用户${usersLoading || usersFailed ? '' : `（${roleUsers.length}）`}`" size="520px">
      <el-skeleton v-if="usersLoading" :rows="5" animated />
      <el-result v-else-if="usersFailed" icon="error" title="角色用户加载失败" :sub-title="usersFailed"><template #extra><el-button type="primary" @click="showUsers(usersOf)">重新加载</el-button></template></el-result>
      <el-table v-else :data="roleUsers" size="small">
        <el-table-column prop="username" label="工号" min-width="110" />
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="departmentName" label="部门" min-width="180" />
        <el-table-column label="状态" width="70"><template #default="{ row }">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</template></el-table-column>
        <template #empty><span class="muted">该角色下没有用户</span></template>
      </el-table>
    </el-drawer>
  </div>
</template>
