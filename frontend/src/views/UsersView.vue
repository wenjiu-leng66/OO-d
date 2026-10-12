<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/mock'
import { auth } from '../stores/auth'
import { useRoute } from 'vue-router'

const route = useRoute()

const loading = ref(false); const saving = ref(false); const failed = ref(false)
const query = ref(''); const department = ref(''); const accountType = ref(''); const page = ref(1); const pageSize = 3; const items = ref([])
const createVisible = ref(false); const detailVisible = ref(false); const roleDialogVisible = ref(false); const selectedUser = ref(null); const formRef = ref()
const roleDraft = ref([])
const form = reactive({ name: '', employeeNo: '', phone: '', branch: '总公司', departmentId: '', departmentName: '', position: '', accountType: 'employee', roleIds: [], initialPassword: '', status: 'active' })
const uniqueEmployeeNo = (_rule, value, callback) => usersHave('employeeNo', value) ? callback(new Error('员工号已存在')) : callback()
const uniquePhone = (_rule, value, callback) => usersHave('phone', value) ? callback(new Error('手机号已存在')) : callback()
const usersHave = (field, value) => Boolean(value && items.value.some(item => item[field] === value))
const rules = { initialPassword: [{ required: true, message: "请输入初始密码", trigger: "blur" }, { min: 6, max: 64, message: "密码须为 6–64 位", trigger: "blur" }], name: [{ required: true, message: '请输入姓名', trigger: 'blur' }], employeeNo: [{ required: true, message: '请输入员工号', trigger: 'blur' }, { validator: uniqueEmployeeNo, trigger: 'blur' }], phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { pattern: /^1\d{10}$/, message: '请输入 11 位手机号', trigger: 'blur' }, { validator: uniquePhone, trigger: 'blur' }], departmentName: [{ required: true, message: '请选择部门', trigger: 'change' }], position: [{ required: true, message: '请输入岗位', trigger: 'blur' }] }
const departments = computed(() => [...new Set(items.value.map(i => i.departmentName))])
const departmentOptions = computed(() => auth.user?.roleIds.includes('role-admin') ? api.departmentOptions : api.departmentOptions.filter(item => item.id === auth.user?.departmentId))
const branchOptions = computed(() => [...new Set(departmentOptions.value.map(item => item.branch))])
const scopedDepartmentOptions = computed(() => departmentOptions.value.filter(item => item.branch === form.branch))
const positionOptions = computed(() => ({
  办公室: ['行政专员', '文秘', '信息技术专员'],
  人力资源部: ['人事专员', '招聘专员', '部门经理'],
  财务部: ['会计', '出纳', '部门经理'],
  综合办公室: ['办公室职员', '部门经理'],
  工程技术部: ['工程师', '技术员', '运维工程师', '部门经理'],
  运营管理部: ['运维工程师', '运营专员', '部门经理']
}[form.departmentName] || []))
const accountTypes = [{ label: '员工账号', value: 'employee' }, { label: '临时运维账号', value: 'temporary-ops' }]
const filtered = computed(() => items.value.filter(i => { const text = `${i.name}${i.employeeNo}${i.phone}${i.position}${i.branch}`; return (!query.value || text.includes(query.value)) && (!department.value || i.departmentName === department.value) && (!accountType.value || i.accountType === accountType.value) }))
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const totalPermissions = computed(() => selectedUser.value?.permissionCodes?.length || 0)
const canManageUserRoles = computed(() => auth.permissionsLoaded && auth.permissions.includes('rbac:user:assign-role'))
const canEditSelectedRoles = computed(() => Boolean(
  selectedUser.value && canManageUserRoles.value &&
  selectedUser.value.id !== auth.user?.id &&
  !selectedUser.value.roleIds?.includes('role-admin')
))
const canUpdateSelectedStatus = computed(() => Boolean(
  selectedUser.value && auth.permissionsLoaded && auth.permissions.includes('rbac:user:update') &&
  selectedUser.value.id !== auth.user?.id && !selectedUser.value.roleIds?.includes('role-admin')
))
const typeLabel = value => accountTypes.find(item => item.value === value)?.label || value
const statusLabel = value => value === 'active' ? '启用' : '停用'
const statusType = value => value === 'active' ? 'success' : 'info'

async function load() { loading.value = true; failed.value = false; try { items.value = (await api.listUsers()).items } catch { failed.value = true } finally { loading.value = false } }
function reset() { query.value = ''; department.value = ''; accountType.value = ''; page.value = 1 }
function openCreate() { Object.assign(form, { name: '', employeeNo: '', phone: '', branch: '总公司', departmentId: '', departmentName: '', position: '', accountType: 'employee', roleIds: [], initialPassword: '', status: 'active' }); form.branch = branchOptions.value[0] || ''; createVisible.value = true }
function selectBranch(name) { form.branch = name; form.departmentId = ''; form.departmentName = ''; form.position = '' }
function selectDepartment(name) { const item = scopedDepartmentOptions.value.find(option => option.name === name); form.departmentId = item?.id || ''; form.position = '' }
function showDetail(row) { selectedUser.value = row; roleDraft.value = [...row.roleIds]; detailVisible.value = true }
function openRoleEditor() { roleDraft.value = [...selectedUser.value.roleIds]; roleDialogVisible.value = true }
function cancelRoleEdit() { roleDraft.value = [...selectedUser.value.roleIds]; roleDialogVisible.value = false }
async function updateRoles() {
  if (!selectedUser.value || !canEditSelectedRoles.value) return
  saving.value = true
  try {
    const updated = await api.updateUserRoles(selectedUser.value.id, roleDraft.value)
    Object.assign(selectedUser.value, updated)
    ElMessage.success('角色分配已更新')
    await load()
    const refreshed = items.value.find(item => item.id === selectedUser.value.id)
    if (refreshed) selectedUser.value = refreshed
    roleDraft.value = [...selectedUser.value.roleIds]
    roleDialogVisible.value = false
  } catch (error) { ElMessage.error(error.message || '角色更新失败') } finally { saving.value = false }
}
async function toggleStatus() { if (!canUpdateSelectedStatus.value) return; const next = selectedUser.value.status === 'active' ? 'inactive' : 'active'; saving.value = true; try { await api.updateUserStatus(selectedUser.value.id, next); selectedUser.value.status = next; ElMessage.success(next === 'active' ? '账号已启用' : '账号已停用') } catch (error) { ElMessage.error(error.message || '状态更新失败') } finally { saving.value = false } }
async function submitCreate() { await formRef.value.validate(async valid => { if (!valid) return; saving.value = true; try { await api.createUser({ ...form }); createVisible.value = false; ElMessage.success('账号已创建，可继续调整角色权限'); await load() } catch (error) { ElMessage.error(error.message || '账号创建失败') } finally { saving.value = false } }) }
onMounted(async () => {
  await load()
  if (['employee', 'temporary-ops'].includes(route.query.create)) {
    openCreate()
    form.accountType = route.query.create
  }
})
</script>

<template>
  <div>
    <div class="page-heading"><div><h2>用户管理</h2><p>统一维护员工账号、组织归属和角色权限摘要。</p></div><div class="heading-actions"><el-button @click="load">刷新</el-button><el-button v-if="auth.permissions.includes('rbac:user:create')" type="primary" @click="openCreate">＋ 添加用户</el-button></div></div>
    <el-card class="filter-card"><div class="toolbar"><el-input v-model="query" placeholder="搜索姓名、工号、手机号或岗位" clearable @input="page = 1" /><el-select v-model="department" placeholder="全部部门" clearable @change="page = 1"><el-option v-for="d in departments" :key="d" :label="d" :value="d" /></el-select><el-select v-model="accountType" placeholder="全部账号类别" clearable @change="page = 1"><el-option v-for="item in accountTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-button @click="reset">重置</el-button></div></el-card>
    <el-card class="table-card"><div class="table-caption"><div><b>员工账号</b><span>共 {{ filtered.length }} 条匹配记录</span></div><el-tag type="info" effect="plain">支持临时运维账号</el-tag></div><el-skeleton v-if="loading" :rows="6" animated /><el-result v-else-if="failed" icon="error" title="用户加载失败" sub-title="请检查网络后重试"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result><el-empty v-else-if="!paged.length" description="暂无匹配用户" /><template v-else><el-table :data="paged" stripe><el-table-column prop="employeeNo" label="员工号" min-width="130" /><el-table-column prop="name" label="姓名" min-width="90" /><el-table-column prop="branch" label="分公司" min-width="100" /><el-table-column prop="departmentName" label="部门" min-width="110" /><el-table-column prop="position" label="岗位" min-width="120" /><el-table-column label="账号类别" min-width="130"><template #default="{ row }"><el-tag :type="row.accountType === 'temporary-ops' ? 'warning' : 'primary'" effect="plain">{{ typeLabel(row.accountType) }}</el-tag></template></el-table-column><el-table-column label="状态" width="82"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column><el-table-column label="操作" fixed="right" width="90"><template #default="{ row }"><el-button link type="primary" @click="showDetail(row)">查看详情</el-button></template></el-table-column></el-table><div class="pagination"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="total, prev, pager, next" /></div></template></el-card>

    <el-dialog v-model="createVisible" title="添加用户账号" width="620px" destroy-on-close><p class="dialog-intro">员工号和手机号必须唯一。临时运维账号的权限由角色配置决定，前端不写死具体业务权限。</p><el-form ref="formRef" :model="form" :rules="rules" label-width="92px"><el-row :gutter="18"><el-col :span="12"><el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item></el-col><el-col :span="12"><el-form-item label="员工号" prop="employeeNo"><el-input v-model="form.employeeNo" /></el-form-item></el-col><el-col :span="12"><el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" /></el-form-item></el-col><el-col :span="24"><el-form-item label="初始密码" prop="initialPassword"><el-input v-model="form.initialPassword" type="password" show-password autocomplete="new-password" placeholder="设置 6–64 位初始密码" /></el-form-item></el-col><el-col :span="12"><el-form-item label="分公司" prop="branch"><el-select v-model="form.branch" class="full-width" placeholder="选择分公司" @change="selectBranch"><el-option v-for="name in branchOptions" :key="name" :label="name" :value="name" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="部门" prop="departmentName"><el-select v-model="form.departmentName" placeholder="先选择分公司" class="full-width" :disabled="!form.branch" @change="selectDepartment"><el-option v-for="item in scopedDepartmentOptions" :key="item.id" :label="item.name" :value="item.name" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="岗位" prop="position"><el-select v-model="form.position" placeholder="先选择部门" class="full-width" :disabled="!form.departmentName"><el-option v-for="name in positionOptions" :key="name" :label="name" :value="name" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="账号类别"><el-select v-model="form.accountType" class="full-width"><el-option v-for="item in accountTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="分配角色"><el-select v-model="form.roleIds" multiple filterable collapse-tags collapse-tags-tooltip :max-collapse-tags="2" class="full-width" placeholder="搜索并选择角色（可多选）"><el-option v-for="item in api.roleOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitCreate">创建账号</el-button></template></el-dialog>

    <el-drawer v-model="detailVisible" title="用户详情" size="480px"><template v-if="selectedUser"><div class="profile-head"><div class="profile-avatar">{{ selectedUser.name.slice(0, 1) }}</div><div><h3>{{ selectedUser.name }}</h3><p>{{ selectedUser.employeeNo }} · {{ selectedUser.position }}</p></div><el-tag :type="statusType(selectedUser.status)">{{ statusLabel(selectedUser.status) }}</el-tag></div><el-descriptions :column="1" border class="detail-descriptions"><el-descriptions-item label="分公司">{{ selectedUser.branch }}</el-descriptions-item><el-descriptions-item label="部门">{{ selectedUser.departmentName }}</el-descriptions-item><el-descriptions-item label="联系电话">{{ selectedUser.phone }}</el-descriptions-item><el-descriptions-item label="账号类别">{{ typeLabel(selectedUser.accountType) }}</el-descriptions-item></el-descriptions><div class="permission-summary"><div class="summary-title"><b>已分配角色</b><el-tag type="primary" effect="plain">{{ totalPermissions }} 项权限</el-tag></div><div class="role-chips"><span v-if="!selectedUser.roles.length">未分配角色</span><el-tag v-for="role in selectedUser.roles" :key="role" type="primary" disable-transitions>{{ role }}</el-tag></div><p class="permission-note">权限由角色配置产生。成员 2 负责展示，具体授权规则由角色权限模块和后端校验决定。</p><div class="permission-codes"><code v-for="code in selectedUser.permissionCodes" :key="code">{{ code }}</code></div></div><div class="detail-actions" v-if="canEditSelectedRoles || canUpdateSelectedStatus"><el-button v-if="canEditSelectedRoles" type="primary" plain @click="openRoleEditor">更改角色</el-button><el-button v-if="canUpdateSelectedStatus" :type="selectedUser.status === 'active' ? 'danger' : 'success'" plain :loading="saving" @click="toggleStatus">{{ selectedUser.status === 'active' ? '手动停用账号' : '重新启用账号' }}</el-button></div><el-dialog v-model="roleDialogVisible" title="更改角色" width="520px" destroy-on-close><p class="dialog-intro">选择该用户拥有的角色，保存后权限将按角色重新计算。</p><div class="role-preview"><span class="preview-label">当前角色</span><div class="role-chips"><el-tag v-for="roleId in roleDraft" :key="roleId" type="primary" closable @close="roleDraft = roleDraft.filter(id => id !== roleId)">{{ api.roleOptions.find(role => role.id === roleId)?.name || roleId }}</el-tag><span v-if="!roleDraft.length" class="empty-role">未分配角色</span></div></div><el-select v-model="roleDraft" multiple filterable collapse-tags collapse-tags-tooltip class="full-width" placeholder="添加角色"><el-option v-for="item in api.roleOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select><template #footer><el-button @click="cancelRoleEdit">取消</el-button><el-button type="primary" :loading="saving" @click="updateRoles">保存更改</el-button></template></el-dialog></template></el-drawer>
  </div>
</template>
