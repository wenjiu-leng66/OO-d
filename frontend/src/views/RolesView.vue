<script setup>
// 角色管理（ROLE-01 ~ 04）：列表、新增、编辑、启停用、删除、查看角色下的用户。继承、约束为后续阶段页签。
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
async function load() {
  loading.value = true; failed.value = false
  try { roles.value = await api.listRoles() } catch { failed.value = true } finally { loading.value = false }
}

const formVisible = ref(false); const formRef = ref(); const editing = ref(null)
const form = reactive({ code: '', name: '', description: '' })
const rules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }, { pattern: /^[A-Za-z][A-Za-z0-9_]{1,31}$/, message: '字母开头，2–32 位字母、数字或下划线', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}
function openForm(role = null) {
  editing.value = role
  Object.assign(form, role ? { code: role.code, name: role.name, description: role.description } : { code: '', name: '', description: '' })
  formVisible.value = true
}
async function submit() {
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true
  try {
    if (editing.value) await api.updateRole(editing.value.id, form); else await api.createRole(form)
    ElMessage.success(editing.value ? '角色已保存' : '角色已创建，可到“角色授权”配置权限'); formVisible.value = false; await load()
  } catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}
async function toggle(role) {
  if (role.enabled) { try { await ElMessageBox.confirm(`停用“${role.name}”后，它的权限对所有 ${role.userCount} 个持有者都不生效。`, '停用角色', { type: 'warning' }) } catch { return } }
  try { await api.setRoleEnabled(role.id, !role.enabled); ElMessage.success(role.enabled ? '角色已停用' : '角色已启用'); await load() }
  catch (error) { ElMessage.error(error.message) }
}
async function remove(role) {
  try { await ElMessageBox.confirm(`删除角色“${role.name}”？其授权和用户关系一并删除。`, '删除角色', { type: 'warning' }) } catch { return }
  try { await api.deleteRole(role.id) }
  catch (error) {
    if (error.code !== 'ROLE_HAS_USERS') { ElMessage.error(error.message); return }
    try { await ElMessageBox.confirm(`${error.message}。删除后这些用户将失去该角色，确定继续？`, '二次确认', { type: 'error', confirmButtonText: '仍然删除' }) } catch { return }
    try { await api.deleteRole(role.id, { confirmed: true }) } catch (again) { ElMessage.error(again.message); return }
  }
  ElMessage.success('角色已删除'); await load()
}

const usersVisible = ref(false); const roleUsers = ref([]); const usersOf = ref(null)
async function showUsers(role) { usersOf.value = role; roleUsers.value = await api.listRoleUsers(role.id); usersVisible.value = true }
function changeTab(name) { router.replace({ query: name === 'list' ? {} : { tab: name } }) }
onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading"><div><h2>角色管理</h2><p>6 个内置角色不能删除；超级管理员角色不能停用，也不能修改授权。</p></div>
      <div class="heading-actions"><el-button @click="load">刷新</el-button>
        <el-tooltip :disabled="can('rbac:role:create')" content="没有新增角色的权限"><span><el-button type="primary" :disabled="!can('rbac:role:create')" @click="openForm()">＋ 新增角色</el-button></span></el-tooltip>
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
              <el-button link type="primary" @click="router.push({ path: '/admin/grants', query: { role: row.id } })">授权</el-button>
              <el-button link :disabled="!can('rbac:role:update')" @click="openForm(row)">编辑</el-button>
              <el-button link :disabled="!can('rbac:role:update') || row.id === 'R01'" @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
              <el-tooltip :disabled="!row.builtin" content="内置角色不能删除"><span><el-button link type="danger" :disabled="!can('rbac:role:delete') || row.builtin" @click="remove(row)">删除</el-button></span></el-tooltip>
            </template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane name="hierarchy"><template #label>继承关系 <span class="stage-chip">阶段二</span></template><HierarchyView embedded /></el-tab-pane>
      <el-tab-pane name="constraints"><template #label>约束 <span class="stage-chip">阶段三</span></template><ConstraintsView embedded /></el-tab-pane>
    </el-tabs>

    <el-dialog v-model="formVisible" :title="editing ? '编辑角色' : '新增角色'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="角色编码" prop="code"><el-input v-model="form.code" :disabled="Boolean(editing)" placeholder="如 LEDGER_KEEPER，保存后不可修改" /></el-form-item>
        <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="usersVisible" :title="`“${usersOf?.name}”下的用户（${roleUsers.length}）`" size="520px">
      <el-table :data="roleUsers" size="small">
        <el-table-column prop="username" label="工号" min-width="110" />
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="departmentName" label="部门" min-width="180" />
        <el-table-column label="状态" width="70"><template #default="{ row }">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</template></el-table-column>
        <template #empty><span class="muted">该角色下没有用户</span></template>
      </el-table>
    </el-drawer>
  </div>
</template>
