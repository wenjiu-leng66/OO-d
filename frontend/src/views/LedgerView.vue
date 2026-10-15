<script setup>
// 员工台账管理（PORTAL-04 ~ 06、UC-08）：唯一的真实业务，用来演示数据范围不能被绕过。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/mock'
import { auth } from '../stores/auth'
import { permissionScope } from '../policy-adapter'
import { scopeLabel } from '../api/fixtures/catalog'
import ScopeTag from '../components/ScopeTag.vue'

const loading = ref(false); const failed = ref(false); const saving = ref(false)
const keyword = ref(''); const page = ref(1); const size = 8
const result = ref({ items: [], total: 0, readScope: null, editScope: null })
const editing = ref(null); const formRef = ref()
const form = reactive({ phone: '', email: '' })
const rules = {
  phone: [{ pattern: /^(1\d{10})?$/, message: '手机号须为 11 位数字', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}
const placeholderActions = [
  { action: 'create', label: '新增' }, { action: 'approve', label: '审批' },
  { action: 'export', label: '导出' }, { action: 'delete', label: '删除' }
].map(item => ({ ...item, code: `hr:f046:${item.action}` }))
const actionScopes = computed(() => placeholderActions.map(item => ({ ...item, scope: permissionScope(auth, item.code) })))

async function load() {
  loading.value = true; failed.value = false
  try { result.value = await api.listEmployees({ keyword: keyword.value, page: page.value, pageSize: size }) }
  catch { failed.value = true } finally { loading.value = false }
}
function search() { page.value = 1; load() }
function openEdit(row) { editing.value = row; Object.assign(form, { phone: row.phone, email: row.email }) }
async function save() {
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true
  try { await api.updateEmployeeContact(editing.value.id, { phone: form.phone, email: form.email }); ElMessage.success('已保存电话和邮箱'); editing.value = null; await load() }
  catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}
function placeholder(item) { ElMessage.info(`占位：${item.label}员工。后端检查 ${item.code}，范围“${scopeLabel(item.scope)}”。`) }

// 越权演示：绕过界面直接提交修改请求，改用任意员工编号或附带不允许修改的字段。
const probe = reactive({ id: 'u0029', field: 'phone', response: null })
async function sendProbe() {
  const payload = probe.field === 'phone' ? { phone: '13800009999' } : { name: '被篡改的姓名' }
  try { probe.response = { ok: true, body: await api.updateEmployeeContact(probe.id.trim(), payload) }; await load() }
  catch (error) { probe.response = { ok: false, status: error.status, code: error.code, message: error.message } }
}
onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading">
      <div><h2>员工台账管理</h2><p>人力资源管理系统 / 员工信息管理 · 权限码 <code>hr:f046:*</code> · 只能修改电话和邮箱</p></div>
      <div class="heading-actions">
        <el-tooltip v-for="item in actionScopes" :key="item.code" :content="item.scope ? `范围：${scopeLabel(item.scope)}（占位）` : '没有此操作的权限'">
          <span><el-button :disabled="!item.scope" :type="item.action === 'delete' ? 'danger' : 'default'" plain @click="placeholder(item)">{{ item.label }}</el-button></span>
        </el-tooltip>
      </div>
    </div>
    <el-card class="filter-card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索工号、姓名或岗位" clearable @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <span class="scope-summary">查看范围 <ScopeTag :scope="result.readScope || permissionScope(auth, 'hr:f046:read')" /> 编辑范围 <ScopeTag :scope="result.editScope || permissionScope(auth, 'hr:f046:edit')" /></span>
      </div>
    </el-card>
    <el-card class="table-card">
      <div class="table-caption"><div><b>员工列表</b><span>按查看范围显示，共 {{ result.total }} 人</span></div></div>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-result v-else-if="failed" icon="error" title="台账加载失败"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
      <el-empty v-else-if="!result.items.length" description="暂无匹配员工" />
      <template v-else>
        <el-table :data="result.items" stripe>
          <el-table-column prop="username" label="工号" min-width="120" />
          <el-table-column prop="name" label="姓名" min-width="110" />
          <el-table-column label="部门" min-width="200"><template #default="{ row }"><span :title="row.departmentPath">{{ row.departmentName }}</span></template></el-table-column>
          <el-table-column prop="position" label="岗位" min-width="130" />
          <el-table-column prop="phone" label="电话" min-width="130" />
          <el-table-column label="邮箱" min-width="190"><template #default="{ row }">{{ row.email || '—' }}</template></el-table-column>
          <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }">
            <el-tooltip :disabled="row.editable" content="不在编辑范围内">
              <span><el-button link type="primary" :disabled="!row.editable" @click="openEdit(row)">编辑</el-button></span>
            </el-tooltip>
          </template></el-table-column>
        </el-table>
        <div class="pagination"><el-pagination v-model:current-page="page" :page-size="size" :total="result.total" layout="total, prev, pager, next" @current-change="load" /></div>
      </template>
    </el-card>

    <el-card class="top-gap">
      <div class="table-caption"><div><b>接口越权演示</b><span>模拟有人绕过界面，直接修改请求里的员工编号或字段（AUTHZ-03、T-09）</span></div></div>
      <div class="toolbar">
        <el-input v-model="probe.id" placeholder="目标员工编号，如 u0029" style="width: 220px" />
        <el-select v-model="probe.field" style="width: 220px"><el-option label="改电话（允许的字段）" value="phone" /><el-option label="改姓名（不允许的字段）" value="name" /></el-select>
        <el-button type="warning" plain @click="sendProbe">直接提交修改请求</el-button>
      </div>
      <el-alert v-if="probe.response" :type="probe.response.ok ? 'success' : 'error'" :closable="false" show-icon
        :title="probe.response.ok ? `200 已修改：${JSON.stringify(probe.response.body)}` : `${probe.response.status} ${probe.response.code}：${probe.response.message}`"
        description="拒绝时不返回目标员工的任何信息；编号不存在与不在范围内的返回相同。" />
    </el-card>

    <el-dialog :model-value="Boolean(editing)" title="编辑员工联系方式" width="480px" @close="editing = null">
      <template v-if="editing">
        <el-descriptions :column="1" border class="block-gap">
          <el-descriptions-item label="工号">{{ editing.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ editing.name }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ editing.departmentPath }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ editing.position }}</el-descriptions-item>
        </el-descriptions>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="60px">
          <el-form-item label="电话" prop="phone"><el-input v-model="form.phone" /></el-form-item>
          <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" placeholder="导入后为空，可在此填写" /></el-form-item>
        </el-form>
      </template>
      <template #footer><el-button @click="editing = null">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
