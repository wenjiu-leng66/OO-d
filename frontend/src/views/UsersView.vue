<script setup>
// 用户管理（USER-01 ~ 07）与分配角色（GRANT-01、GRANT-03）。只列出管理范围内的用户，服务端分页。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/mock'
import { auth } from '../stores/auth'
import { hasPermission, permissionScope } from '../policy-adapter'
import { scopeLabel } from '../api/fixtures/catalog'

const route = useRoute()
const loading = ref(false); const saving = ref(false); const failed = ref(false)
const filters = reactive({ departmentId: '', keyword: '', roleId: '', status: '' })
const page = ref(1); const size = 8
const result = ref({ items: [], total: 0 })
const departments = ref([]); const roles = ref([])
const can = code => hasPermission(auth, code)
const userScope = computed(() => permissionScope(auth, 'rbac:user:read'))
const actorIsSuper = computed(() => auth.roles.some(role => role.id === 'R01' && role.enabled))

async function load() {
  loading.value = true; failed.value = false
  try { result.value = await api.listUsers({ ...filters, page: page.value, pageSize: size }) }
  catch { failed.value = true } finally { loading.value = false }
}
function search() { page.value = 1; load() }
function reset() { Object.assign(filters, { departmentId: '', keyword: '', roleId: '', status: '' }); search() }

// ---------- 新增 / 编辑 ----------
const formVisible = ref(false); const formRef = ref(); const editingId = ref(null)
const form = reactive({ username: '', name: '', departmentId: '', position: '', phone: '', email: '' })
const formDepartments = ref([])
const rules = {
  username: [{ required: true, message: '请输入工号', trigger: 'blur' }, { pattern: /^[A-Za-z0-9_]{2,32}$/, message: '2–32 位字母、数字或下划线', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }],
  phone: [{ pattern: /^(1\d{10})?$/, message: '手机号须为 11 位数字，可以为空', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}
async function openForm(user = null) {
  editingId.value = user?.id || null
  Object.assign(form, user ? { username: user.username, name: user.name, departmentId: user.departmentId, position: user.position, phone: user.phone, email: user.email }
    : { username: '', name: '', departmentId: '', position: '', phone: '', email: '' })
  formDepartments.value = await api.departmentOptions(user ? 'rbac:user:update' : 'rbac:user:create')
  formVisible.value = true
}
async function submitForm() {
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true
  try {
    if (editingId.value) { const updated = await api.updateUser(editingId.value, { ...form }); if (selected.value?.id === updated.id) selected.value = updated; ElMessage.success('用户信息已保存') }
    else { await api.createUser({ ...form }); ElMessage.success('用户已创建，首次登录须修改初始密码') }
    formVisible.value = false; await load()
  } catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}

// ---------- 详情与账号操作 ----------
const selected = ref(null); const detailVisible = ref(false)
const isProtected = user => Boolean(user) && (user.id === auth.user?.id || user.roleIds.includes('R01'))
const protectReason = user => user?.id === auth.user?.id ? '不能对自己的账号执行此操作' : '唯一的超级管理员账号受保护'
async function showDetail(row) { selected.value = await api.getUser(row.id); detailVisible.value = true }
async function act(label, call, confirmText) {
  if (confirmText) { try { await ElMessageBox.confirm(confirmText, label, { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }) } catch { return } }
  saving.value = true
  try { const updated = await call(); ElMessage.success(`${label}成功`); if (updated?.username) selected.value = { ...selected.value, ...updated }; else detailVisible.value = false; await load() }
  catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}
const toggleStatus = () => {
  const next = selected.value.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  act(next === 'DISABLED' ? '停用账号' : '启用账号', () => api.setUserStatus(selected.value.id, next), next === 'DISABLED' ? `停用 ${selected.value.username} 后不能登录，已登录的会话在下一次请求时失效。` : null)
}
const resetPassword = () => act('重置密码', () => api.resetPassword(selected.value.id), `把 ${selected.value.username} 的密码重置为初始密码？该用户下次登录必须先修改密码。`)
const removeUser = () => act('删除用户', () => api.deleteUser(selected.value.id), `删除 ${selected.value.username}（${selected.value.name}）？其角色关系一并删除；如果他是部门负责人，负责人置空。此操作不可撤销。`)

// ---------- 分配角色：整组替换 + 版本号 ----------
const roleDialog = ref(false); const roleDraft = ref([])
const added = computed(() => roleDraft.value.filter(id => !selected.value?.roleIds.includes(id)))
const removed = computed(() => (selected.value?.roleIds || []).filter(id => !roleDraft.value.includes(id)))
const roleName = id => roles.value.find(role => role.id === id)?.name || id
const roleLocked = role => role.id === 'R01' || (!actorIsSuper.value && role.kind === 'admin')
const roleLockReason = role => role.id === 'R01' ? '超级管理员角色不能授给其他账号' : '部门管理员只能分配或撤销业务角色'
function openRoles() { roleDraft.value = [...selected.value.roleIds]; roleDialog.value = true }
function simulateOtherAdmin() {
  api.demo.bumpUserRoleVersion(selected.value.id)
  ElMessage.warning('已模拟：另一位管理员刚保存了这个用户的角色，服务端版本号 +1。现在点保存会收到 409。')
}
async function saveRoles() {
  if (removed.value.length) {
    try { await ElMessageBox.confirm(`将撤销：${removed.value.map(roleName).join('、')}。确定保存？`, '撤销角色', { type: 'warning' }) } catch { return }
  }
  saving.value = true
  try {
    selected.value = { ...selected.value, ...await api.assignRoles(selected.value.id, roleDraft.value, selected.value.roleVersion) }
    ElMessage.success('角色已更新，该用户的下一次请求即按新角色鉴权'); roleDialog.value = false; await load()
  } catch (error) {
    if (error.status === 409 && error.code === 'VERSION_CONFLICT') {
      await ElMessageBox.alert('409 冲突：该用户的角色已被其他管理员修改，本次提交没有写入任何数据。点击确定重新加载最新角色。', '保存冲突', { type: 'warning' })
      selected.value = await api.getUser(selected.value.id); roleDraft.value = [...selected.value.roleIds]
    } else ElMessage.error(error.message)
  } finally { saving.value = false }
}

onMounted(async () => {
  const [deptOptions, roleList] = await Promise.all([api.departmentOptions('rbac:user:read'), can('rbac:role:read') ? api.listRoles() : []])
  departments.value = deptOptions; roles.value = roleList
  await load()
  if (route.query.create && can('rbac:user:create')) openForm()
})
</script>

<template>
  <div>
    <div class="page-heading">
      <div><h2>用户管理</h2><p>只列出管理范围内的用户：当前范围“{{ scopeLabel(userScope) }}”{{ userScope === 'DEPT' ? `（${auth.user?.departmentPath} 及下级）` : '' }}。</p></div>
      <div class="heading-actions"><el-button @click="load">刷新</el-button>
        <el-tooltip :disabled="can('rbac:user:create')" content="没有新增用户的权限"><span><el-button type="primary" :disabled="!can('rbac:user:create')" @click="openForm()">＋ 新增用户</el-button></span></el-tooltip>
      </div>
    </div>
    <el-card class="filter-card"><div class="toolbar">
      <el-select v-model="filters.departmentId" placeholder="全部部门（含下级）" clearable filterable @change="search"><el-option v-for="d in departments" :key="d.id" :label="d.path" :value="d.id" /></el-select>
      <el-input v-model="filters.keyword" placeholder="工号或姓名" clearable @keyup.enter="search" @clear="search" />
      <el-select v-model="filters.roleId" placeholder="全部角色" clearable @change="search"><el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" /></el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable @change="search"><el-option label="启用" value="ENABLED" /><el-option label="停用" value="DISABLED" /></el-select>
      <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
    </div></el-card>
    <el-card class="table-card">
      <div class="table-caption"><div><b>用户列表</b><span>服务端分页，共 {{ result.total }} 条</span></div></div>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-result v-else-if="failed" icon="error" title="用户加载失败" sub-title="请检查网络后重试"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
      <el-empty v-else-if="!result.items.length" description="暂无匹配用户" />
      <template v-else>
        <el-table :data="result.items" stripe>
          <el-table-column prop="username" label="工号" min-width="120" />
          <el-table-column prop="name" label="姓名" min-width="110" />
          <el-table-column label="部门" min-width="190"><template #default="{ row }"><span :title="row.departmentPath">{{ row.departmentName }}</span></template></el-table-column>
          <el-table-column prop="position" label="岗位" min-width="120" />
          <el-table-column label="角色" min-width="170"><template #default="{ row }"><el-tag v-for="name in row.roleNames" :key="name" class="tag-gap" size="small" effect="plain">{{ name }}</el-tag><span v-if="!row.roleNames.length" class="muted">未分配</span></template></el-table-column>
          <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag><el-tag v-if="row.mustChangePassword" class="tag-gap" size="small" type="warning" effect="plain">待改密</el-tag></template></el-table-column>
          <el-table-column label="操作" fixed="right" width="90"><template #default="{ row }"><el-button link type="primary" @click="showDetail(row)">详情</el-button></template></el-table-column>
        </el-table>
        <div class="pagination"><el-pagination v-model:current-page="page" :page-size="size" :total="result.total" layout="total, prev, pager, next" @current-change="load" /></div>
      </template>
    </el-card>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑用户' : '新增用户'" width="580px" destroy-on-close>
      <p class="dialog-intro">{{ editingId ? '可修改姓名、部门、岗位、电话、邮箱；部门只能选管理范围内的部门。' : '工号唯一；手机号可以为空，不为空时唯一。新用户使用初始密码，首次登录必须修改。角色在创建后通过“分配角色”设置。' }}</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="72px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="工号" prop="username"><el-input v-model="form.username" :disabled="Boolean(editingId)" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="部门" prop="departmentId"><el-select v-model="form.departmentId" filterable class="full-width" placeholder="选择部门"><el-option v-for="d in formDepartments" :key="d.id" :label="d.path" :value="d.id" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="岗位"><el-input v-model="form.position" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" placeholder="可以为空" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" placeholder="可以为空" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="formVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitForm">{{ editingId ? '保存' : '创建' }}</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="用户详情" size="500px">
      <template v-if="selected">
        <div class="profile-head"><div class="profile-avatar">{{ selected.name.slice(-1) }}</div><div><h3>{{ selected.name }}</h3><p>{{ selected.username }} · {{ selected.position || '—' }}</p></div><el-tag :type="selected.status === 'ENABLED' ? 'success' : 'info'">{{ selected.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></div>
        <el-descriptions :column="1" border class="detail-descriptions">
          <el-descriptions-item label="部门">{{ selected.departmentPath }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ selected.phone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ selected.email || '—' }}</el-descriptions-item>
          <el-descriptions-item label="密码">{{ selected.mustChangePassword ? '初始密码，待用户修改' : '已设置' }}</el-descriptions-item>
        </el-descriptions>
        <div class="permission-summary">
          <div class="summary-title"><b>已分配角色</b><span><el-tag type="info" effect="plain">版本 {{ selected.roleVersion }}</el-tag> <el-tag type="primary" effect="plain">{{ selected.permissionCount }} 条有效权限</el-tag></span></div>
          <div class="role-chips"><span v-if="!selected.roleNames.length" class="muted">未分配角色</span><el-tag v-for="name in selected.roleNames" :key="name">{{ name }}</el-tag></div>
        </div>
        <el-alert v-if="isProtected(selected)" class="top-gap" type="info" :closable="false" show-icon :title="protectReason(selected) + '：不能停用、删除或修改角色（USER-07）。'" />
        <div class="detail-actions">
          <el-button :disabled="!can('rbac:user:update')" @click="openForm(selected)">编辑</el-button>
          <el-button type="primary" plain :disabled="!can('rbac:user:assign-role') || isProtected(selected)" @click="openRoles">分配角色</el-button>
          <el-button :disabled="!can('rbac:user:update') || isProtected(selected)" :loading="saving" @click="toggleStatus">{{ selected.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
          <el-button :disabled="!can('rbac:user:update') || isProtected(selected)" @click="resetPassword">重置密码</el-button>
          <el-button type="danger" plain :disabled="!can('rbac:user:delete') || isProtected(selected)" @click="removeUser">删除</el-button>
        </div>
        <p v-if="!can('rbac:user:update')" class="muted small">当前账号对用户只有查看权限，按钮置灰；直接调用接口同样返回 403。</p>
      </template>
    </el-drawer>

    <el-dialog v-model="roleDialog" title="分配角色" width="600px" destroy-on-close>
      <p class="dialog-intro">提交完整的角色集合（整组替换）并带版本号 {{ selected?.roleVersion }}。版本不符时服务端返回 409，不做任何部分写入。</p>
      <el-checkbox-group v-model="roleDraft" class="role-check-grid">
        <el-tooltip v-for="role in roles" :key="role.id" :disabled="!roleLocked(role)" :content="roleLockReason(role)">
          <el-checkbox :value="role.id" :disabled="roleLocked(role)" border>
            {{ role.name }} <small class="muted">{{ role.kind === 'admin' ? '管理类' : '业务' }}{{ role.enabled ? '' : ' · 已停用' }}</small>
          </el-checkbox>
        </el-tooltip>
      </el-checkbox-group>
      <div class="role-diff">
        <span>将新增：<el-tag v-for="id in added" :key="id" type="success" size="small" class="tag-gap">{{ roleName(id) }}</el-tag><span v-if="!added.length" class="muted">无</span></span>
        <span>将撤销：<el-tag v-for="id in removed" :key="id" type="danger" size="small" class="tag-gap">{{ roleName(id) }}</el-tag><span v-if="!removed.length" class="muted">无</span></span>
      </div>
      <template #footer>
        <el-button class="float-left" type="warning" link @click="simulateOtherAdmin">模拟另一管理员先保存</el-button>
        <el-button @click="roleDialog = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!added.length && !removed.length" @click="saveRoles">保存角色</el-button>
      </template>
    </el-dialog>
  </div>
</template>
